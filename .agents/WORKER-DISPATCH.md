# Worker Dispatch — 8 Parallel Tasks (2026-03-25)

**Mission**: Syntax-correct code. No build required. PRs to `dev`.

---

## Task Assignment

| Worker | Task ID | Branch | Scope |
|--------|---------|--------|-------|
| 1 | T-047 | `claude/T-047-landing-idle-temp-charge-state` | Landing: suppress idle 0°C, add charge badge |
| 2 | T-067 | `claude/T-067-session-stats-starting-battery` | SessionStats: add startingBattery field |
| 3 | T-076 | `claude/T-076-hit-classification-fields` | AnalyticsModels: add hit classification fields |
| 4 | T-077 | `claude/T-077-hit-achievement-metrics` | AnalyticsRepository: compute hit achievements |
| 5 | T-069 | `claude/T-069-notification-channels` | Consolidate notification channels |
| 6 | T-075 | `claude/T-075-notification-permission-handling` | Android 13+ POST_NOTIFICATIONS permission |
| 7 | T-042 | `claude/T-042-session-program-entity` | SessionProgram entity + DAO + migration 3→4 |
| 8 | T-043 | `claude/T-043-program-repository` | ProgramRepository CRUD + presets |

---

## Process

### For Each Worker:

1. **Read task file**: `.agents/tasks/T-XXX-description.md` — **this is your full scope**
2. **Create branch**: `git checkout -b claude/T-XXX-short-desc` from `dev`
3. **Implement**: Follow task file instructions exactly
4. **Checkpoint**: Code syntax correct (no IDE errors)
5. **Commit**: Clear message, reference task ID
6. **Push**: `git push -u origin claude/T-XXX-short-desc`
7. **PR**: Target `dev` branch, link task file in description

### No Build Required

Gradle/environment won't block you. CI validates after merge.

---

## Branch Base

All branches start from `dev`:
```bash
git checkout dev && git pull origin dev
git checkout -b claude/T-XXX-...
```

---

## PR Template

```
## Task
Closes/Implements #[TASK-ID]

## What changed
[Brief summary of changes]

## Verification
- [ ] Code syntax correct (no IDE errors)
- [ ] Follows task file scope
- [ ] Ready for CI validation

Task file: `.agents/tasks/T-XXX-description.md`
```

---

Ready to work. Deploy when Morpheus gives the signal.
