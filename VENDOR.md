# Vendored skills

The skill packages under `.junie/skills/` are vendored from **[jfrog/jfrog-skills](https://github.com/jfrog/jfrog-skills)** and committed to `main`.

| | |
| --- | --- |
| **Repository** | https://github.com/jfrog/jfrog-skills |
| **Pinned release** | see `pin` in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json) |

Included directories: `jfrog/`, `jfrog-package-safety-and-download/`, `jfrog-ai-catalog-skills/`, `jfrog-mcp-management/`, `jfrog-reference-architecture/`, `jfrog-setup-package-managers/` (as of the pinned release).

Unlike the other JFrog plugin repos, upstream's `skills/` is renamed to `.junie/skills/` on vendor - that's Junie's own Agent Skills discovery convention, not a JFrog-specific choice.

At build time the vendored tree is zipped into the plugin jar (`junie/junie-skills.zip`) so the startup deployer ([`JfrogJunieDeployer`](src/main/kotlin/com/jfrog/jetbrains/startup/JfrogJunieDeployer.kt)) can materialize it into the user's `~/.junie/skills/` - see the README's "How delivery works".

## Refreshing

When the upstream repo publishes a new release, refresh the vendored tree via a PR that:

1. Bumps `pin` in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json) to the new tag.
2. Re-syncs and commits the refreshed `.junie/skills/` tree.
3. Bumps `version` in [`gradle.properties`](gradle.properties) so the published plugin version reflects the new skills bundle.

To regenerate the tree locally before opening the PR:

```bash
node .github/scripts/sync-skills.mjs
```

The script reads its sibling [`sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json), downloads the pinned upstream tarball from `codeload.github.com` (public `jfrog/jfrog-skills`), and replaces each destination listed in `mappings` (today: `skills` &rarr; `.junie/skills`).
