Display the following as a clean, formatted reminder — no extra commentary:

---

## SBTracker — Session Quickstart

### Branching model
```
main  ← stable; never push here directly
  └── dev  ← all agent PRs target this
        └── claude/T-XXX-...  ← your branch
```

### Starting a session
1. Check **`.agents/TASKS.md`** — find a `ready` task
2. Read its file in **`.agents/tasks/T-XXX-*.md`** — that is your full scope
3. Cut branch from `dev`: `git checkout -b claude/T-XXX-short-description origin/dev`

### Doing the work
- Read **only** the files the task file lists
- Change **only** the files the task file allows
- Follow the numbered steps exactly

### Before pushing
```
./gradlew assembleDebug   ← must pass
```

### Wrapping up
1. Commit: `T-XXX: description`
2. Mark task `done` in `.agents/TASKS.md`
3. Append to `CHANGELOG.md`
4. Push and open a PR targeting **`dev`** — title: `T-XXX — Task Title`

---

### Key files
| File | Purpose |
|---|---|
| `.agents/TASKS.md` | Task index — start here |
| `.agents/tasks/T-XXX-*.md` | Scoped task files |
| `PROJECT.md` | Architecture reference |
| `CHANGELOG.md` | Change log |

### Ready right now
Check `.agents/TASKS.md` for the current `ready` list — T-022 through T-029 are all unblocked.
