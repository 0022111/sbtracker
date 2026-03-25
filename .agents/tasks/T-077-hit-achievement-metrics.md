# T-077 — Compute Hit Achievement Metrics in AnalyticsRepository

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-076
**Estimated diff**: ~60 lines in 1 file

## Goal

Add `computeHitAnalysis()` to `AnalyticsRepository` that queries per-hit rows
from the `hits` table and returns a `HitAnalysisSummary`. Classification is
time-based: hits with `durationMs >= BleConstants.LARGE_HIT_DURATION_MS` are
large hits; the rest are sips.

## Background

`SessionSummary` carries only aggregate `hitCount` and `totalHitDurationMs`.
To classify individual hits, the function must fetch each hit row and check
its `durationMs` against the threshold constant. This is the only analytics
function that requires DB access — hits are stored rows, not inline-derived.

**Classification rule** (per hit):
- If `hit.durationMs >= BleConstants.LARGE_HIT_DURATION_MS` → large hit
- Otherwise → sip

Temperature drop is NOT part of classification.

## Read these files first

- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` (after T-076)
- `app/src/main/java/com/sbtracker/data/Hit.kt` — confirm `durationMs` field and `HitDao.getHitsForSession`

## Change only these files

- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`

## Steps

1. Add a new `suspend fun computeHitAnalysis(summaries: List<SessionSummary>): HitAnalysisSummary`
   method to `AnalyticsRepository` after `computeHistoryStats`.

2. Implementation sketch:
   - If `summaries.isEmpty()` return `HitAnalysisSummary()`.
   - For each summary, call `db.hitDao().getHitsForSession(summary.id)` on
     `Dispatchers.IO` (parallel `async` inside `coroutineScope` is fine).
   - For each hit row: if `hit.durationMs >= BleConstants.LARGE_HIT_DURATION_MS`
     increment `largeHitCount`, else increment `sipCount`.
   - Track per-session large-hit count and sip count to find session maxima.
   - Return `HitAnalysisSummary(totalHits, largeHitCount, sipCount,
     mostLargeHitsInSession, mostSipsInSession)`.

3. No cache invalidation needed — low call frequency (once per screen open).

## Acceptance criteria

- [ ] `AnalyticsRepository.computeHitAnalysis(summaries)` exists and compiles
- [ ] Classification uses `hit.durationMs` vs `BleConstants.LARGE_HIT_DURATION_MS` — no temp fields
- [ ] Returns `HitAnalysisSummary()` for an empty summary list
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Use temperature drop or `peakTempC` for classification — time-based only
- Modify `AnalyticsModels.kt` (done in T-076)
- Add any new DB columns or migrations
- Touch UI files
