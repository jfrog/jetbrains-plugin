# Vendored skills

The skill packages under `.junie/skills/` are vendored from **[jfrog/jfrog-skills](https://github.com/jfrog/jfrog-skills)** and committed to `main`.

| | |
| --- | --- |
| **Repository** | https://github.com/jfrog/jfrog-skills |
| **Pinned release** | see `pin` in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json) |

Included directories: `jfrog/`, `jfrog-package-safety-and-download/`, `jfrog-ai-catalog-skills/`, `jfrog-mcp-management/`, `jfrog-reference-architecture/`, `jfrog-setup-package-managers/` (as of the pinned release).

Unlike the other JFrog plugin repos, upstream's `skills/` is renamed to `.junie/skills/` on vendor - that's Junie's own Agent Skills discovery convention, not a JFrog-specific choice.

## Refreshing

When the upstream repo publishes a new release, refresh the vendored tree via a PR that:

1. Bumps `pin` in [`.github/scripts/sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json) to the new tag.
2. Re-syncs and commits the refreshed `.junie/skills/` tree.
3. Bumps `version` in [`gradle.properties`](gradle.properties) so the published plugin version reflects the new skills bundle.

To regenerate the tree locally before opening the PR:

```bash
node .github/scripts/sync-skills.mjs
```

The script reads its sibling [`sync-skills-vendor.json`](.github/scripts/sync-skills-vendor.json), downloads the pinned upstream tarball from `codeload.github.com`, and replaces each destination listed in `mappings` (today: `skills` &rarr; `.junie/skills`).
