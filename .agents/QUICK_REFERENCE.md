# 🚀 Quick Reference — Agent Workflows

**TL;DR**: Use the slash commands. They enforce the right procedure.

---

## For Neo (The User)

### Starting Work
You have three entry points:

| Need | Command | File |
|------|---------|------|
| Prioritize & delegate | `/morpheus` or `/orchestrate` | `.agents/workflows/orchestrate.md` |
| Plan a feature | `/plan-feature F-XXX` | `.agents/workflows/plan-feature.md` |
| Intake a new idea | `/intake` | `.agents/workflows/intake.md` |

---

## For Orchestrators (Morpheus / Trinity)

### Your Job
- Read state: `BACKLOG.md`, `.agents/TASKS.md`, `CHANGELOG.md`
- Decide: What's ready? What's blocked? What's next?
- Delegate: Spawn workers, planners, or other orchestrators

### Command
```bash
/orchestrate
# or
/morpheus
```

### What It Does
1. Reads current project state
2. Identifies ready tasks, blockers, completed work
3. Outputs worker kickoff prompts (copy/paste into new agent windows)
4. Updates `.agents/TASKS.md` to unblock tasks

### Example Flow
```
/orchestrate
→ [Shows 3 ready tasks, 2 blocked, 1 done]
→ [Spawns 2 independent workers]
→ [Updates TASKS.md]
```

---

## For Planners (Niobe / Link)

### Your Job
- Decompose a backlog feature into atomic tasks
- Create `.agents/tasks/T-*.md` files
- Update `.agents/TASKS.md` and `BACKLOG.md`

### Command
```bash
/plan-feature F-018
# or for new ideas
/intake
```

### What It Does
1. Reads the feature in `BACKLOG.md`
2. Creates `.agents/tasks/T-XXX-name.md` files (one per atomic task)
3. Updates `.agents/TASKS.md` with new tasks
4. Updates `BACKLOG.md` (marks feature `in-progress`)
5. Pushes all meta-files directly to `dev`

### Example Flow
```
/plan-feature F-025
→ [Reads F-025: "History Clear"]
→ [Creates 2 tasks: T-030, T-031]
→ [Pushes to dev]
→ "Ready for workers"
```

---

## For Workers (Apoc / Switch / Mouse)

### Your Job
- Pick a `ready` task from `.agents/TASKS.md`
- Read the task file exactly
- Build it, test it, open a PR, mark task `done`

### Workflow
Follow `.agents/workflows/feature-work.md` step-by-step:

```bash
# 1. Get the task
# Read .agents/TASKS.md, find a "ready" task, note the ID (e.g. T-025)

# 2. Read your scope
# cat .agents/tasks/T-025-description.md
# This tells you EXACTLY what to do

# 3. Create branch from dev
git fetch origin dev
git checkout -b claude/T-025-description origin/dev

# 4. Make changes (ONLY the files listed in the task)
# Edit app/src/...
# Edit app/build.gradle
# ... (task file tells you what)

# 5. Build & test
./gradlew assembleDebug    # MUST pass
./gradlew lint             # MUST pass

# 6. Commit
git add <changed files>
git commit -m "T-025: description of change"

# 7. Update changelog
# Edit CHANGELOG.md: add one line under [Unreleased]
git add CHANGELOG.md
git commit -m "docs: T-025 changelog"

# 8. Rebase onto latest dev
git fetch origin dev
git rebase origin/dev
# (resolve conflicts if any)

# 9. Push & create PR
git push -u origin claude/T-025-description
# Then create PR on GitHub (target dev, not main)

# 10. Mark task done (isolated meta push)
git fetch origin dev
git checkout -b meta-T-025-done origin/dev
# Edit .agents/TASKS.md: change status to `done`
git add .agents/TASKS.md
git commit -m "meta: T-025 done"
git push origin HEAD:dev
git checkout claude/T-025-description
git branch -d meta-T-025-done
```

### Key Rules
- ✅ Feature code goes in a PR
- ✅ CHANGELOG.md updated on your branch
- ✅ TASKS.md marked done AFTER PR is open
- ✅ Never push feature code directly to dev/main
- ❌ Don't skip rebase before push
- ❌ Don't skip the build check

---

## Protected Branches

```
main  ← NEVER touch (releases only)
dev   ← ONLY PRs or isolated meta-files
```

### What Happens if You Violate

```bash
git push origin HEAD:dev
# ❌ ERROR: Cannot push directly to 'dev'
# (pre-push hook blocks it)
```

### How to Recover

```bash
# If you're trying to update a meta-file:
git fetch origin dev
git checkout -b meta-update origin/dev
# Edit meta-files ONLY
git add BACKLOG.md .agents/TASKS.md
git commit -m "meta: update"
git push origin HEAD:dev  # This is allowed (HEAD != dev)
```

---

## Troubleshooting

### "My PR conflicts with another PR"
```bash
git fetch origin dev
git rebase origin/dev
# Fix conflicts
git add <resolved files>
git rebase --continue
git push -f origin claude/T-XXX-description
```

### "I need to update a meta-file while working on a feature"
**Do NOT mix meta-files with feature code in one push.**

```bash
# Option 1: Push meta-file separately
git fetch origin dev
git checkout -b meta-update origin/dev
# Edit .agents/TASKS.md
git commit -m "meta: update"
git push origin HEAD:dev
git checkout claude/T-XXX-description  # Return to feature branch

# Option 2: Wait until after PR
# Update meta-file AFTER you open the PR
```

### "The pre-push hook is blocking my push"
Check which branch you're on:
```bash
git branch
```

If you're on `dev` or `main` and need to push a meta-file:
```bash
# Create a clean checkout of origin/dev
git fetch origin dev
git checkout -b meta-update-TIMESTAMP origin/dev
# Make meta-file changes
git commit -m "meta: update"
git push origin HEAD:dev
```

### "I can't remember the task ID"
```bash
# View all tasks
cat .agents/TASKS.md

# View a specific task
cat .agents/tasks/T-025-history-clear.md
```

---

## Files You Need to Know

| File | Purpose | Update When |
|------|---------|------------|
| `BACKLOG.md` | Feature roadmap & status | Planner decomposes feature |
| `.agents/TASKS.md` | Atomic task status & blocking | Worker completes task, orchestrator unblocks |
| `.agents/tasks/T-*.md` | Task specification (worker's bible) | Planner creates new tasks |
| `CHANGELOG.md` | Release notes & work history | Worker completes feature |
| `PROJECT.md` | Architecture & component list | After major structural changes |

---

## Getting Help

- **"What should I work on?"** → Run `/morpheus`
- **"How do I build T-025?"** → Read `.agents/tasks/T-025-*.md`
- **"Why was my push blocked?"** → See **Protected Branches** above
- **"What's the full procedure?"** → Read `.agents/WORKFLOW_ENFORCEMENT.md`
- **"I'm stuck on branching/PRs"** → Read `.agents/workflows/feature-work.md`
