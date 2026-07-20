# JFrog Plugin for JetBrains IDEs

JFrog plugin for JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, and others): Agent Skills for [Junie](https://www.jetbrains.com/junie/) plus JFrog tools (Artifactory search, Xray security scanning, AI Catalog package safety checks) exposed through the IDE's built-in MCP server.

> **Status:** early scaffold, not yet published. `./gradlew buildPlugin` succeeds — see [CONTRIBUTING.md](CONTRIBUTING.md) for what's confirmed vs. what still needs validating in a real IDE before a real release.

## Scope

This plugin targets **Junie**, JetBrains' native coding agent, specifically. IntelliJ's AI Assistant chat also lets you drive external agents (Claude Agent, Codex, Gemini CLI) via the Agent Client Protocol - those are out of scope here and manage their own configuration independently. If you want JFrog tooling in one of those, install [`claude-plugin`](https://github.com/jfrog/claude-plugin) / [`codex-plugin`](https://github.com/jfrog/codex-plugin) into that tool directly, the same way you would outside IntelliJ.

## What's included

| Component | Feature | Description |
| --- | --- | --- |
| **Skills** | JFrog Platform, package safety & download, AI Catalog | Vendored into `.junie/skills/` - Junie's own Agent Skills discovery path. See [Skills](#skills) below. |
| **MCP tools** | Artifactory search, Xray security scan, AI Catalog lookup | Contributed to the IDE's built-in MCP server via the `com.intellij.mcpServer` extension point - see [MCP tools](#mcp-tools). |
| **Fallback action** | `Tools \| Configure JFrog MCP...` | Copies the JFrog MCP server JSON to your clipboard and opens Settings, in case the native tool contribution above isn't picked up automatically (see [Known open risk](CONTRIBUTING.md#known-open-risk)). |

## Skills

| Skill | Description |
| --- | --- |
| `jfrog` | JFrog Platform operations via CLI and APIs (Artifactory, Xray, access, projects, and more). |
| `jfrog-package-safety-and-download` | Check package safety and download via Artifactory. |
| `jfrog-ai-catalog-skills` | Discover, install, manage, and publish agent skills from the JFrog AI Catalog via `jf skills` and Agent Guard. |
| `jfrog-mcp-management` | Install, list, and remove MCP servers/tools via JFrog Agent Guard, and browse the JFrog MCP catalog. |
| `jfrog-reference-architecture` | JFrog Platform topology, sizing, deployment, and HA/DR guidance from the official Reference Architecture site. |
| `jfrog-setup-package-managers` | Set up and bind package managers (npm, pip, Maven, Gradle, Go, Docker, Helm, …) to Artifactory via `jf setup`. |

Skill content is vendored under `.junie/skills/` - see [VENDOR.md](VENDOR.md).

## MCP tools

| Tool | Description |
| --- | --- |
| `jfrog_artifactory_search` | Search Artifactory for artifacts, builds, or packages. |
| `jfrog_xray_security_scan` | Run an Xray / Advanced Security scan and summarize findings. |
| `jfrog_ai_catalog_lookup` | Check package safety/curation via the JFrog AI Catalog. |

These are implemented as suspend functions on a single `JfrogToolset` (see [`src/main/kotlin/com/jfrog/jetbrains/mcp/JfrogToolset.kt`](src/main/kotlin/com/jfrog/jetbrains/mcp/JfrogToolset.kt)), registered via `<extensions defaultExtensionNs="com.intellij.mcpServer"><mcpToolset implementation="..."/></extensions>` in [`plugin.xml`](src/main/resources/META-INF/plugin.xml) - the same pattern the platform's own bundled toolsets use. Bodies are stubs (`TODO`) pending the JFrog API wiring - see [CONTRIBUTING.md](CONTRIBUTING.md).

## Prerequisites

- A JetBrains IDE on 2025.2+ with Junie / AI Assistant installed.
- **Skill runtime** (when using the skills) - `jf` CLI, `jq`, and `curl` on `PATH`, plus `JFROG_URL` and `JFROG_ACCESS_TOKEN` (or `jf config add`). See [jfrog-skills requirements](https://github.com/jfrog/jfrog-skills/blob/v0.16.0/README.md#requirements).

## Installation

Not yet published to JetBrains Marketplace. For local development:

```bash
./gradlew runIde
```

or build a distributable zip and install it via **Settings | Plugins | ⚙ | Install Plugin from Disk...**:

```bash
./gradlew buildPlugin
```

## Repository layout

```
jetbrains-plugin/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── src/main/resources/META-INF/plugin.xml   # plugin manifest
├── src/main/resources/META-INF/jfrog-junie.xml  # loaded only when Junie is present
├── src/main/kotlin/com/jfrog/jetbrains/
│   ├── mcp/          # JfrogToolset (McpToolset implementation)
│   └── actions/       # fallback "Configure JFrog MCP" action
├── .junie/skills/     # vendored Agent Skills (see VENDOR.md)
├── .github/scripts/    # sync-skills vendoring
├── LICENSE
├── README.md
└── VENDOR.md
```

## Validate locally

```bash
node scripts/validate-jetbrains-plugin.mjs
```

## Versioning

Bump `version` in [`gradle.properties`](gradle.properties) when you publish a new release, then tag (for example `v0.1.0`).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
