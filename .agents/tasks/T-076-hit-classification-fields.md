# T-076 — Hit Classification Fields in AnalyticsModels

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: nothing
**Estimated diff**: ~30 lines in 2 files

## Goal

Add a `HitAnalysisSummary` data class to `AnalyticsModels.kt` and a duration
threshold constant to `BleConstants.kt` as a framework skeleton for hit
achievements. Individual achievement definitions will be filled in later —
do not pre-populate fields beyond what is listed here.

## Background

A hit is stored in the `hits` table with a `durationMs` field. Classification
is **time-based only**: a **large hit** is a hit whose `durationMs` exceeds
`BleConstants.LARGE_HIT_DURATION_MS`. A **sip** is any hit below that threshold.
Temperature drop is NOT used for classification.

The goal of this task is just to establish the data model and constant so
downstream tasks (T-077 computation, T-080 display) have a stable contract.
Keep the model minimal — it is intentionally a framework to be extended.

## Read these files first

- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` — understand existing models
- `app/src/main/java/com/sbtracker/data/Hit.kt` — confirm `durationMs` field exists
- `app/src/main/java/com/sbtracker/BleConstants.kt` — existing threshold constants

## Change only these files

- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt`
- `app/src/main/java/com/sbtracker/BleConstants.kt`

## Steps

1. Add a constant to `BleConstants.kt`:
```kotlin
/** Minimum hit duration (ms) to classify a hit as a large hit / rip. Tune after real-device validation. */
const val LARGE_HIT_DURATION_MS = 3000L
```

2. After the `IntakeStats` data class in `AnalyticsModels.kt`, append:

```kotlin
/**
 * Per-session hit classification counts derived from the hits table.
 * Used to power the hit-achievement display on the Analytics tab.
 *
 * Classification is time-based: a hit is "large" if its durationMs
 * exceeds [BleConstants.LARGE_HIT_DURATION_MS].
 *
 * This is a framework skeleton — add achievement fields here as needed.
 */
data class HitAnalysisSummary(
    /** Total hits classified across all sessions in scope. */
    val totalHits: Int = 0,
    /** Hits whose duration exceeded LARGE_HIT_DURATION_MS. */
    val largeHitCount: Int = 0,
    /** Hits whose duration was below LARGE_HIT_DURATION_MS. */
    val sipCount: Int = 0,
    /** Highest large-hit count recorded in a single session. */
    val mostLargeHitsInSession: Int = 0,
    /** Highest sip count recorded in a single session. */
    val mostSipsInSession: Int = 0
)
```

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria

- [ ] `HitAnalysisSummary` data class exists in `AnalyticsModels.kt` with the 5 fields above
- [ ] `BleConstants.LARGE_HIT_DURATION_MS = 3000L` constant exists
- [ ] No temperature-drop fields or constants added
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Add any temperature-drop fields or constants — classification is time-based only
- Pre-populate achievement fields beyond the 5 listed — this is a framework skeleton
- Compute anything — that is T-077
- Modify any DAO or DB schema
- Touch UI files
