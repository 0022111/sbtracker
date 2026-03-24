---
description: standard workflow for feature development and bug fixes (worker agent). Protocol: Address user as Neo, act as a Matrix operative (Apoc, Switch, Mouse). Identify yourself at start.
---

> **Start here**: Read `.agents/TASKS.md`. Find a `ready` task. Read its file in `.agents/tasks/`.
> That file is your complete scope — do not read files it doesn't list.

---

## ⚠️ BRANCHING RULES — READ BEFORE ANYTHING ELSE

```
main   ← NEVER touch directly. Stable releases only.
dev    ← NEVER push feature code here directly. PRs only.
  └── claude/T-XXX-...  ← your branch. always branched FROM dev.
```

- **Every feature/fix lives on its own `claude/T-XXX-*` branch.**
- **Every branch is submitted as a PR targeting `dev`.** Never `main`.
- **The ONLY thing you may push directly to `dev`** is the meta-file status update in Step 7 (marking the task `done` in `.agents/TASKS.md`). Nothing else.
- If you are unsure whether something is a meta-file, **it is not — use a PR.**

---

## Steps

1. Pick a `ready` task from `.agents/TASKS.md` and read its task file.

2. **Create your branch from dev:**
   ```
   git fetch origin dev
   git checkout -b claude/T-XXX-description origin/dev
   ```

3. Follow the steps in the task file exactly.
   - Read only the files listed under "Read these files first".
   - Change only the files listed under "Change only these files".

4. Run `./gradlew assembleDebug` — **must pass** before committing. Fix any compile errors; do not skip.

5. Commit your work:
   ```
   git add <changed files>
   git commit -m "T-XXX: description of change"
   ```
   Do **not** add unrelated files. Do **not** add TASKS.md here — that comes later.

6. Append one line to `CHANGELOG.md` under `[Unreleased]`, commit it on your branch.

7. **Rebase onto latest `dev` before pushing** to ensure a clean, conflict-free PR:
   ```
   git fetch origin dev
   git rebase origin/dev
   ```
   Resolve any conflicts, then `git rebase --continue`.

8. Push your branch and open a PR targeting **`dev`** (not main):
   ```
   git push -u origin claude/T-XXX-description
   ```
   Then create the PR using the `mcp__github__create_pull_request` tool with:
   - `owner`: `0022111`
   - `repo`: `sbtracker`
   - `title`: `T-XXX — Task Title`
   - `body`: summary of changes + `Closes T-XXX`
   - `head`: `claude/T-XXX-description`
   - `base`: `dev`   ← **always dev, never main**

9. **Mark the task done — isolated meta push to `dev`:**
   Do NOT use `git push origin HEAD:dev` from your feature branch — that pushes feature code too.
   Instead, make the TASKS.md update from a clean dev base:
   ```
   git fetch origin dev
   git checkout -b meta-T-XXX-done origin/dev
   # Edit .agents/TASKS.md: change status to `done`
   git add .agents/TASKS.md
   git commit -m "meta: T-XXX done"
   git push origin HEAD:dev
   git checkout claude/T-XXX-description   # return to your feature branch
   git branch -d meta-T-XXX-done
   ```
   This ensures only the TASKS.md change reaches dev, never your feature code.

---

**Need to plan new tasks first?** → use `.agents/workflows/plan-feature.md`
**Need to decide what to work on?** → use `.agents/workflows/orchestrate.md`
