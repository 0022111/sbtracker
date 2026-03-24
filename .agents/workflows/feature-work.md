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
7. **Rebase onto latest `dev` before pushing**: `git fetch origin dev && git rebase origin/dev`
   - Resolve any conflicts, then `git rebase --continue`.
   - This keeps history linear and ensures the PR is conflict-free when it lands.
8. Mark the task `done` in `.agents/TASKS.md`.
   - **Immediately push this change to `dev`**: `git add .agents/TASKS.md && git commit -m "meta: T-XXX done" && git fetch origin dev && git push origin HEAD:dev`
9. Push the functional branch and open a PR: `git push -u origin <branch> && gh pr create --base dev --title "T-XXX — Task Title"`

---

**Need to plan new tasks first?** → use `.agents/workflows/plan-feature.md`
**Need to decide what to work on?** → use `.agents/workflows/orchestrate.md`
