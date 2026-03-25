# T-076 — Hit Classification Fields in AnalyticsModels

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: nothing
**Estimated diff**: ~40 lines in 1 file

## Goal

Add a `HitAnalysisSummary` data class to `AnalyticsModels.kt` that carries
per-session hit classification counts (large hits vs sips) and temperature-drop
severity. This model is consumed by T-077 (computation) and T-080 (display).

## Background

A hit is currently stored in the `hits` table with `peakTempC` and `durationMs`.
The existing `HitDetector` fires on a temperature dip of ≥ `BleConstants.TEMP_DIP_THRESHOLD_C` (2°C).
A **large hit** (rip) is a hit whose temperature drop at peak exceeds a larger
threshold (proposed: ≥ 8°C). A **sip** is any hit below that threshold.
Duration is a secondary classifier: long dips indicate sustained draws.

## Read these files first

- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` — understand existing models
- `app/src/main/java/com/sbtracker/data/Hit.kt` — available fields per hit row
- `app/src/main/java/com/sbtracker/BleConstants.kt` — existing threshold constants

## Change only these files

- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt`

## Steps

1. After the `IntakeStats` data class, append a new `HitAnalysisSummary` data class:

```kotlin
/**
 * Per-session hit classification counts derived from the hits table.
 * Used to power the hit-achievement display on the Analytics tab.
 *
 * A "large hit" (rip) is defined as a hit whose temperature drop
 * exceeded [BleConstants.LARGE_HIT_TEMP_DROP_C] during the inhale window.
 * A "sip" is any hit that did not meet that threshold.
 */
data class HitAnalysisSummary(
    /** Total hits across all classified sessions. */
    val totalHits: Int = 0,
    /** Hits classified as large (rip-level temperature drop). */
    val largeHitCount: Int = 0,
    /** Hits classified as sips (smaller temperature drop). */
    val sipCount: Int = 0,
    /** Session with the single highest large-hit count. */
    val mostLargeHitsInSession: Int = 0,
    /** Session with the single highest sip count. */
    val mostSipsInSession: Int = 0,
    /** Average temperature drop (°C) across all classified hits. */
    val avgTempDropC: Float = 0f,
    /** Peak temperature drop recorded in a single hit (°C). */
    val maxTempDropC: Int = 0
)
```

2. Add a companion constant to `BleConstants.kt`:
```kotlin
/** Minimum temperature drop (°C) for a hit to be classified as a large hit / rip. */
const val LARGE_HIT_TEMP_DROP_C = 8
```

## Acceptance criteria

- [ ] `HitAnalysisSummary` data class exists in `AnalyticsModels.kt`
- [ ] `BleConstants.LARGE_HIT_TEMP_DROP_C = 8` constant exists
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Compute anything — that is T-077
- Modify any DAO or DB schema
- Touch UI files
