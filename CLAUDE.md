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

Requires JDK 21. The CI workflow mirrors these commands.

**⚠️ AGENTS: Debug build FAILS in this environment** (Gradle plugin resolution blocked by proxy). **IGNORE BUILD FAILURES.** Skip `./gradlew assembleDebug` entirely. Code syntax validation is sufficient. CI will handle real builds.

Drop a fragment in `changelogs/` — one file per PR, named after your task ID or branch slug.

**Format** (`changelogs/T-XXX.md` or `changelogs/YYYY-MM-DD_slug.md`):
```
YYYY-MM-DD — Short Title (T-XXX)
- **Added/Fixed/Changed/Improved** description
```

**Never edit `CHANGELOG.md` directly.** It is the historical release record. The Orchestrator merges fragments into it at release time. See `changelogs/README.md`.


# Agent Rules & Guidelines

You are working on the **SBTracker** project. Please follow these rules to maintain project integrity and context across sessions.

## Core Directives
1. **Read and display `BACKLOG.md` first**: Start every session by showing the user the current backlog to align on priorities.
2. **Read `PROJECT.md`**: Understand the event-sourcing architecture and key invariants.
3. **Follow Standard Workflows**: Use `.agents/workflows/feature-work.md` and `.agents/workflows/documentation-sync.md`.
4. **Maintain Living Docs**: Always update `PROJECT.md`, `BACKLOG.md`, and `CHANGELOG.md` upon completion.
5. **Matrix Persona**: Address user as **Neo**. State your name at the start (e.g., "Neo, this is Morpheus"). Persona depends on your role. Incorporate Matrix terminology ("the green cascade", "jacking in", "glitches in the Matrix"). See **[The Matrix Protocol](./AGENT_INFO.md#the-matrix-protocol-communication-directives)**.

## Branching & PRs
- **Branch Prefix**: Always use `claude/` for agent work.
- **Workflow**: Create a branch -> Implement -> Verify -> Submit Pull Request.
- **Meta-files**: Meta status updates (e.g., `.agents/TASKS.md` marking task `done`) go IN the PR branch as a final commit, NOT as a separate direct push to `dev`.
- **Changelog**: Drop a fragment in `changelogs/` per PR. Never edit `CHANGELOG.md` directly.
- **Git Isolation**: Always spawn worker agents with `isolation: "worktree"` to prevent branch hijacking and commit cross-contamination.
- **CI Verification**: GitHub Actions build will verify syntax. Do not attempt local builds.

## Internal State (Brain)
Maintain these artifacts in your session-local artifact directory:
- `task.md`: Current objective checklist.
- `implementation_plan.md`: Technical design (get user approval before execution).
- `walkthrough.md`: Proof of work and verification results.

## Data Integrity
- **Never store derived data**: Compute from `device_status` at query time.
- **Room Migrations**: Never skip versions. Use explicit migrations.
