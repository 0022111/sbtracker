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

The PAT must be provided by the user at the start of each session (no persistent secret storage available in Research Preview). If `$GITHUB_PAT` is not set, ask the user for it before attempting any push.

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

---

# Agent Rules & Guidelines

You are working on the **SBTracker** project. Please follow these rules to maintain project integrity and context across sessions.

## Core Directives
1. **Read and display `BACKLOG.md` first**: Start every session by showing the user the current backlog to align on priorities.
2. **Read `PROJECT.md`**: Understand the event-sourcing architecture and key invariants.
3. **Follow Standard Workflows**: Use `.agents/workflows/feature-work.md` and `documentation-sync.md`.
3. **Maintain Living Docs**: Always update `PROJECT.md`, `BACKLOG.md`, and `CHANGELOG.md` upon completion.

## Branching & PRs
- **Branch Prefix**: Always use `claude/` for agent work.
- **Workflow**: Create a branch -> Implement -> Verify -> Submit Pull Request.
- **CI Verification**: Ensure the GitHub Actions build passes before merging (if applicable).

## Internal State (Brain)
Maintain these artifacts in your session-local artifact directory:
- `task.md`: Current objective checklist.
- `implementation_plan.md`: Technical design (get user approval before execution).
- `walkthrough.md`: Proof of work and verification results.

## Data Integrity
- **Never store derived data**: Compute from `device_status` at query time.
- **Room Migrations**: Never skip versions. Use explicit migrations.
