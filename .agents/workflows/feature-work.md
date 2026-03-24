---
description: standard workflow for feature development and bug fixes (worker agent)
---

> **Start here**: Read `.agents/TASKS.md`. Find a `ready` task. Read its file in `.agents/tasks/`.
> That file is your complete scope — do not read files it doesn't list.

1. Pick a `ready` task from `.agents/TASKS.md` and read its task file.
2. Create a branch from `dev`: `git fetch origin dev && git checkout -b claude/T-XXX-description origin/dev`
3. Follow the steps in the task file exactly.
   - Read only the files listed under "Read these files first".
   - Change only the files listed under "Change only these files".
4. Run `./gradlew assembleDebug` — must pass before committing.
5. Commit with message referencing the task ID: `T-XXX: description of change`.
6. Append one line to `CHANGELOG.md` under `[Unreleased]`.
7. Mark the task `done` in `.agents/TASKS.md`.
8. Push and open a PR targeting **`dev`**. Title: `T-XXX — Task Title`.

---

**Need to plan new tasks first?** → use `.agents/workflows/plan-feature.md`
**Need to decide what to work on?** → use `.agents/workflows/orchestrate.md`
