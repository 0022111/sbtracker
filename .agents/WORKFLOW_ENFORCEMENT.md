# ⚠️ WORKFLOW ENFORCEMENT — Mandatory Agent Protocol

**This document enforces the PR/changelog procedure for all agent work.**

---

## The Rule: NO Direct Pushes to `dev` or `main` (Except Meta-files)

```
❌ FORBIDDEN:
  git push origin HEAD:dev           # NEVER push feature code directly
  git push origin HEAD:main          # NEVER push to main

✅ ALLOWED:
  git push origin claude/T-XXX-name  # Push to feature branch
  git push origin HEAD:dev           # ONLY for isolated meta-file updates
```

---

## Why This Matters

**Parallel Agent Problem**: Multiple agents work on different tasks simultaneously. Direct pushes to `dev` cause:

1. **Lost Context**: The `CHANGELOG.md` and `.agents/TASKS.md` become fragmented when agents push without PRs
2. **No Review Trail**: No way to audit what changed and why
3. **Merge Chaos**: When two agents push conflicting changes, there's no PR to detect the conflict early
4. **Changelog Gaps**: Future agents can't understand what work was done

**Solution**: Every feature change goes through a PR. Only meta-file updates (after the PR is open) bypass PRs.

---

## The Procedure (Must Follow Exactly)

### For Feature Code Changes

**1. Branch from `dev`**
```bash
git fetch origin dev
git checkout -b claude/T-XXX-description origin/dev
```

**2. Make changes** (follow the task file exactly)

**3. Build & commit**
```bash
./gradlew assembleDebug        # MUST pass
git add <changed files>
git commit -m "T-XXX: description"
```

**6. Drop a changelog fragment**
```bash
# Create changelogs/T-XXX.md with a brief description
git add changelogs/T-XXX.md
git commit -m "docs: T-XXX changelog fragment"
```

**7. Rebase & push**
```bash
git fetch origin dev
git rebase origin/dev           # Ensure clean history
git push -u origin claude/T-XXX-description
```

**8. Create PR** (target `dev`, never `main`)
```bash
# Use mcp__github__create_pull_request:
# - owner: 0022111
# - repo: sbtracker
# - head: claude/T-XXX-description
# - base: dev          ← ALWAYS dev
# - title: T-XXX — Task Title
```

**Done.** The Orchestrator handles TASKS.md, BACKLOG.md, and CHANGELOG.md.

---

## Meta-files (Restricted — Orchestrator/Planner Only)

These files are the #1 source of merge conflicts. **Workers must never edit them.**

| File | Who May Edit | When |
|---|---|---|
| `.agents/TASKS.md` | Orchestrator | After PR merges |
| `CHANGELOG.md` | Orchestrator | At release time (merge `changelogs/` fragments) |
| `BACKLOG.md` | Orchestrator / Planner | Status sync, new items |
| `.agents/tasks/T-*.md` | Planner | Task creation |
| `AGENT_INFO.md`, `CLAUDE.md` | User / Orchestrator | Rule changes |

**Workers** create `changelogs/T-XXX.md` on their feature branch. Unique filename = zero conflicts.

**Orchestrator/Planner** may push meta-file edits via their own PR or a direct push to `dev` — but only meta-files, never feature code.

---

## Detecting Violations

### Anti-pattern: Direct Feature Pushes

```bash
# ❌ WRONG:
git checkout dev
git merge claude/T-XXX-description
git push origin dev
```

**Detection**: Run `git log origin/dev --oneline` — if you see commits that don't have associated PRs, someone pushed directly.

### Anti-pattern: No Changelog Entry

```bash
# ❌ WRONG:
# ... push PR without updating CHANGELOG.md
```

**Detection**: Check the PR — if it has code changes but the CHANGELOG.md wasn't touched, flag it.

### Anti-pattern: Missing TASKS.md Update

```bash
# ❌ WRONG:
# ... merge PR but don't update .agents/TASKS.md to `done`
```

**Detection**: Run `/orchestrate` — it will show tasks that have merged PRs but aren't marked `done`.

---

## Enforcement Checklist

Before pushing anything, ask yourself:

- [ ] Am I on a `claude/T-XXX-*` feature branch?
- [ ] Have I run `./gradlew assembleDebug` and it passed?
- [ ] Did I commit my changes with a task-scoped message (T-XXX)?
- [ ] Did I update `CHANGELOG.md` on the feature branch?
- [ ] Did I rebase onto `origin/dev` to ensure clean history?
- [ ] Is my PR targeting `dev` (not `main`)?
- [ ] After the PR is open, did I make an isolated TASKS.md update?
- [ ] Did I avoid pushing feature code directly to `dev` or `main`?

**If you answered no to any question, stop and fix it before pushing.**

---

## Git Hooks (Optional Automation)

To prevent accidental direct pushes to `dev`, you can set up a pre-push hook:

```bash
# Create .git/hooks/pre-push
#!/bin/bash
protected_branch='dev|main'
current_branch=$(git rev-parse --abbrev-ref HEAD)

if [[ $current_branch =~ ^($protected_branch)$ ]]; then
  echo "⚠️  Pushing to $current_branch is forbidden."
  echo "Please create a feature branch (claude/T-XXX-*) and open a PR instead."
  exit 1
fi
exit 0

# Make it executable
chmod +x .git/hooks/pre-push
```

---

## Available Workflows (Skills/Slash Commands)

### For Orchestrators
- `/orchestrate` — Read project state, decide what to do next, spawn tasks
- `/morpheus` — Alias for orchestrator role (Morpheus persona)

### For Planners
- `/plan-feature F-XXX` — Decompose a backlog feature into atomic tasks
- `/intake` — Intake new feature ideas into BACKLOG.md

### For Workers
- `/workflow` or check `.agents/workflows/feature-work.md` — Standard feature development procedure

### Documentation Sync
- Check `.agents/workflows/documentation-sync.md` — Keep BACKLOG/PROJECT/CHANGELOG in sync

---

## Questions?

If an agent is unsure:
1. Read `.agents/workflows/feature-work.md` (worker checklist)
2. Read `.agents/workflows/orchestrate.md` (orchestrator checklist)
3. Ask the user before pushing

**Remember**: A PR is not just code review—it's the official record of what was done and why.
