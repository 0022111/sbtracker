# T-086 — F-052 Hit Classification: Compute Thresholds from Alpha Data

**Phase**: Phase 3 — Post-Alpha Feature Development
**Blocked by**: B-010 (temp accuracy), F-054 (session report redesign), F-056 (analytics tab), Alpha release shipped
**Estimated diff**: ~100 lines across 2 files

## Goal
Implement the "80% version" of F-052: analyze real hit distribution data from Alpha users and compute data-driven thresholds for hit classification (small/medium/large), replacing the arbitrary 3-second guess with empirical reality.

## Context
This task is **intentionally parked** until re-entry conditions are met (see BACKLOG.md). The goal is to ship the feature with real user data driving the classification logic, not speculation.

## Prerequisites (ALL must be true before starting)
1. ✅ **B-010 resolved** — `peakTempC` is accurate
2. ✅ **F-054 complete** — Session report redesign gives stable display
3. ✅ **F-056 complete** — Analytics tab exists for hit aggregate cards
4. ✅ **Alpha shipped** — Real users have generated ≥500 hits across 20+ sessions

## Read these files first
- `app/src/main/java/com/sbtracker/data/Hit.kt` — Hit entity fields (durationMs, peakTempC, sessionId)
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — analytics aggregation patterns
- `BACKLOG.md` lines 123–146 — detailed F-052 spec and re-entry conditions

## Change only these files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` (add hit classification logic)
- `app/src/main/java/com/sbtracker/data/AnalyticsModels.kt` (add HitClassification enum + HitStats model)

## Steps

### 1. Extract Hit Duration Distribution
Create a one-time data extraction script (pseudo-code, run manually in debugger):

```kotlin
// Run once in MainViewModel.init or SettingsFragment
val hits = sessionDao.getAllHits()  // Get all hits from Alpha phase
val durations = hits.map { it.durationMs / 1000.0 }  // Convert to seconds
val sorted = durations.sorted()
println("Hit Duration Distribution (seconds):")
println("  Min: ${sorted.first()}")
println("  P25: ${sorted[sorted.size/4]}")
println("  P50 (median): ${sorted[sorted.size/2]}")
println("  P75: ${sorted[(sorted.size*3)/4]}")
println("  P90: ${sorted[(sorted.size*9)/10]}")
println("  Max: ${sorted.last()}")
println("  Mean: ${durations.average()}")
```

**Document the actual distribution in BACKLOG.md notes section after running.**

### 2. Define HitClassification Enum
In `AnalyticsModels.kt`:

```kotlin
enum class HitClassification {
    SMALL,    // < P33 (shortest hits)
    MEDIUM,   // P33–P66 (typical hits)
    LARGE;    // > P66 (longest hits)

    companion object {
        fun classify(durationSecs: Double, smallThreshold: Double, largeThreshold: Double): HitClassification {
            return when {
                durationSecs < smallThreshold -> SMALL
                durationSecs >= largeThreshold -> LARGE
                else -> MEDIUM
            }
        }
    }
}
```

### 3. Add Hit Classification to AnalyticsRepository
Add a new data class and computation function:

```kotlin
data class HitStats(
    val totalHits: Int,
    val smallCount: Int,
    val mediumCount: Int,
    val largeCount: Int,
    val smallPercentage: Float,
    val mediumPercentage: Float,
    val largePercentage: Float
)

fun computeHitStats(
    hits: List<Hit>,
    smallThresholdSecs: Double,    // Empirical P33
    largeThresholdSecs: Double     // Empirical P66
): HitStats {
    val classified = hits.map { hit ->
        HitClassification.classify(
            hit.durationMs / 1000.0,
            smallThresholdSecs,
            largeThresholdSecs
        )
    }

    val small = classified.count { it == HitClassification.SMALL }
    val medium = classified.count { it == HitClassification.MEDIUM }
    val large = classified.count { it == HitClassification.LARGE }
    val total = classified.size

    return HitStats(
        totalHits = total,
        smallCount = small,
        mediumCount = medium,
        largeCount = large,
        smallPercentage = (small.toFloat() / total * 100),
        mediumPercentage = (medium.toFloat() / total * 100),
        largePercentage = (large.toFloat() / total * 100)
    )
}
```

### 4. Add Per-Session Hit Badges
Implement a pure function to compute session-level hit classification badge:

```kotlin
fun getSessionHitBadge(hits: List<Hit>, smallThreshold: Double, largeThreshold: Double): String {
    val large = hits.count { (it.durationMs / 1000.0) >= largeThreshold }
    return when {
        large >= 5 -> "🔥 Heavy Hitter"
        large >= 2 -> "⚡ Intense"
        else -> "✓ Standard"
    }
}
```

### 5. Wire Hit Stats to Analytics Card
Update `HistoryViewModel` to expose hit stats:

```kotlin
val hitStats: StateFlow<HitStats?> =
    sessionSummaries.map { summaries ->
        val allHits = hitDao.getHitsForSessions(summaries.map { it.sessionId })
        computeHitStats(allHits, SMALL_THRESHOLD, LARGE_THRESHOLD)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
```

### 6. Document Thresholds as Constants
In `BleConstants.kt`:

```kotlin
object HitClassification {
    // IMPORTANT: These are empirically derived from Alpha user data (2026-04-XX)
    // Do NOT guess or modify without re-analyzing real hit distribution
    const val SMALL_THRESHOLD_SECS = 1.5   // P33 from Alpha data
    const val LARGE_THRESHOLD_SECS = 4.2   // P66 from Alpha data

    // Change log:
    // 2026-04-XX: Initial thresholds from 547 hits across 23 sessions
    //   - Min: 0.8s, P50: 2.1s, Max: 8.3s
    //   - Classification: <1.5s=small, 1.5-4.2s=medium, >4.2s=large
}
```

### 7. Add Unit Tests (minimal)
In test suite:

```kotlin
@Test
fun hitClassification_classifiesBasedOnThreshold() {
    assertEquals(HitClassification.SMALL, HitClassification.classify(1.0, 1.5, 4.2))
    assertEquals(HitClassification.MEDIUM, HitClassification.classify(3.0, 1.5, 4.2))
    assertEquals(HitClassification.LARGE, HitClassification.classify(5.0, 1.5, 4.2))
}

@Test
fun hitStats_computesDistribution() {
    val hits = listOf(
        Hit(durationMs = 1000),  // 1s = small
        Hit(durationMs = 2500),  // 2.5s = medium
        Hit(durationMs = 5000)   // 5s = large
    )
    val stats = computeHitStats(hits, 1.5, 4.2)
    assertEquals(1, stats.smallCount)
    assertEquals(1, stats.mediumCount)
    assertEquals(1, stats.largeCount)
}
```

## Acceptance criteria
- [ ] Real Alpha user hit data extracted and analyzed (document P25, P50, P75, actual distribution)
- [ ] Thresholds derived from empirical data (not guesses)
- [ ] `HitClassification` enum implemented with `classify()` function
- [ ] `computeHitStats()` returns distribution counts and percentages
- [ ] Hit stats wired to Analytics tab (card shows distribution + %)
- [ ] Session-level badges (e.g., "🔥 Heavy Hitter") computed correctly
- [ ] Thresholds documented in `BleConstants.kt` with changelog
- [ ] `./gradlew test` passes (includes hit classification tests)
- [ ] No hardcoded thresholds outside of BleConstants

## Do NOT
- Start this task before Alpha is shipped and real data exists
- Guess thresholds — extract actual distribution first
- Store classifications in database (compute at query time)
- Create achievements table yet (spec says "later decision")
- Modify existing hit table structure (use durationMs + peakTempC as-is)
