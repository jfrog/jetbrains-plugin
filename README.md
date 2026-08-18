# JFrog Plugin for JetBrains IDEs

JFrog plugin for JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, and others): artifact management, security scanning, supply-chain best practices, and Agent Guard for [Junie](https://www.jetbrains.com/junie/) — plus JFrog tools exposed through the IDE's built-in MCP server.

> **Status:** the JFrog skills bundle and the JFrog (remote) MCP server are delivered to Junie automatically on IDE startup. The IDE-native MCP tools (`JfrogToolset`) are still stubs. Not yet published to JetBrains Marketplace — see [CONTRIBUTING.md](CONTRIBUTING.md) for what's confirmed vs. what still needs validating.

## What's new

- **Auto-delivery to Junie.** On IDE startup the plugin materializes the JFrog skills and a JFrog MCP server entry into `~/.junie/` — no manual copying. See [How delivery works](#how-delivery-works).

## Scope

This plugin targets **Junie**, JetBrains' native coding agent, specifically. IntelliJ's AI Assistant chat also lets you drive external agents (Claude Agent, Codex, Gemini CLI) via the Agent Client Protocol — those are out of scope here and manage their own configuration independently. If you want JFrog tooling in one of those, install [`claude-plugin`](https://github.com/jfrog/claude-plugin) / [`codex-plugin`](https://github.com/jfrog/codex-plugin) into that tool directly, the same way you would outside IntelliJ.

## Features

| Component | Feature | Description |
| --- | --- | --- |
| **Skill** | JFrog Platform | Interact with Artifactory repositories, builds, permissions, users, access tokens, projects, release bundles, and platform administration via the JFrog CLI and REST/GraphQL APIs. Also covers security audits, CVE lookups, and Advanced Security exposure queries. |
| **Skill** | Package safety & download | Check whether npm, Maven, PyPI, Go, and other packages are safe, curated, or allowed, then download them through Artifactory remote caches or curation-aware package managers. |
| **Skill** | Agent Guard | Manage MCPs through the JFrog Agent Guard — discover, install, configure, update, and remove MCP servers from the JFrog AI Catalog approved for your project. |
| **MCP (remote)** | JFrog MCP server | The remote JFrog MCP server (OAuth, no API keys), auto-added to Junie's `~/.junie/mcp/mcp.json`. See [How delivery works](#how-delivery-works). |
| **MCP tools (IDE-native, preview)** | Artifactory search, Xray scan, AI Catalog lookup | Contributed to the IDE's built-in MCP server via `com.intellij.mcpServer`. Bodies are stubs pending the JFrog API wiring — see [MCP tools](#mcp-tools-ide-native-preview). |

## Prerequisites

- A JetBrains IDE on **2025.2+** with Junie installed.
- **`JFROG_PLATFORM_URL`** environment variable set to your JFrog instance (e.g. `mycompany.jfrog.io`). The plugin uses it to fill in the JFrog MCP server URL.
- **Node.js** (≥ 18) with `npx` on your `PATH` (used by Agent Guard).
- **Skill runtime** (when using the skills) — `jf` CLI, `jq`, and `curl` on `PATH`, plus a configured JFrog instance. For the minimum versions, see the upstream skills [`Requirements`](https://github.com/jfrog/jfrog-skills/blob/v0.20.0/README.md#requirements).
- **JFrog Platform access** (optional) — the Agent Guard feature needs the AI Catalog entitlement on your subscription.

CLI authentication options: run `jf login` for browser-based setup, or set the `JFROG_ACCESS_TOKEN` environment variable. The JFrog MCP server authenticates via **OAuth** and requires no additional configuration.

## Installation

Not yet published to JetBrains Marketplace. For local development:

```bash
./gradlew runIde      # launches a sandbox IDE with the plugin installed
```

or build a distributable zip and install it via **Settings | Plugins | ⚙ | Install Plugin from Disk...**:

```bash
./gradlew buildPlugin # produces build/distributions/*.zip
```

## How delivery works

Because a compiled JetBrains plugin can't drop files into place the way the file-based Cursor/Claude/Codex plugins do, this plugin ships the same assets and materializes them into your **global Junie home** on IDE startup ([`JfrogJunieDeployer`](src/main/kotlin/com/jfrog/jetbrains/startup/JfrogJunieDeployer.kt)):

| Written to | What | Notes |
| --- | --- | --- |
| `~/.junie/skills/` | The vendored `jfrog-skills` bundle | Refreshed when the plugin version changes; other (non-JFrog) skills in the folder are left untouched. |
| `~/.junie/mcp/mcp.json` | A `jfrog` remote MCP server entry | **Merged**, not clobbered — Junie's own `idea` entry and any other servers survive. The URL is resolved from `JFROG_PLATFORM_URL`. |

Junie discovers both by convention (skills from `.junie/skills/`, MCP servers from `.junie/mcp/mcp.json`). The first JFrog MCP call in Junie triggers a one-time browser **OAuth** login.

If `JFROG_PLATFORM_URL` isn't set when the IDE starts (common for IDEs launched from the Dock/Finder, which don't inherit your shell env), the entry is written with a `<JFROG_PLATFORM_URL>` placeholder — set the variable and restart the IDE, or edit `~/.junie/mcp/mcp.json` directly. The **Tools | Configure JFrog MCP...** action remains as a manual fallback.

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

## MCP tools (IDE-native, preview)

Separately from the remote JFrog MCP server above, the plugin also contributes tools to the **IDE's own** built-in MCP server:

| Tool | Description |
| --- | --- |
| `jfrog_artifactory_search` | Search Artifactory for artifacts, builds, or packages. |
| `jfrog_xray_security_scan` | Run an Xray / Advanced Security scan and summarize findings. |
| `jfrog_ai_catalog_lookup` | Check package safety/curation via the JFrog AI Catalog. |

These are implemented as `@McpTool`-annotated suspend functions on a single `JfrogToolset` (see [`src/main/kotlin/com/jfrog/jetbrains/mcp/JfrogToolset.kt`](src/main/kotlin/com/jfrog/jetbrains/mcp/JfrogToolset.kt)), registered via `<extensions defaultExtensionNs="com.intellij.mcpServer"><mcpToolset implementation="..."/></extensions>` in [`plugin.xml`](src/main/resources/META-INF/plugin.xml). The `@McpTool` annotation is required: the IDE only exposes annotated methods. Bodies are stubs (`TODO`) pending the JFrog API wiring — see [CONTRIBUTING.md](CONTRIBUTING.md).

### Using the IDE-native tools in Junie

The IDE's MCP server treats Junie as an external client, so it must be pointed at the server once — these tools do **not** appear automatically:

1. **Settings → Tools → MCP Server** → check **Enable MCP Server**.
2. In **Clients Auto-Configuration**, find the **Junie** row and click **Auto-Configure** (it should flip from "Not configured"). Apply.
3. Start a Junie task — the tools now appear as `mcp_idea_jfrog_artifactory_search`, `mcp_idea_jfrog_xray_security_scan`, and `mcp_idea_jfrog_ai_catalog_lookup`.

By default these tools are **router-only** (reached through the universal `execute_tool` router, not listed individually), which keeps their descriptions out of the agent's context until needed. To list them by name instead, clear the **Router-only** checkbox for each under **Settings → Tools → MCP Server → Exposed Tools**.

> Validated on IntelliJ IDEA 2026.2 (build 262) with Junie installed.

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

## Publishing to JetBrains Marketplace

Publishing is signed + token-based via the IntelliJ Platform Gradle Plugin. Run it locally or from CI (the [`Publish to JetBrains Marketplace`](.github/workflows/publish-marketplace.yml) `workflow_dispatch` workflow):

```bash
export PUBLISH_TOKEN=...          # Marketplace token
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD=...   # if the key has one
./gradlew publishPlugin           # signs, then uploads to the "default" channel
```

Requires the four secrets (`PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`) set in **Settings → Secrets and variables → Actions**. Compatibility range, signing, and publishing behavior live in [`build.gradle.kts`](build.gradle.kts).

## Repository layout

```
jetbrains-plugin/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── src/main/resources/META-INF/plugin.xml       # plugin manifest
├── src/main/resources/META-INF/jfrog-junie.xml  # loaded only when Junie is present
├── src/main/kotlin/com/jfrog/jetbrains/
│   ├── startup/       # JfrogJunieDeployer (materializes skills + MCP into ~/.junie)
│   ├── mcp/           # JfrogToolset (McpToolset implementation)
│   └── actions/       # fallback "Configure JFrog MCP" action
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
./gradlew runIde                              # launches a sandbox IDE with the plugin installed
```

## Versioning

Bump `version` in [`gradle.properties`](gradle.properties) (and [`VERSION`](VERSION)) when you publish a new release, then tag (for example `v0.2.0`).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
