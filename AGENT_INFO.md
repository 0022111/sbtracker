# Agent Information

This file provides context and guidelines for AI agents (like Claude) working on the SBTracker project.

## Branch Strategy

### The flow

```
feature branch  →  PR  →  dev  →  (periodically) main
```

Each task gets its own isolated branch. That branch proposes changes to `dev` via a Pull Request. `dev` is the continuously-updated source of accepted work. `main` is stable/release only.

**Why this matters for agents running in parallel:**
- Multiple agents can work on different tasks simultaneously without interfering — each lives on its own branch
- When an agent starts a new task it always branches from `dev`, so it starts from the latest accepted state
- When a PR merges, other in-flight branches do not automatically get those changes — they only see them if they rebase/merge from `dev`
- Two branches that both modify the same file will produce a **merge conflict** on the second PR to merge; whoever is second must resolve it

**Practical rules:**
1. Always `git fetch origin dev && git checkout -b claude/T-XXX-name origin/dev` — never branch from another feature branch
2. One task per branch — keep scope narrow to minimize conflicts
3. Open a PR to `dev` as the last step; do not push directly to `dev` or `main`
4. If your branch is behind `dev` (other PRs merged while you worked), rebase: `git fetch origin dev && git rebase origin/dev`

### Naming convention
- **Prefix**: `claude/` for all agent branches
- **Format**: `claude/T-XXX-short-description`

## GitHub Integrity

To maintain a stable and verifiable codebase, always:
- **Use PRs**: Never commit directly to `main`. Use Pull Requests to document and review changes.
- **Check CI**: Ensure the GitHub Actions build passes for your branch/PR.
- **Verification**: Document your verification steps in the PR description using the provided template.

## Session Initialization

At the start of every new session or task, the agent **must**:
1.  **Read and display `BACKLOG.md`** to the user to align on current priorities.
2.  **Verify the current branch** and ensure it matches the task (or create a new one).
3.  **Check for existing `task.md`** in the artifact directory to resume pending work.

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
