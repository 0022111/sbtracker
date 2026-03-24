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
