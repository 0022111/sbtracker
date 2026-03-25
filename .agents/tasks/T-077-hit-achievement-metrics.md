# T-077 — Compute Hit Achievement Metrics in AnalyticsRepository

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-076
**Estimated diff**: ~80 lines in 2 files

## Goal

Add `computeHitAnalysis(summaries, db)` to `AnalyticsRepository` that queries
the per-hit rows from the `hits` table (via `HitDao.getHitsForSession`) and
returns a `HitAnalysisSummary`. This is the only analytics function that
requires DB access (hits are stored rows, not derived inline).

## Background

`SessionSummary` currently carries only `hitCount` and `totalHitDurationMs`
(aggregate stats from `HitStats`). Per-hit `peakTempC` is stored in the
`hits` table. To classify large hits vs sips, the function must fetch the
individual hit rows and compare each hit's `peakTempC` against the session's
baseline peak temp, using `BleConstants.LARGE_HIT_TEMP_DROP_C` (8°C).

**Classification rule** (per hit):
- `tempDrop = session.peakTempC - hit.peakTempC`
- If `tempDrop >= BleConstants.LARGE_HIT_TEMP_DROP_C` → large hit
- Otherwise → sip

## Read these files first

- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` (after T-076)
- `app/src/main/java/com/sbtracker/data/Hit.kt` — `HitDao.getHitsForSession`

## Change only these files

- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`

## Steps

1. Add a new `suspend fun computeHitAnalysis(summaries: List<SessionSummary>): HitAnalysisSummary`
   method to `AnalyticsRepository` after `computeHistoryStats`.

2. Implementation sketch:
   - If `summaries.isEmpty()` return `HitAnalysisSummary()`.
   - For each summary, call `db.hitDao().getHitsForSession(summary.id)` on
     `Dispatchers.IO` (can be parallel with `async` inside `coroutineScope`).
   - For each hit row, compute `tempDrop = summary.peakTempC - hit.peakTempC`.
   - Accumulate: `largeHitCount`, `sipCount`, per-session large-hit max,
     per-session sip max, running sum of `tempDrop` for average,
     running max `tempDrop`.
   - Return `HitAnalysisSummary(totalHits, largeHitCount, sipCount,
     mostLargeHitsInSession, mostSipsInSession, avgTempDropC, maxTempDropC)`.

3. No cache invalidation needed — this function re-queries the DB each call.
   The call frequency from the ViewModel is low (once per screen open).

## Acceptance criteria

- [ ] `AnalyticsRepository.computeHitAnalysis(summaries)` exists and compiles
- [ ] Returns `HitAnalysisSummary()` for an empty summary list
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Modify `AnalyticsModels.kt` (done in T-076)
- Add any new DB columns or migrations
- Touch UI files
