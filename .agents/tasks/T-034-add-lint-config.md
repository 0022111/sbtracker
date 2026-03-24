# T-034 — Add Lint Configuration & CI Gate

**Status**: ready
**Phase**: 1
**Blocked by**: —
**Blocks**: —

---

## Goal
No lint configuration exists. CI runs `./gradlew lint` but there's no `lint.xml`
to control which rules are errors vs warnings, and no baseline to track progress.
Add a lint config that enforces critical checks and establishes a baseline.

---

## Read first
- `app/build.gradle` (lintOptions section, if any)
- `.github/workflows/build.yml`

## Change only these files
- `app/lint.xml` (create)
- `app/build.gradle` (add lintOptions block)

## Steps
1. Create `app/lint.xml` with:
   - **Error**: `HardcodedText`, `MissingTranslation`, `UnusedResources`, `Deprecated`, `NewApi`
   - **Warning**: `ObsoleteSdkInt`, `TypographyDashes`, `TypographyQuotes`
   - **Ignore** (for now): `AllowBackup`, `GoogleAppIndexingWarning`
2. In `app/build.gradle`, add:
   ```groovy
   android {
       lint {
           xmlReport true
           htmlReport true
           warningsAsErrors false
           abortOnError false  // don't break build yet; tighten later
           lintConfig file("lint.xml")
           baseline file("lint-baseline.xml")
       }
   }
   ```
3. Run `./gradlew lint` to generate `lint-baseline.xml` (captures existing violations).
4. Commit the baseline so future PRs only flag NEW violations.
5. `./gradlew assembleDebug` — must pass.

## Do NOT touch
- Any source files
- CI workflow (lint already runs there)
- Database schema
