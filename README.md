# JFrog for Coding Agents

JFrog plugin for JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, and others). It delivers the JFrog Agent Skills bundle and a JFrog (remote) MCP server to [Junie](https://www.jetbrains.com/junie/) — artifact management, security scanning, supply-chain best practices, and Agent Guard for MCP governance.

> **Status:** the JFrog skills bundle and the JFrog (remote) MCP server are delivered to Junie automatically on IDE startup.

## Scope

This plugin targets **Junie**, JetBrains' native coding agent, specifically. IntelliJ's AI Assistant chat also lets you drive external agents (Claude Agent, Codex, Gemini CLI) via the Agent Client Protocol — those are out of scope here and manage their own configuration independently. If you want JFrog tooling in one of those, install [`claude-plugin`](https://github.com/jfrog/claude-plugin) / [`codex-plugin`](https://github.com/jfrog/codex-plugin) into that tool directly.

## Features

| Component | Feature | Description |
| --- | --- | --- |
| **Skill** | JFrog Platform | Interact with Artifactory repositories, builds, permissions, users, access tokens, projects, release bundles, and platform administration via the JFrog CLI and REST/GraphQL APIs. Also covers security audits, CVE lookups, and Advanced Security exposure queries. |
| **Skill** | Package safety & download | Check whether npm, Maven, PyPI, Go, and other packages are safe, curated, or allowed, then download them through Artifactory remote caches or curation-aware package managers. |
| **Skill** | Agent Guard | Manage MCPs through the JFrog Agent Guard — discover, install, configure, update, and remove MCP servers from the JFrog AI Catalog approved for your project. |
| **MCP (remote)** | JFrog MCP server | The remote JFrog MCP server (OAuth, no API keys), auto-added to Junie's `~/.junie/mcp/mcp.json`. See [How delivery works](#how-delivery-works). |

## Prerequisites

- A JetBrains IDE on **2025.2+** with Junie installed.
- **`JFROG_PLATFORM_URL`** environment variable set to your JFrog instance (e.g. `mycompany.jfrog.io`). The plugin uses it to fill in the JFrog MCP server URL.
- **Node.js** (≥ 18) with `npx` on your `PATH` (used by Agent Guard).
- **Skill runtime** (when using the skills) — `jf` CLI, `jq`, and `curl` on `PATH`, plus a configured JFrog instance. For the minimum versions, see the upstream skills [`Requirements`](https://github.com/jfrog/jfrog-skills/blob/v0.20.0/README.md#requirements).
- **JFrog Platform access** (optional) — the Agent Guard feature needs the AI Catalog entitlement on your subscription.

## Installation

Install **JFrog for Coding Agents** from the JetBrains Marketplace (**Settings | Plugins | Marketplace**, then search for it), or build the zip from source and install it via **Settings | Plugins | ⚙ | Install Plugin from Disk...**:

```bash
./gradlew buildPlugin # produces build/distributions/*.zip
```

## How delivery works

The plugin materializes its assets into your **global Junie home** on IDE startup ([`JfrogJunieDeployer`](src/main/kotlin/com/jfrog/jetbrains/startup/JfrogJunieDeployer.kt)):

| Written to | What | Notes |
| --- | --- | --- |
| `~/.junie/skills/` | The vendored `jfrog-skills` bundle | Refreshed when the plugin version changes; other (non-JFrog) skills in the folder are left untouched. |
| `~/.junie/mcp/mcp.json` | A `jfrog` remote MCP server entry | **Merged**, not clobbered — Junie's own `idea` entry and any other servers survive. The URL is resolved from `JFROG_PLATFORM_URL`, falling back to your JFrog CLI config. |

Junie discovers both by convention (skills from `.junie/skills/`, MCP servers from `.junie/mcp/mcp.json`). The first JFrog MCP call in Junie triggers a one-time browser **OAuth** login.

**Host resolution.** The `jfrog` URL host is resolved in this order:

1. The **`JFROG_PLATFORM_URL`** environment variable, if set.
2. Otherwise your **JFrog CLI config** (`~/.jfrog/jfrog-cli.conf.v*`): the server marked default, or — if only one server is configured — that single server.
3. If neither is available, the placeholder `https://<JFROG_PLATFORM_URL>/mcp` is written.

**If the placeholder was written** (the host couldn't be resolved automatically), you must set it yourself — the plugin does not guess. Either:

- set `JFROG_PLATFORM_URL` (or run `jf config add`) and **restart the IDE** so the plugin fills it in on next startup, **or**
- edit `~/.junie/mcp/mcp.json` directly, replace `<JFROG_PLATFORM_URL>` with your instance host (e.g. `mycompany.jfrog.io`), and restart the IDE.

## Skills

| Skill | Description |
| --- | --- |
| `jfrog` | JFrog Platform operations via CLI and APIs (Artifactory, Xray, access, projects, and more). |
| `jfrog-package-safety-and-download` | Check package safety and download via Artifactory. |
| `jfrog-ai-catalog-skills` | Discover, install, manage, and publish agent skills from the JFrog AI Catalog via `jf skills` and Agent Guard. |
| `jfrog-mcp-management` | Install, list, and remove MCP servers/tools via JFrog Agent Guard, and browse the JFrog MCP catalog. |
| `jfrog-reference-architecture` | JFrog Platform topology, sizing, deployment, and HA/DR guidance from the official Reference Architecture site. |
| `jfrog-setup-package-managers` | Set up and bind package managers (npm, pip, Maven, Gradle, Go, Docker, Helm, …) to Artifactory via `jf setup`. |

Skill content is vendored under `.junie/skills/` and bundled into the plugin — see [VENDOR.md](VENDOR.md).

## Usage

Once the IDE has started (and OAuth is completed on first use), interact with the JFrog plugin through Junie in natural language.

| Ask Junie… | What happens |
| --- | --- |
| "List my Artifactory repositories." | Returns repositories via the JFrog CLI / MCP. |
| "Run a security audit on this project." | Runs an Xray / Advanced Security audit and summarizes findings. |
| "Which MCP servers can I install?" | Returns MCP servers approved for your project (Agent Guard). |
| "Add the GitHub MCP server." | Installs an approved MCP server and syncs its tool policies locally. |
| "Remove the Slack MCP server." | Removes the server and its stored credentials. |
| "Is `lodash@4.17.21` safe to install?" | Checks JFrog Public Catalog signals and curation policy. |

## Troubleshooting

- **`node` / `npx` / `jf` "command not found", or an installed MCP shows red in Junie.** Junie doesn't resolve your login-shell `PATH`, so bare commands (and a bare `"command": "npx"` entry) fail. Either launch the IDE from a terminal (e.g. `idea .`) so it inherits `PATH`, or ensure Node.js and the `jf` CLI are on a system `PATH`. The `jfrog-mcp-management` skill mitigates this by resolving absolute tool paths and writing MCP entries with an absolute `npx` path.
- **Where are the MCP servers / tools?** In the IDE: **Settings → Tools → Junie → MCP Settings**. There is no interactive `/mcp` command in the IDE (typing `/mcp` in the Junie chat is treated as plain text).
- **Agent Guard keeps asking for a JFrog project key.** That's by design — it never guesses. Set the `JF_PROJECT` environment variable to skip the prompt.
- For platform-side MCP issues, see the [JFrog MCP Registry troubleshooting guide](https://docs.jfrog.com/ai-ml/docs/mcp-registry-troubleshooting).

## Updating the vendored skills

The `.junie/skills/` tree is vendored from [`jfrog/jfrog-skills`](https://github.com/jfrog/jfrog-skills) at the version pinned in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json). To pull a newer upstream release into this repo:

1. Bump `pin` in `.github/scripts/sync-skills-vendor.json` to the new tag (e.g. `v0.26.0`).
2. Run the sync script from the repo root:

   ```bash
   node .github/scripts/sync-skills.mjs
   ```

   It downloads the pinned tarball from `codeload.github.com`, extracts it, and replaces `.junie/skills/`.
3. Bump `version` in [`gradle.properties`](gradle.properties) (and [`VERSION`](VERSION)) so the published plugin — and the startup deployer's version marker — pick up the new bundle.
4. Update the pinned-version link in [Prerequisites](#prerequisites) so the skill runtime requirements point at the new tag.
5. Commit the pin bump, the regenerated `.junie/skills/` tree, and the version bump together, and open a PR.

See [VENDOR.md](VENDOR.md) for the full picture.

## Repository layout

```
jetbrains-plugin/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── src/main/resources/META-INF/plugin.xml       # plugin manifest
├── src/main/kotlin/com/jfrog/jetbrains/
│   └── startup/       # JfrogJunieDeployer (materializes skills + MCP into ~/.junie)
├── .junie/skills/     # vendored Agent Skills (see VENDOR.md)
├── .junie/mcp/mcp.json # JFrog MCP server template (${JFROG_PLATFORM_URL})
├── .github/scripts/   # sync-skills vendoring
├── LICENSE
├── README.md
└── VENDOR.md
```

## Check it locally

```bash
node scripts/validate-jetbrains-plugin.mjs   # fast: manifest + skills + MCP template
./gradlew verifyPlugin                        # plugin structure + compatibility checks
./gradlew buildPlugin                         # produces build/distributions/*.zip
```

Then install the built zip in a real IntelliJ + Junie and confirm the JFrog skills + JFrog MCP surface. (The `runIde` sandbox can't run Junie end-to-end.)

## Publishing to JetBrains Marketplace

Publishing is signed + token-based (configured in [`build.gradle.kts`](build.gradle.kts)). Run it from the [`Publish to JetBrains Marketplace`](.github/workflows/publish-marketplace.yml) workflow (`workflow_dispatch`), or locally with `./gradlew publishPlugin`.

It requires four repository secrets — add them under **Settings → Secrets and variables → Actions**:

| Secret | What | Where to get it |
| --- | --- | --- |
| `PUBLISH_TOKEN` | Marketplace upload token | [Marketplace](https://plugins.jetbrains.com/) profile → **My Tokens** |
| `CERTIFICATE_CHAIN` | Signing certificate chain (PEM) | [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) |
| `PRIVATE_KEY` | Signing private key (PEM) | [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) |
| `PRIVATE_KEY_PASSWORD` | Private-key password (only if the key has one) | — |

> **The first version must be uploaded manually** at [plugins.jetbrains.com](https://plugins.jetbrains.com/) → **Upload plugin** (JetBrains reviews the first submission). Token/CI publishing only works after the plugin listing exists.

## Versioning

[`VERSION`](VERSION) at the repo root is the source of truth, and `version` in
[`gradle.properties`](gradle.properties) has to match it — PRs fail the
[`Validate plugin`](.github/workflows/validate.yml) check if the two disagree.

The repo has no release tags yet, so merging this change to `main` cuts the first GitHub
Release, `v0.2.0`, from the version already in those files. From then on, **every merge to
`main` must bump both files**; [`.github/workflows/release.yml`](.github/workflows/release.yml)
fails when the version is not newer than the latest `vX.Y.Z` tag, and it creates the tag and
the GitHub Release (with the built plugin zip attached) when it is.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
