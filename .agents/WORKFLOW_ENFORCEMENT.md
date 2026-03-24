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

**4. Update CHANGELOG.md** (on your branch)
```bash
# Edit CHANGELOG.md: add one line under [Unreleased]
git add CHANGELOG.md
git commit -m "docs: T-XXX changelog"
```

**5. Rebase & push**
```bash
git fetch origin dev
git rebase origin/dev           # Ensure clean history
git push -u origin claude/T-XXX-description
```

**6. Create PR** (target `dev`, never `main`)
```bash
# Use mcp__github__create_pull_request:
# - owner: 0022111
# - repo: sbtracker
# - head: claude/T-XXX-description
# - base: dev          ← ALWAYS dev
# - title: T-XXX — Task Title
```

**7. Mark task done** (isolated meta push)
```bash
git fetch origin dev
git checkout -b meta-T-XXX-done origin/dev

# Edit .agents/TASKS.md: change status to `done`
git add .agents/TASKS.md
git commit -m "meta: T-XXX done"
git push origin HEAD:dev        # Push only TASKS.md to dev

git checkout claude/T-XXX-description
git branch -d meta-T-XXX-done
```

---

## Meta-files (Allowed for Direct Push to `dev`)

These files can be pushed directly to `dev` *after* their associated PR is open:

- `.agents/TASKS.md` (mark tasks `done` after PR merges)
- `CHANGELOG.md` (append entry on feature branch, then pushed via PR; only direct push if orchestrator syncing)
- `BACKLOG.md` (planner updates before decomposing into tasks; sync to dev directly)
- `.agents/tasks/T-*.md` (new task files; direct push after creation)
- `AGENT_INFO.md`, `CLAUDE.md` (agent instruction updates; direct push)

**Rule**: Push meta-files directly to `dev` *only* if:
1. They don't contain feature code
2. You've isolated the commit (it only modifies that meta-file)
3. You use `git push origin HEAD:dev` from a clean checkout of `origin/dev`

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
