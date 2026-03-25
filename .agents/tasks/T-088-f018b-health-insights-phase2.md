# T-088 — F-018b Health & Dosage Phase 2: Advanced Insights & Trends

**Phase**: Phase 3 — Health & Dosage Complete
**Blocked by**: T-034–T-038 (F-018a Phase 1 complete)
**Estimated diff**: ~200 lines across 4 files

## Goal
Complete F-018b Phase 2: implement advanced health insights including grams/week trends, habit analysis, and dosage history visualization. Phase 1 (T-034–T-038) established the foundation; Phase 2 builds analytics on top.

## Context
Phase 1 delivered:
- ✅ Capsule weight + default session type in settings
- ✅ Session capsule/free-pack toggle in session report
- ✅ Basic Health & Intake card showing total weight + usage

Phase 2 adds:
- Grams per week trend chart (timeline)
- Habit analysis (frequency, time-of-day patterns)
- Dosage efficiency metrics (grams per session, grams per day)
- Predictive insights (e.g., "Your average intake is X g/week; maintain trend or change it")

## Read these files first
- `BACKLOG.md` lines 205–235 — complete F-018b spec with 6-step implementation plan
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — existing analytics patterns
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt` — capsule_weight, default_session_type fields
- `app/src/main/java/com/sbtracker/ui/history/HistoryViewModel.kt` — where health insights are exposed

## Change only these files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` (add Phase 2 analytics)
- `app/src/main/java/com/sbtracker/data/AnalyticsModels.kt` (add HealthInsights model)
- `app/src/main/java/com/sbtracker/ui/history/HistoryViewModel.kt` (expose health metrics)
- `app/src/test/java/com/sbtracker/analytics/AnalyticsRepositoryTest.kt` (add health tests)

## Steps

### 1. Extend HealthInsights Data Model
In `AnalyticsModels.kt`, add advanced metrics to the `HealthInsights` model:

```kotlin
data class HealthInsights(
    // Phase 1 (existing)
    val totalCapsuleWeight: Float,  // grams
    val capsuleCount: Int,
    val averageSessionType: String,  // "Mixed", "Capsule", "Free Pack"

    // Phase 2 (new)
    val gramsPerWeek: Float,         // Average weekly intake
    val gramsPerDay: Float,          // Average daily intake
    val dailyFrequency: Float,       // Sessions per day (all types)
    val capsuleFrequency: Float,     // Capsule sessions per day
    val mostCommonTimeOfDay: String, // "Morning", "Afternoon", "Evening"
    val weightTrend: List<DailyIntakeStat>,  // Last 12 weeks
    val efficiencyRating: String     // "Efficient", "Moderate", "High Volume"
)

data class DailyIntakeStat(
    val dateMs: Long,
    val weekStart: String,  // "2026-03-23–03-29"
    val gramsIntaken: Float,
    val sessionsCount: Int,
    val averageGramsPerSession: Float
)
```

### 2. Implement Grams Per Week Calculation
In `AnalyticsRepository.kt`:

```kotlin
fun computeHealthInsightsPhase2(
    summaries: List<SessionSummary>,
    capsuleWeight: Float,  // grams per capsule
    defaultSessionType: String,
    sessionMetadata: Map<Long, SessionMetadata>,  // sessionId → metadata
    dayStartHour: Int
): HealthInsights {
    // Identify capsule sessions
    val capsuleSessions = summaries.filter { summary ->
        sessionMetadata[summary.sessionId]?.isCapsule ?:
            defaultSessionType == "Capsule"
    }

    // Phase 1 metrics
    val totalWeight = capsuleSessions.size * capsuleWeight
    val capsuleCount = capsuleSessions.size

    // Phase 2: Compute weekly intake trend (last 12 weeks)
    val weeklyStats = mutableListOf<DailyIntakeStat>()
    val now = System.currentTimeMillis()
    val twelveWeeksMs = 12 * 7 * 24 * 60 * 60 * 1000L

    for (weekOffset in 0..11) {
        val weekStartMs = now - (weekOffset * 7 * 24 * 60 * 60 * 1000L)
        val weekEndMs = weekStartMs + (7 * 24 * 60 * 60 * 1000L)

        val weekSessions = capsuleSessions.filter {
            it.startTimeMs in weekStartMs..weekEndMs
        }

        val weekGrams = weekSessions.size * capsuleWeight
        val avgGramsPerSession = if (weekSessions.isNotEmpty())
            weekGrams / weekSessions.size else 0f

        weeklyStats.add(DailyIntakeStat(
            dateMs = weekStartMs,
            weekStart = formatWeekRange(weekStartMs),
            gramsIntaken = weekGrams,
            sessionsCount = weekSessions.size,
            averageGramsPerSession = avgGramsPerSession
        ))
    }

    // Calculate rolling averages
    val gramsPerWeek = weeklyStats.take(4).map { it.gramsIntaken }.average().toFloat()
    val gramsPerDay = gramsPerWeek / 7f

    // Compute daily frequency (all sessions)
    val dailyFrequency = computeDailyFrequency(summaries)
    val capsuleFrequency = computeDailyFrequency(capsuleSessions)

    // Find most common time of day
    val mostCommonTimeOfDay = computeMostCommonTimeOfDay(capsuleSessions)

    // Efficiency rating
    val efficiencyRating = when {
        gramsPerWeek > 50f -> "High Volume"
        gramsPerWeek > 20f -> "Moderate"
        else -> "Efficient"
    }

    return HealthInsights(
        totalCapsuleWeight = totalWeight,
        capsuleCount = capsuleCount,
        averageSessionType = if (capsuleCount == summaries.size) "Capsule" else "Mixed",
        gramsPerWeek = gramsPerWeek,
        gramsPerDay = gramsPerDay,
        dailyFrequency = dailyFrequency,
        capsuleFrequency = capsuleFrequency,
        mostCommonTimeOfDay = mostCommonTimeOfDay,
        weightTrend = weeklyStats,
        efficiencyRating = efficiencyRating
    )
}

private fun computeDailyFrequency(sessions: List<SessionSummary>): Float {
    if (sessions.isEmpty()) return 0f

    val firstDate = sessions.minOf { it.startTimeMs }
    val lastDate = sessions.maxOf { it.startTimeMs }
    val daysDiff = (lastDate - firstDate) / (24 * 60 * 60 * 1000L)

    return sessions.size.toFloat() / maxOf(daysDiff, 1L)
}

private fun computeMostCommonTimeOfDay(sessions: List<SessionSummary>): String {
    val calendar = Calendar.getInstance()
    val hourCounts = mutableMapOf<String, Int>()

    sessions.forEach { session ->
        calendar.timeInMillis = session.startTimeMs
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 6..11 -> "Morning"
            in 12..17 -> "Afternoon"
            in 18..23 -> "Evening"
            else -> "Night"
        }
        hourCounts[period] = (hourCounts[period] ?: 0) + 1
    }

    return hourCounts.maxByOrNull { it.value }?.key ?: "Varied"
}

private fun formatWeekRange(startMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = startMs }
    val endMs = startMs + (7 * 24 * 60 * 60 * 1000L)
    val endCal = Calendar.getInstance().apply { timeInMillis = endMs }

    val startStr = "${cal.get(Calendar.MONTH)+1}-${cal.get(Calendar.DAY_OF_MONTH)}"
    val endStr = "${endCal.get(Calendar.MONTH)+1}-${endCal.get(Calendar.DAY_OF_MONTH)}"
    return "$startStr–$endStr"
}
```

### 3. Wire Health Insights to HistoryViewModel
Update `HistoryViewModel`:

```kotlin
val healthInsights: StateFlow<HealthInsights?> =
    combine(
        sessionSummaries,
        userPreferencesRepository.intakeSetting,  // capsule weight in grams
        userPreferencesRepository.defaultSessionType
    ) { summaries, capsuleWeight, sessionType ->
        if (summaries.isNotEmpty()) {
            computeHealthInsightsPhase2(
                summaries,
                capsuleWeight.toFloat(),
                sessionType,
                sessionMetadataMap,
                userPreferencesRepository.dayStartHour
            )
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
```

### 4. Display Grams Per Week Trend Chart
Update Health & Intake card in HistoryFragment to add a line chart:

```kotlin
healthInsights?.let { insights ->
    // Existing summary stats
    val summaryText = """
        Total intake: ${insights.totalCapsuleWeight.toInt()} g
        ${insights.capsuleCount} capsule sessions
        ${insights.averageSessionType}
    """.trimIndent()

    // NEW: Weekly trend chart
    val weeklyChart = LineChart(requireContext()).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            240.dpToPx()
        )
        // Plot insights.weightTrend
        // X-axis: week labels
        // Y-axis: grams per week
        // Render last 12 weeks
    }
    healthCard.addView(weeklyChart)

    // NEW: Insight text
    val insightText = TextView(requireContext()).apply {
        text = "Your average intake: ${insights.gramsPerWeek.toInt()} g/week. " +
               "Most active time: ${insights.mostCommonTimeOfDay}. " +
               "Rating: ${insights.efficiencyRating}."
    }
    healthCard.addView(insightText)
}
```

### 5. Add Unit Tests (Phase 2)
In `AnalyticsRepositoryTest.kt`:

```kotlin
@Test
fun healthInsights_computesGramsPerWeek() {
    val summaries = listOf(
        SessionSummary(startTimeMs = now, /* capsule */),
        SessionSummary(startTimeMs = now - 7.days, /* capsule */),
        SessionSummary(startTimeMs = now - 14.days, /* capsule */)
    )
    val metadata = mapOf(
        1L to SessionMetadata(isCapsule = true),
        2L to SessionMetadata(isCapsule = true),
        3L to SessionMetadata(isCapsule = true)
    )

    val insights = computeHealthInsightsPhase2(
        summaries,
        capsuleWeight = 0.5f,  // 0.5g per capsule
        defaultSessionType = "Capsule",
        metadata,
        dayStartHour = 0
    )

    // 3 capsules × 0.5g = 1.5g over 14 days ≈ 0.54g/week
    assertEquals(0.54f, insights.gramsPerWeek, 0.1f)
}

@Test
fun healthInsights_identifiesMostCommonTimeOfDay() {
    val summaries = listOf(
        SessionSummary(startTimeMs = morningTime),    // 9am
        SessionSummary(startTimeMs = morningTime + 1000), // 9am
        SessionSummary(startTimeMs = afternoonTime),  // 3pm
        SessionSummary(startTimeMs = eveningTime)     // 8pm
    )
    val insights = computeHealthInsightsPhase2(
        summaries, 0.5f, "Capsule", emptyMap(), 0
    )
    assertEquals("Morning", insights.mostCommonTimeOfDay)
}
```

### 6. Documentation & Polish
- [ ] Add string resources for insight labels (capsule_weight_label, health_insights_title, etc.)
- [ ] Document time-of-day thresholds in code comments
- [ ] Add CHANGELOG.md entry describing Phase 2 additions
- [ ] Verify retroactive weight changes update all trend stats (query-time computation)

## Acceptance criteria
- [ ] `HealthInsights` model extended with Phase 2 fields (grams per week, trends, efficiency)
- [ ] `computeHealthInsightsPhase2()` computes weekly trends correctly over 12 weeks
- [ ] Daily frequency and capsule frequency calculated correctly
- [ ] Most common time-of-day identified from session distribution
- [ ] Efficiency rating assigned based on intake volume
- [ ] Health & Intake card displays trend chart (line graph of grams/week)
- [ ] Insight text shows summary + recommendations
- [ ] Unit tests cover grams/week, time-of-day, efficiency calculations
- [ ] Retroactive capsule weight changes update all trend stats (no storage)
- [ ] `./gradlew test` passes
- [ ] Build passes: `./gradlew assembleDebug`

## Do NOT
- Add new database tables for health trends (compute at query time from sessions + metadata)
- Hardcode time-of-day thresholds (document rationale: 6–11 morning, 12–17 afternoon, 18–23 evening)
- Store efficiency ratings (compute from current metrics)
- Create achievements (spec says that's future F-052 work)
