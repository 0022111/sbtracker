# T-035 — IntakeStats: analytics model + computeIntakeStats

**Phase**: Phase 2 — F-018 Health & Dosage Tracking
**Blocked by**: nothing
**Estimated diff**: ~70 lines added across 2 files

## Goal
Add the `IntakeStats` data class and a pure `computeIntakeStats()` function to the analytics layer so the rest of F-018 can call it. No ViewModel wiring, no UI — pure data and computation only.

## Read these files first
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` — where all analytics data classes live; add `IntakeStats` here.
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — where all compute functions live (e.g. `computeHistoryStats`, `computeUsageInsights`); add `computeIntakeStats` here matching the same style.
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt` — the `SessionMetadata` entity you will receive as input.
- `app/src/main/java/com/sbtracker/data/SessionSummary.kt` — the `SessionSummary` you will receive as input.

## Change only these files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt`
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`

## Steps

### 1 — Add `IntakeStats` to `AnalyticsModels.kt`

Append at the end of the file:

```kotlin
/**
 * Dosage and intake analytics derived from session metadata.
 * Uses a global default capsule weight; per-session overrides are respected
 * when [SessionMetadata.capsuleWeightGrams] is non-zero.
 */
data class IntakeStats(
    /** Total grams consumed across all capsule sessions (all time). */
    val totalGramsAllTime: Float = 0f,
    /** Total grams consumed in the last 7 days. */
    val totalGramsThisWeek: Float = 0f,
    /** Total grams consumed in the last 30 days. */
    val totalGramsThisMonth: Float = 0f,
    /** Number of sessions marked as capsule type. */
    val capsuleSessionCount: Int = 0,
    /** Number of sessions marked as free-pack type. */
    val freePackSessionCount: Int = 0,
    /** Average grams per capsule session (0 if no capsule sessions). */
    val avgGramsPerSession: Float = 0f,
    /** Grams per day averaged over the last 7 days. */
    val gramsPerDay7d: Float = 0f,
    /** Grams per day averaged over the last 30 days. */
    val gramsPerDay30d: Float = 0f
)
```

### 2 — Add `computeIntakeStats` to `AnalyticsRepository`

Add this function to the `AnalyticsRepository` class (after the last existing `compute*` function):

```kotlin
/**
 * Compute intake statistics from session summaries and their metadata.
 *
 * @param summaries All session summaries for the active device (or filtered set).
 * @param metadataMap Map of sessionId → SessionMetadata for those sessions.
 * @param defaultWeightGrams Global fallback capsule weight when per-session weight is 0.
 */
fun computeIntakeStats(
    summaries: List<SessionSummary>,
    metadataMap: Map<Long, SessionMetadata>,
    defaultWeightGrams: Float
): IntakeStats {
    if (summaries.isEmpty()) return IntakeStats()

    val nowMs = System.currentTimeMillis()
    val weekAgoMs  = nowMs - 7L  * 24 * 60 * 60 * 1000
    val monthAgoMs = nowMs - 30L * 24 * 60 * 60 * 1000

    var totalAll   = 0f
    var totalWeek  = 0f
    var totalMonth = 0f
    var capsuleCount   = 0
    var freePackCount  = 0

    for (summary in summaries) {
        val meta = metadataMap[summary.id]
        val isCapsule = meta?.isCapsule ?: false

        if (isCapsule) {
            val weight = meta?.capsuleWeightGrams?.takeIf { it > 0f } ?: defaultWeightGrams
            capsuleCount++
            totalAll += weight
            if (summary.startTimeMs >= weekAgoMs)  totalWeek  += weight
            if (summary.startTimeMs >= monthAgoMs) totalMonth += weight
        } else {
            freePackCount++
        }
    }

    return IntakeStats(
        totalGramsAllTime      = totalAll,
        totalGramsThisWeek     = totalWeek,
        totalGramsThisMonth    = totalMonth,
        capsuleSessionCount    = capsuleCount,
        freePackSessionCount   = freePackCount,
        avgGramsPerSession     = if (capsuleCount > 0) totalAll / capsuleCount else 0f,
        gramsPerDay7d          = totalWeek  / 7f,
        gramsPerDay30d         = totalMonth / 30f
    )
}
```

### 3 — Add the required import to `AnalyticsRepository.kt`

Add at the top with the other imports:
```kotlin
import com.sbtracker.data.SessionMetadata
```

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `IntakeStats` data class exists in `AnalyticsModels.kt` with all 8 fields
- [ ] `computeIntakeStats(summaries, metadataMap, defaultWeightGrams)` exists in `AnalyticsRepository`
- [ ] Capsule sessions with zero per-session weight fall back to `defaultWeightGrams`
- [ ] Free-pack sessions contribute to `freePackSessionCount` but add 0 grams
- [ ] Week/month filtering uses session `startTimeMs`
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Do not add a StateFlow or any ViewModel code — that is T-038.
- Do not add UI of any kind.
- Do not touch `MainViewModel.kt`, `SessionMetadata.kt`, or any layout XML.
- Do not modify existing `compute*` functions.
