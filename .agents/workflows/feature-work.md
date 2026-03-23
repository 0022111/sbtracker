---
description: standard workflow for feature development and bug fixes
---

1. Create a new branch with the `claude/` prefix.
   - Example: `git checkout -b claude/F-042-unit-tests`
2. Initialize the internal agent task.
   - Create/update `task.md` in the artifact directory.
3. Understand the requirements and propose an implementation plan.
   - Create `implementation_plan.md` in the artifact directory.
   - Request review from the user via `notify_user`.
4. Once approved, execute the plan.
   - Follow the `implementation_plan.md` precisely.
   - Update `task.md` as progress is made.
5. Verify the changes.
   - Run relevant build and test commands (e.g., `./gradlew assembleDebug`).
   - Create `walkthrough.md` with proof of work.
6. Sync documentation.
   - Run the `/documentation-sync` workflow.
7. Notify the user of completion.
   - Use `notify_user` with a brief summary and links to relevant artifacts (especially `walkthrough.md`).
