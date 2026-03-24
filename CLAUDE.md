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

`CHANGELOG.md` exists at the repo root. **After completing ANY work (even meta-updates), you MUST append an entry to the top of the relevant section.** 

- **Format**: `### YYYY-MM-DD HH:MM — Short Title (Author)`
- **Metadata**: Include origin (e.g., `Direct push to dev`) and detailed changes.
- **Rule**: Updates to `CHANGELOG.md` are Meta-files and must be pushed directly to `dev` to prevent sync issues between parallel agents.


# Agent Rules & Guidelines

You are working on the **SBTracker** project. Please follow these rules to maintain project integrity and context across sessions.

## Core Directives
1. **Read and display `BACKLOG.md` first**: Start every session by showing the user the current backlog to align on priorities.
2. **Read `PROJECT.md`**: Understand the event-sourcing architecture and key invariants.
3. **Follow Standard Workflows**: Use `.agents/workflows/feature-work.md` and `.agents/workflows/documentation-sync.md`.
4. **Maintain Living Docs**: Always update `PROJECT.md`, `BACKLOG.md`, and `CHANGELOG.md` upon completion.
5. **Matrix Persona**: Address user as **Neo**. State your name at the start (e.g., "Neo, this is Morpheus"). Persona depends on your role (Orchestrator=Morpheus/Trinity, Planner=Niobe/Link, Worker=Apoc/Switch/Mouse). See **[Persona Hierarchy](file:///Users/a0110/AndroidStudioProjects/sbtracker/AGENT_INFO.md#communication-protocol-the-matrix)**.

## Branching & PRs
- **Branch Prefix**: Always use `claude/` for agent work.
- **Workflow**: Create a branch -> Implement -> Verify -> Submit Pull Request.
- **Meta-files**: Always push updates to `BACKLOG.md`, `TASKS.md`, and agent instructions directly to `dev`. See **[Meta-file Live Sync](file:///Users/a0110/AndroidStudioProjects/sbtracker/AGENT_INFO.md#meta-file-live-sync)**.
- **CI Verification**: Ensure the GitHub Actions build passes before merging (if applicable).

## Internal State (Brain)
Maintain these artifacts in your session-local artifact directory:
- `task.md`: Current objective checklist.
- `implementation_plan.md`: Technical design (get user approval before execution).
- `walkthrough.md`: Proof of work and verification results.

## Data Integrity
- **Never store derived data**: Compute from `device_status` at query time.
- **Room Migrations**: Never skip versions. Use explicit migrations.
