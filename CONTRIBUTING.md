# Contributing to JFrog for Coding Agents

Thank you for your interest in contributing! This project is maintained by JFrog and licensed under the [Apache License 2.0](LICENSE).

## Contributor License Agreement (CLA)

All contributors must sign the [JFrog CLA](https://jfrog.com/cla/) before contributions can be merged into the official `jfrog/jetbrains-plugin` repository. A CLA check runs automatically on pull requests when CI is enabled — follow the prompts to sign if you haven't already.

## Validate in a real IDE

For end-to-end checks, install the `buildPlugin` zip into a real IntelliJ + Junie (**Settings | Plugins | ⚙ | Install Plugin from Disk...**). The `runIde` sandbox can't run Junie end-to-end.

## How to Contribute

1. **Fork** the repository and create a feature branch from `main`.
2. Make your changes, ensuring they follow the existing code style and project conventions.
3. **Validate** locally:

```bash
node scripts/validate-jetbrains-plugin.mjs
```

This checks `plugin.xml` and walks every `.junie/skills/*/SKILL.md` for required YAML frontmatter.

4. **Test** by installing the built zip (`./gradlew buildPlugin`) into a real IntelliJ + Junie — see "Validate in a real IDE" above.

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
- [ ] `./gradlew buildPlugin` succeeds against a real IntelliJ Platform Gradle sync.
- [ ] Version bumped in [`gradle.properties`](gradle.properties) when the plugin changes.
- [ ] No secrets, credentials, or files under `**/local-cache/` committed.
- [ ] If the skill tree changed: `pin` in `.github/scripts/sync-skills-vendor.json` matches the upstream tag the new tree was generated from.
- [ ] Smoke-test: install the `buildPlugin` zip in a real IDE, confirm the JFrog skills + JFrog MCP surface in Junie.

## Build order

Releases follow a fixed sequence:

1. **Skills** — `.junie/skills/` bundle vendored from `jfrog/jfrog-skills`, delivered to `~/.junie/` on startup.
2. **JFrog (remote) MCP** — the `jfrog` server entry merged into `~/.junie/mcp/mcp.json`.
3. **Marketplace publish** — once (1) and (2) are validated against a real Junie build, via the [`Publish to JetBrains Marketplace`](.github/workflows/publish-marketplace.yml) workflow (see the README "Publishing to JetBrains Marketplace" section).

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
