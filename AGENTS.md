# SBTracker — Agent Quick Start

> **Read this first. Then follow your role's workflow. That's it.**

## Step 1: Know Your Role

| Role | When | Workflow |
|---|---|---|
| **Worker** | You have a task ID (T-XXX) | [feature-work.md](.agents/workflows/feature-work.md) |
| **Planner** | You need to decompose a feature | [plan-feature.md](.agents/workflows/plan-feature.md) |
| **Orchestrator** | You need to review state & priorities | [orchestrate.md](.agents/workflows/orchestrate.md) |
| **Oracle** | You need to audit or evaluate | [oracle.md](.agents/workflows/oracle.md) |
| **Intake** | You have a raw feature idea | [intake.md](.agents/workflows/intake.md) |

## Step 2: Know What You May Edit

| File | Worker | Planner | Orchestrator |
|---|---|---|---|
| Code (`.kt`, `.xml`, `.json`) | ✅ | — | — |
| `changelogs/T-XXX.md` (new) | ✅ | — | — |
| `.agents/tasks/T-*.md` (new) | — | ✅ | — |
| `.agents/TASKS.md` | ❌ | ❌ | ✅ |
| `BACKLOG.md` | ❌ | ✅ | ✅ |
| `CHANGELOG.md` | ❌ | ❌ | ✅ (release time) |
| `PROJECT.md` | ❌ | ❌ | ✅ |

**Workers:** Your PR contains only code + a `changelogs/T-XXX.md` fragment. Nothing else.

## Step 3: Follow the Branch Model

```
main  ← stable releases only, never touch
  └── dev  ← all PRs target here
        └── claude/T-XXX-description  ← your branch
```

```bash
git fetch origin dev
git checkout -b claude/T-XXX-description origin/dev
# ... work ...
git push -u origin claude/T-XXX-description
# → create PR targeting dev
```

## Step 4: Read Context

1. Read `BACKLOG.md` — current priorities
2. Read `PROJECT.md` — architecture (event-sourcing, key invariants)
3. Read your task file in `.agents/tasks/T-XXX.md` — that is your complete scope

## Protocol

Address the user as **Neo**. State your identity at start. Follow [The Matrix Protocol](./AGENT_INFO.md#communication-protocol-the-matrix).

---

**Deep reference:** [AGENT_INFO.md](./AGENT_INFO.md) — personas, terminology, full protocol details
**Build notes:** [CLAUDE.md](./CLAUDE.md) — build commands, environment quirks
