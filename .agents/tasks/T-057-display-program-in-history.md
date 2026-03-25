# T-057 — Display Applied Program Name in Session History and Session Report

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-056
**Estimated diff**: ~80 lines across 4 files

## Goal
Surface the applied program name (e.g., "Terpene Optimization") in two places:
1. The session history list — a small badge/label on each session row that had a program
2. `SessionReportActivity` — a labeled line in the session header section

## Why this matters
Without this task, F-027 is invisible to users in the history view. The whole point of
logging `appliedProgramId` (T-056) is to let users review which programs they used and
draw their own conclusions about effectiveness. This closes the loop on F-027.

## Read these files first
- `app/src/main/java/com/sbtracker/data/SessionSummary.kt` — current fields; add `appliedProgramName: String?`
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — where `SessionSummary` is assembled; this is where the program name lookup goes
- `app/src/main/java/com/sbtracker/SessionHistoryAdapter.kt` — RecyclerView adapter; add program badge to each row
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt` — detail view; add program name line

## Change only these files
- `app/src/main/java/com/sbtracker/data/SessionSummary.kt`
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`
- `app/src/main/java/com/sbtracker/SessionHistoryAdapter.kt`
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt`

## Steps

### 1. `SessionSummary.kt` — add `appliedProgramName`

```kotlin
data class SessionSummary(
    // ... existing fields unchanged ...
    val appliedProgramName: String? = null   // ← new, null if no program was used
)
```

### 2. `AnalyticsRepository.kt` — look up program name when building SessionSummary

`AnalyticsRepository` already fetches `session_metadata` rows via
`sessionMetadataDao.getMetadataForSessions(sessionIds)`. Extend this:

1. Collect all distinct non-null `appliedProgramId` values from the metadata results.
2. For each distinct ID, call `sessionProgramDao.getById(id)` and build a
   `Map<Long, String>` of `programId → programName`.
3. When assembling each `SessionSummary`, set:
   ```kotlin
   appliedProgramName = metadata?.appliedProgramId
       ?.let { programNameMap[it] }
   ```

`SessionProgramDao` needs to be injected into `AnalyticsRepository`. Add it as a
constructor parameter (Hilt already provides it via `AppModule` from T-042).

### 3. `SessionHistoryAdapter.kt` — program badge on session rows

In `onBindViewHolder` (or wherever session rows are built), check
`summary.appliedProgramName`:

- If non-null: render a small `TextView` badge with the program name, styled similarly to
  the existing capsule/free-pack badge if one exists, or use a distinct color
  (e.g., `color_blue` with 70% alpha background). Label format: `"▶ <name>"`.
- If null: hide the badge `View.GONE`.

Keep the badge programmatic (no new XML). Insert it into the existing row `LinearLayout`
below the session time/duration line.

### 4. `SessionReportActivity.kt` — program name in header

Find the section that builds the summary header (near where session start time, duration,
and hits are displayed). Add a row:

```
Program:   Terpene Optimization
```

Only display this row if `summary.appliedProgramName != null`. Style it consistently with
the other stat rows. Keep it near the top of the header (after session type / capsule line).

### 5. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `SessionSummary` has `appliedProgramName: String?` field
- [ ] `AnalyticsRepository` resolves program names from `SessionProgramDao` and populates the field
- [ ] Sessions with no program have `appliedProgramName == null` (no badge shown)
- [ ] Session history list rows show a program badge when `appliedProgramName` is non-null
- [ ] `SessionReportActivity` shows a "Program: ..." line when `appliedProgramName` is non-null
- [ ] Deleted programs: if `appliedProgramId` references a deleted program, the name resolves to null gracefully (the `getById` returns null → no badge)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Store `appliedProgramName` in the DB — always resolve at query time from `session_programs`
- Modify `AppDatabase`, `SessionMetadata`, or migration files
- Add program-based analytics aggregation here — that is a future analytics task (F-052 scope)
- Show program info for sessions that pre-date F-027 (they have null `appliedProgramId` — handle gracefully)
