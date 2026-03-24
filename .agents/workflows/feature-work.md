---
description: standard workflow for feature development and bug fixes
---

> **Start here**: Read `.agents/TASKS.md`. Find a `ready` task. Read its file in `.agents/tasks/`.
> That file is your complete scope — do not read files it doesn't list.

1. Pick a `ready` task from `.agents/TASKS.md` and read its task file.
2. Create a branch: `git checkout -b claude/T-XXX-description`
3. Follow the steps in the task file exactly.
   - Read only the files listed under "Read these files first".
   - Change only the files listed under "Change only these files".
4. Run `./gradlew assembleDebug` — must pass before committing.
5. Commit with message referencing the task ID: `T-XXX: description of change`.
6. Run the `/documentation-sync` workflow.
7. Mark the task `done` in `.agents/TASKS.md`.
8. Open a PR. Title: `T-XXX — Task Title`.
