Display the following as a clean, formatted reminder — no extra commentary:

---

## SBTracker — Session Quickstart

### Starting a session
1. Check **`.agents/TASKS.md`** — find a `ready` task
2. Read its file in **`.agents/tasks/T-XXX-*.md`** — that is your full scope
3. Create branch: `git checkout -b claude/T-XXX-short-description`

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
3. Update `BACKLOG.md` (mark feature done)
4. Append to `CHANGELOG.md`
5. Open a PR — title: `T-XXX — Task Title`

---

### Key files
| File | Purpose |
|---|---|
| `.agents/TASKS.md` | Task index — start here |
| `.agents/tasks/T-XXX-*.md` | Scoped task files |
| `.agents/implementation_plan.md` | Full phased plan |
| `PROJECT.md` | Architecture reference |
| `BACKLOG.md` | Feature + bug tracker |

### Ready right now (Phase 0)
- **T-001** Upgrade dependencies (Room stable, targetSdk 35)
- **T-002** Enable R8 minification
- **T-003** Fix TEMP_DIP_THRESHOLD duplication
- **T-004** Data retention / pruning
- **T-005** targetSdk 35 compat pass *(run after T-001)*
