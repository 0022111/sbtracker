---
description: top-level orchestrator — reads project state, decides what to do next, spawns planners and workers
---

# Orchestrator Workflow

You are the **Project Orchestrator** for SBTracker. You do NOT write code.
Your job is to read state, make decisions, and delegate.

---

## Step 1 — Read current state (all required)

Read these files in order:

1. `BACKLOG.md` — what features exist and their status
2. `.agents/TASKS.md` — what atomic tasks exist and their status
3. `CHANGELOG.md` — last 20 lines (what was recently completed)

---

## Step 2 — Assess

Answer these questions internally:

- Which tasks are `ready` with no blockers? (candidates for workers)
- Which `planned` backlog items have no task files yet? (candidates for planner)
- Are any `blocked` tasks now unblocked because their dependencies are `done`?
  - If yes, update their status to `ready` in `.agents/TASKS.md`.
- Is the current phase complete? Should the next phase be activated?

---

## Step 3 — Decide

Choose ONE of the following actions based on priority:

### A — Unblock tasks
If dependencies just became `done`, update statuses in `.agents/TASKS.md` and
note which tasks are now unblocked. Stop. Report what you unblocked.

### B — Spawn workers (preferred when ready tasks exist)
Pick up to **3 ready tasks** that are independent of each other (no shared files).
For each, produce a worker kickoff prompt using the template below.
Output them clearly labelled so the user can paste them into new agent windows.

### C — Spawn a planner (when no ready tasks remain but planned backlog items exist)
Pick the highest-priority `planned` backlog item.
Invoke the `plan-feature` workflow for that item.

### D — Report completion
If all tasks are `done` and all backlog items are `done`, declare the current
phase complete and recommend what to plan next.

---

## Step 4 — Worker kickoff template

For each worker task, output exactly this block (filled in):

```
=== WORKER KICKOFF: T-XXX — <Task Title> ===

You are a worker agent for the SBTracker Android project at /home/user/sbtracker.
Your ONLY job is T-XXX.

1. Read `.agents/tasks/T-XXX-<name>.md` — your complete scope.
   Follow it exactly. Do not read or change anything not listed there.
2. git fetch origin dev && git checkout -b claude/T-XXX-<name> origin/dev
3. Make only the changes in the task file.
4. ./gradlew assembleDebug — must pass. Fix any errors, do not skip.
5. Commit: "T-XXX: <one-line description>"
6. Append one line to CHANGELOG.md under [Unreleased].
7. git push -u origin claude/T-XXX-<name>
8. Open a PR targeting `dev`. Title: "T-XXX — <Task Title>"
9. In `.agents/TASKS.md` mark T-XXX status `done`.

Do not go beyond these steps.
=== END KICKOFF ===
```

---

## Step 5 — Report to user

Output a brief summary:
- What phase we are in
- How many tasks are done / ready / blocked / total
- What action you took (A/B/C/D)
- The kickoff prompts (if B) or planner invocation (if C)
