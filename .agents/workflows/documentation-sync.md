---
description: ensures core project documentation stays in sync
---

1. Update `BACKLOG.md`.
   - Mark completed items as `done`.
   - Ensure the `Notes & Decisions Log` reflects any major architectural decisions.
2. Update `CHANGELOG.md`.
   - Append a summary of changes to the top of the relevant section.
   - Use standardized headers with dates.
3. Update `PROJECT.md`.
   - If new files were added, ensure they are listed in the relevant component table.
   - If the architecture changed, update the `Architecture` section.
4. **Verify and Sync**.
   // turbo
   - Run `./gradlew lintDebug` to check for any new issues (if applicable).
   - Run `./gradlew assembleDebug` to ensure the project still builds.
   - Commit and push to `dev`: `git add BACKLOG.md PROJECT.md CHANGELOG.md && git commit -m "docs: sync project documentation" && git fetch origin dev && git push origin HEAD:dev`
