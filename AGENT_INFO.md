# Agent Information

This file provides context and guidelines for AI agents (like Claude) working on the SBTracker project.

## Branch Strategy

To maintain a clear distinction between automated agent work and manual developer changes, all agents should follow this branch naming convention:

- **Prefix**: `claude/` (for Claude-based agents)
- **Format**: `claude/feature-or-fix-description`
- **Persistent/Reference Branch**: `claude/verify-git-access-BVVfi`
    - This branch was established during initial environment setup to verify repository access and remains as a reference point for agent connectivity.

### Guidelines for Agents
1. **Always** create a new branch with the `claude/` prefix for any non-trivial work.
2. **Sync** with `main` regularly.
3. **Reference** task IDs from `BACKLOG.md` in branch names if applicable (e.g., `claude/F-045-agent-docs`).

## Agent Role

Agents are responsible for maintaining the project's "living documentation" (`PROJECT.md`, `BACKLOG.md`, `CHANGELOG.md`) to ensure context persistence across different sessions.

### Internal Agent State (Brain)

To provide the best possible assistance, agents must maintain several internal artifacts within their "brain" (artifact directory) during a session:

1.  **`task.md`**: A living checklist of the current objective.
2.  **`implementation_plan.md`**: A detailed technical design for the current task.
3.  **`walkthrough.md`**: A summary of work done, including verification results.

These artifacts are *session-local* but critical for maintaining state during complex multi-step tasks.

## Standard Workflows

Agents should follow the standardized workflows located in `.agents/workflows/`:

- **[/feature-work](file:///.agents/workflows/feature-work.md)**: Standard procedure for starting and completing a feature or fix.
- **[/documentation-sync](file:///.agents/workflows/documentation-sync.md)**: Steps to keep `PROJECT.md`, `BACKLOG.md`, and `CHANGELOG.md` updated.
