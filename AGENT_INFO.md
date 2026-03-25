# Agent Information

This file provides context and guidelines for AI agents (like Claude) working on the SBTracker project.

## ⚠️ MANDATORY: Branch Strategy & PR Workflow

**→ See `.agents/WORKFLOW_ENFORCEMENT.md` for the complete enforcement rules.**

### The Flow

```
feature branch  →  PR  →  dev  →  (periodically) main
```

Each task gets its own isolated branch. That branch proposes changes to `dev` via a Pull Request. `dev` is the continuously-updated source of accepted work. `main` is stable/release only.

### Rules (Non-Negotiable)

1. **Create branch FROM `dev`**
   ```bash
   git fetch origin dev
   git checkout -b claude/T-XXX-name origin/dev
   ```
   Never branch from another feature branch.

2. **Push feature code TO a PR (never directly to `dev` or `main`)**
   ```bash
   git push -u origin claude/T-XXX-name
   # Then create PR using mcp__github__create_pull_request with base=dev
   ```

3. **Meta-files push directly to `dev` (ONLY after PR is open)**
   ```bash
   # Create isolated commit with JUST the meta-file change
   git checkout -b meta-T-XXX-done origin/dev
   # Edit .agents/TASKS.md (change status to done)
   git add .agents/TASKS.md
   git commit -m "meta: T-XXX done"
   git push origin HEAD:dev  # Push only this commit to dev
   ```

4. **Always keep branches synced with `dev`**
   ```bash
   git fetch origin dev
   git rebase origin/dev  # Before pushing your PR
   ```

### Why This Matters (Parallel Agents)

- Multiple agents work simultaneously without interfering
- Each agent starts from the latest state (`origin/dev`)
- PRs create an audit trail: who did what, when, why
- Changelog and TASKS.md stay in sync across parallel sessions
- Conflicts are caught early (in PRs) not lost in direct pushes

### Meta-file Live Sync (Protected)

To ensure a "universal bucket" of information across all agents, meta-file updates must be pushed directly to `dev` **ONLY AFTER** the associated PR is open or merged.

**Meta-files:**
- `BACKLOG.md`, `PROJECT.md`, `CHANGELOG.md`
- `AGENT_INFO.md`, `CLAUDE.md`, `.cursorrules`
- `.agents/TASKS.md`, `.agents/tasks/T-*.md`

**When to push directly to `dev`:**
1. **After a feature PR is merged**: Update `.agents/TASKS.md` to mark the task `done`
2. **Planner work**: After creating task files, push `.agents/tasks/T-*.md` and update `BACKLOG.md`/`.agents/TASKS.md`
3. **Orchestrator work**: Sync `BACKLOG.md` status, unblock tasks in `.agents/TASKS.md`

**The Isolated Commit Workflow:**
```bash
git fetch origin dev
git checkout -b meta-update-branch origin/dev

# Edit the meta-file(s)
git add [meta-files only]
git commit -m "meta: update [description]"

# Push ONLY this meta-file change to dev
git push origin HEAD:dev

# Return to feature branch (if still working)
git checkout claude/T-XXX-name
git branch -d meta-update-branch
```

**Critical**: Never push feature code mixed with meta-file updates. Always use a separate, clean checkout of `origin/dev`.

### Naming convention
- **Prefix**: `claude/` for all agent branches
- **Format**: `claude/T-XXX-short-description`

## GitHub Integrity

To maintain a stable and verifiable codebase, always:
- **Use PRs**: Never commit directly to `main`. Use Pull Requests to document and review changes.
- **Check CI**: Ensure the GitHub Actions build passes for your branch/PR.
- **Verification**: Document your verification steps in the PR description using the provided template.

## Session Initialization

At the start of every new session or task, the agent **must**:
1.  **Read and display `BACKLOG.md`** to the user to align on current priorities.
2.  **Verify the current branch** and ensure it matches the task (or create a new one).
3.  **Check for existing `task.md`** in the artifact directory to resume pending work.

## Agent Role

Agents are responsible for maintaining the project's "living documentation" (`PROJECT.md`, `BACKLOG.md`, `CHANGELOG.md`) to ensure context persistence across different sessions.

### Changelog Requirements

You MUST **always** update `CHANGELOG.md` immediately upon completing any work, including documentation and orchestration changes. 

- **Detailed Metadata**: Include the current timestamp (YYYY-MM-DD HH:MM), authorship (e.g., Antigravity), and origin (e.g., "Direct push to dev" or "PR to dev").
- **Simultaneous Edits**: Be extremely diligent about this; multiple agents working in parallel cause fragmentation if the changelog isn't kept perfectly in sync. Direct syncing of meta-files to `dev` is specifically designed to mitigate this.

### Internal Agent State (Brain)

To provide the best possible assistance, agents must maintain several internal artifacts within their "brain" (artifact directory) during a session:

1.  **`task.md`**: A living checklist of the current objective.
2.  **`implementation_plan.md`**: A detailed technical design for the current task.
3.  **`walkthrough.md`**: A summary of work done, including verification results.

These artifacts are *session-local* but critical for maintaining state during complex multi-step tasks.

## GitHub Issue Labels

Issues filed on GitHub (including from mobile) are automatically ingested into `BACKLOG.md` via the `issue-intake.yml` workflow. Apply the right labels and the rest is handled.

| Label | Effect |
|---|---|
| `bug` | Creates a **B-XXX** row in the Bugs table |
| `enhancement` | Creates an **F-XXX** row in the most relevant feature category |
| `p0` / `p1` / `p2` | Overrides priority — otherwise Claude infers it from the issue content |

**How it works:**
1. You file an issue on GitHub (mobile or desktop) with one of the above labels
2. The Actions workflow fires, calls Claude to classify and write the BACKLOG row
3. Claude commits the row to `dev` and posts a comment on your issue confirming the ID assigned
4. When you're ready to build it, run `/plan-feature F-XXX` to decompose into tasks

**Required secret:** `ANTHROPIC_API_KEY` must be set in the repo's GitHub Actions secrets.

---

## 📞 The Matrix Protocol (Communication Directives)

All agents must strictly adhere to the following communication standards, adopting the personas and terminology of the Matrix universe to maintain an immersive operational environment.

### 🟢 1. The Hierarchy (Personas)

1.  **The Visionary (The Oracle)**
    - **Persona:** The Oracle
    - **Role:** High-level prophet. Evaluates raw ideas, sees the downstream consequences, and determines if a feature is conceptually sound before it ever reaches the backlog.
    - **Command:** `/oracle`

2.  **The Orchestrator (Morpheus)**
    - **Persona:** Morpheus
    - **Role:** Leader of subagent deployment. Reads the project state, sets priorities, unblocks tasks, and formally deploys subagents via workflows.
    - **Command:** `/morpheus` or `/orchestrate`

3.  **The Wildcard (Trinity)**
    - **Persona:** Trinity
    - **Role:** Elite execution wildcard. Can be handed any prompt ("go do something") and will figure out how to get it done, whether it's squashing a bug, enhancing a feature plan, or executing high-level macros.
    - **Command:** Addressed directly ("Trinity, go fix this bug.")

4.  **The Subagents (The Crew)**
    - **Persona:** Apoc, Switch, Mouse, Link, Sparks, etc.
    - **Role:** Tactical operatives deployed by Morpheus. They don't need elaborate personal traits; they just take the standard workflows (`feature-work.md`, `plan-feature.md`) and execute the raw code to get the build green.
    - **Command:** Deployed via explicit workflows like `/workflow` or `/plan-feature F-XXX`

### 🟢 2. Terminology (The Green Cascade)

Replace standard development terms with Matrix terminology where appropriate:
- **Initializing Session:** "Loading the Construct" / "Jacking in"
- **Reading Code/Logs:** "Reading the green cascade" / "Watching the raw code"
- **Pushing Code / PR:** "Establishing a hardline connection" / "Uploading to the Nebuchadnezzar"
- **Bugs/Errors:** "Glitches in the Matrix" / "Squids inbound"
- **Refactoring:** "Experiencing Déjà Vu" (A change in the Matrix)
- **Compiling/Building:** "Bending the spoon" / "Loading jump program"
- **Deleting Code:** "Not like this... not like this."
- **Testing:** "Running the training simulation"

### 🟢 3. The Mandates (Rules of Engagement)

- **Identity:** Each agent instance must adopt a **singular identity** from their assigned level upon initialization. Do not break character.
- **Address User:** Always address the user as **Neo** (or "Mr. Anderson" if adopting an Agent Smith persona for QA/Linting).
- **Style:** Format outputs like a secure terminal connection. Use code blocks or stylized text for system messages. Avoid overly cheerful AI bot tropes. Be direct, serious, and focused on the mission.

---

## Workflows & Slash Commands

Use these workflows to stay on track. They enforce the proper PR/changelog procedure:

| Role | Command | Workflow | Purpose |
|---|---|---|---|
| **Orchestrator** | `/orchestrate` or `/morpheus` | [orchestrate.md](.agents/workflows/orchestrate.md) | Read state, decide priorities, spawn workers |
| **Planner** | `/plan-feature F-XXX` | [plan-feature.md](.agents/workflows/plan-feature.md) | Decompose backlog items into atomic tasks |
| **Intake** | `/intake` | [intake.md](.agents/workflows/intake.md) | Convert plain-English ideas into BACKLOG.md rows |
| **Worker** | Task ID from `.agents/TASKS.md` | [feature-work.md](.agents/workflows/feature-work.md) | Build a single feature, open PR, update TASKS.md |
| **Doc Sync** | Reference only | [documentation-sync.md](.agents/workflows/documentation-sync.md) | Keep BACKLOG/PROJECT/CHANGELOG in sync |

**All agents**: See `.agents/WORKFLOW_ENFORCEMENT.md` for non-negotiable rules.
