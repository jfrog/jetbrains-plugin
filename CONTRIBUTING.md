# Contributing to JFrog JetBrains Plugin

Thank you for your interest in contributing! This project is maintained by JFrog and licensed under the [Apache License 2.0](LICENSE).

## Contributor License Agreement (CLA)

All contributors must sign the [JFrog CLA](https://jfrog.com/cla/) before contributions can be merged into the official `jfrog/jetbrains-plugin` repository. A CLA check runs automatically on pull requests when CI is enabled — follow the prompts to sign if you haven't already.

## Known open risk

This is the single most important thing to know before working on this repo.

`./gradlew buildPlugin` **succeeds** — the `McpToolset`/`bundledPlugin`/extension-point wiring below was confirmed against a real build, not just documentation:

- `com.intellij.mcpserver` (lowercase — note the casing) is the real package inside the plugin bundled with the IDE (plugin id `com.intellij.mcpServer`, confirmed by decompiling `plugins/mcpserver/lib/mcpserver.jar` from a downloaded `intellijIdea("2025.2.6.2")` distribution). It is a *different, incompatible* API from the older, deprecated standalone Marketplace plugin of the same id — that older one is what most public blog posts/demo plugins (including the one this repo initially copied) show, and it uses an `AbstractMcpTool`/`<mcpTool>` shape that does not exist in the bundled version. Declare the dependency as `bundledPlugin("com.intellij.mcpServer")` in `build.gradle.kts`, **not** `plugin("com.intellij.mcpServer", "<version>")` (the latter resolves the old deprecated artifact).
- The real shape: implement the `com.intellij.mcpserver.McpToolset` marker interface, write plain public `suspend fun` methods (snake_case name = tool name, via reflection — see `JfrogToolset.kt`), and register the class via `<extensions defaultExtensionNs="com.intellij.mcpServer"><mcpToolset implementation="..."/></extensions>` in `plugin.xml`. This matches the platform's own bundled toolsets (`FileToolset`, `ExecutionToolset`, `CodeInsightToolset`, etc. in the same jar) byte-for-byte in shape.

**Still genuinely unconfirmed** (needs a live JetBrains IDE + Junie session — can't be validated from a headless build):

1. Whether tools registered this way are automatically visible to **Junie's own tool-calling**, or whether they only reach *external* MCP clients that connect to the IDE's built-in MCP server. This is the crux of the original design question and still needs a live check.
2. How a toolset method obtains the current `Project` — none of the bundled toolsets take it as a parameter (confirmed via `javap`), so it's resolved ambiently, likely via coroutine context. Decompile `com.intellij.mcpserver.toolsets.general.CodeInsightToolset` (same jar) for the real accessor before wiring actual JFrog API calls into `JfrogToolset.kt`.
3. Junie's actual plugin id is believed to be `org.jetbrains.junie` (via the JetBrains Marketplace API), but this has not been cross-checked against a real installed Junie `plugin.xml`. That's why the `<depends>` on it in `plugin.xml` is `optional="true"` rather than required — a wrong *required* id would break plugin load entirely.

**Before shipping a real release:**

1. Run `./gradlew runIde` (or install the `buildPlugin` zip) in a real JetBrains IDE with Junie.
2. Open Junie chat and check whether `jfrog_artifactory_search` / `jfrog_xray_security_scan` / `jfrog_ai_catalog_lookup` show up as callable tools.
3. If they don't: the `Tools | Configure JFrog MCP...` action (see `actions/ConfigureJfrogMcpAction.kt`) is the documented fallback — confirm it actually gets JFrog's tools working via the manual Settings path instead, and treat that as the primary path until Junie visibility is confirmed.
4. Resolve the `Project`-access pattern (point 2 above) and wire the three `TODO()` bodies in `JfrogToolset.kt` to the real JFrog APIs.

## How to Contribute

1. **Fork** the repository and create a feature branch from `main`.
2. Make your changes, ensuring they follow the existing code style and project conventions.
3. **Validate** locally:

```bash
node scripts/validate-jetbrains-plugin.mjs
```

This checks `plugin.xml` and walks every `.junie/skills/*/SKILL.md` for required YAML frontmatter.

4. **Test** by running the sandbox IDE from the repo root:

```bash
./gradlew runIde
```

5. **Commit** with a clear, descriptive message.
6. Open a **pull request** against `main` with a summary of what changed and why.

### Updating the vendored skills

The `.junie/skills/` tree is vendored from [jfrog/jfrog-skills](https://github.com/jfrog/jfrog-skills) and committed to `main` — see [`VENDOR.md`](VENDOR.md) for the full flow. To regenerate the tree locally against the pin in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json):

```bash
node .github/scripts/sync-skills.mjs
```

This downloads the pinned upstream tarball and replaces `.junie/skills/`. Commit the result alongside any pin/version bumps.

## Pre-release checklist

- [ ] `node scripts/validate-jetbrains-plugin.mjs` passes.
- [ ] `./gradlew buildPlugin` succeeds against a real IntelliJ Platform Gradle sync (see "Known open risk" above).
- [ ] Version bumped in [`gradle.properties`](gradle.properties) when the plugin changes.
- [ ] No secrets, credentials, or files under `**/local-cache/` committed.
- [ ] If the skill tree changed: `pin` in `.github/scripts/sync-skills-vendor.json` matches the upstream tag the new tree was generated from.
- [ ] Smoke-test: `./gradlew runIde`, confirm skills + MCP tools surface in Junie (or the fallback action works).

## Build order

Releases follow a fixed sequence:

1. **Skills** — `.junie/skills/` bundle vendored from `jfrog/jfrog-skills`.
2. **MCP tools** — native `mcpTool` contribution to the IDE's built-in MCP server (pending validation — see "Known open risk").
3. **Marketplace publish** — once (1) and (2) are validated against a real Junie build.

Do not merge Marketplace-publish tooling before (1) and (2) are validated.

## Reporting Issues

Open a [GitHub issue](https://github.com/jfrog/jetbrains-plugin/issues) with:

- A clear title and description of the problem.
- Steps to reproduce (if applicable).
- Expected vs. actual behavior.
- JetBrains IDE + build number, and Junie version.

## Code Guidelines

- Keep changes focused — one logical change per PR.
- Follow existing patterns and naming conventions in the codebase.
- Do not commit secrets, credentials, or API keys.
- Add copyright headers to new source files:

```
// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0
```

## Code of Conduct

Be respectful and constructive. We are committed to providing a welcoming and inclusive experience for everyone.

## Questions?

Reach out to the JFrog DevRel team at devrel@jfrog.com.
