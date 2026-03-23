# sbtracker - Claude Code Notes

## Build & Test

```bash
# Build debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/*.apk

# Run unit tests (none yet)
./gradlew test

# Run lint
./gradlew lint
```

Requires JDK 17 (JetBrains distribution). The CI workflow mirrors these commands.

## Changelog

`CHANGELOG.md` exists at the repo root. **After completing any work, append an entry to the top of the `[Unreleased]` section** using the existing format:

```
### YYYY-MM-DD — Short Title
- **Fixed/Added/Changed/Improved** description
```

## Git Push Workaround

**The platform's git proxy and GitHub MCP integration do NOT have write access to this repo.**

- `git push` via the local proxy (`127.0.0.1:*`) will fail with `403 Permission denied`
- `mcp__github__create_branch` / `mcp__github__push_files` will fail with `403 Resource not accessible by integration`

**To push branches or create refs, use the GitHub REST API directly with the PAT:**

The PAT is available as `$GITHUB_PAT` (set in `~/.claude/settings.json`).

```bash
# Create a branch
curl -s -X POST \
  -H "Authorization: token $GITHUB_PAT" \
  -H "Content-Type: application/json" \
  -d "{\"ref\":\"refs/heads/<branch-name>\",\"sha\":\"<sha>\"}" \
  https://api.github.com/repos/0022111/sbtracker/git/refs

# Push a file (creates a commit on the branch)
CONTENT=$(base64 -w 0 <file>) && curl -s -X PUT \
  -H "Authorization: token $GITHUB_PAT" \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"<commit msg>\",\"content\":\"$CONTENT\",\"branch\":\"<branch>\"}" \
  https://api.github.com/repos/0022111/sbtracker/contents/<path>

# Update an existing file (requires sha from: git rev-parse <branch>:<path>)
CONTENT=$(base64 -w 0 <file>) && curl -s -X PUT \
  -H "Authorization: token $GITHUB_PAT" \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"<commit msg>\",\"content\":\"$CONTENT\",\"sha\":\"<file-sha>\",\"branch\":\"<branch>\"}" \
  https://api.github.com/repos/0022111/sbtracker/contents/<path>
```

After pushing via API, sync the local branch:
```bash
git fetch origin <branch-name>
git branch -u origin/<branch-name> <branch-name>
```

To sync the local tracking branch with the remote.
