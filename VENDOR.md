# Vendored skills

The skill packages under `.junie/skills/` are vendored from **[jfrog/jfrog-skills](https://github.com/jfrog/jfrog-skills)** and committed to `main`.

| | |
| --- | --- |
| **Repository** | https://github.com/jfrog/jfrog-skills |
| **Pinned release** | see `pin` in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json) |
| **Plugin version** | see `version` in [`gradle.properties`](gradle.properties), mirrored by [`VERSION`](VERSION) |

Included directories: `jfrog/`, `jfrog-package-safety-and-download/`, `jfrog-ai-catalog-skills/`, `jfrog-mcp-management/`, `jfrog-reference-architecture/`, `jfrog-setup-package-managers/` (as of the pinned release).

The README deliberately omits release numbers. The Gradle property and GitHub tags/releases are the authoritative plugin-version sources; the vendor configuration is the authoritative skills pin.

Unlike the other JFrog plugin repos, upstream's `skills/` is renamed to `.junie/skills/` on vendor - that's Junie's own Agent Skills discovery convention, not a JFrog-specific choice.

At build time the vendored tree is zipped into the plugin jar (`junie/junie-skills.zip`) so the startup deployer ([`JfrogJunieDeployer`](src/main/kotlin/com/jfrog/jetbrains/startup/JfrogJunieDeployer.kt)) can materialize it into the user's `~/.junie/skills/` - see the README's "How delivery works".

## Refreshing

When the upstream repo publishes a new release, refresh the vendored tree via a PR that:

1. Bumps `pin` in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json) to the new tag.
2. Re-syncs and commits the refreshed `.junie/skills/` tree.
3. Bumps [`VERSION`](VERSION) and the matching `version` in [`gradle.properties`](gradle.properties) so the published plugin version reflects the new skills bundle.

To regenerate the tree locally before opening the PR:

```bash
node .github/scripts/sync-skills.mjs
```

The script reads its sibling [`sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json), downloads the pinned upstream tarball from `codeload.github.com` (public `jfrog/jfrog-skills`), and replaces each destination listed in `mappings` (today: `skills` &rarr; `.junie/skills`).

# Vendored hooks modules

The `modules/` tree at the repo root is vendored from **jfrog-agent-hooks** by its `sync-plugins.yml` workflow: each `jfrog-agent-hooks/v*` release tag opens a sync pull request here. Never edit or hand-copy it: the sync stamps the hooks version, and an unstamped copy reports `0.0.0`, which install telemetry skips.

| | |
| --- | --- |
| **Pinned release** | see `pin` in `.github/scripts/sync-modules-vendor.json` (the first sync creates it) |
| **Plugin version** | not bumped by the sync (it bumps only JSON files): bump [`VERSION`](VERSION) and `gradle.properties` by hand when releasing, as for the skills |

At build time the tree is zipped into the plugin jar (`junie/junie-modules.zip`). On project open, [`JfrogJunieDeployer`](src/main/kotlin/com/jfrog/jetbrains/startup/JfrogJunieDeployer.kt) unpacks it to `~/.junie/jfrog/modules/`, writes the plugin version to `~/.junie/jfrog/.jetbrains-plugin/plugin.json`, and starts `modules/jetbrains-session-start.mjs` with `node` for install telemetry.
