# Changelog Fragments

Each PR drops a small file here instead of editing the shared `CHANGELOG.md`.
No merge conflicts. No coordination required.

## Worker Rule

Create one file per PR named after your task ID or branch slug:

```
changelogs/T-085.md
changelogs/2026-03-26_portability-docs.md
```

Content format — keep it to 1–4 lines:

```
YYYY-MM-DD — Short Title (T-XXX or branch slug)
- **Added/Fixed/Changed/Improved** description
```

## Orchestrator Rule

At release time: concatenate all fragments (newest first) into the top of
`CHANGELOG.md`, then delete the fragment files and commit.

```bash
# Preview recent changes
ls -t changelogs/*.md | grep -v README

# Merge at release (example)
cat $(ls -t changelogs/*.md | grep -v README) | cat - CHANGELOG.md > /tmp/cl && mv /tmp/cl CHANGELOG.md
```

## Never edit `CHANGELOG.md` directly.

It is the historical release record. Fragments are the live working layer.
