# T-038 — History Screen: Health & Intake Analytics Card

**Phase**: Phase 2 — F-018 Health & Dosage Tracking
**Blocked by**: T-034, T-035
**Estimated diff**: ~80 lines changed across 3 files

## Goal
Wire `computeIntakeStats` (from T-035) into `MainViewModel` using a `capsuleWeightGrams` default (from T-034), and display a "Health & Intake" summary card on the History screen.

## Read these files first
- `app/src/main/java/com/sbtracker/MainViewModel.kt` — find the `init` block and the existing pattern for computing analytics StateFlows (e.g. `historyStats`, `usageInsights`). You will add `intakeStats` following the same pattern.
- `app/src/main/java/com/sbtracker/analytics/AnalyticsModels.kt` — confirm `IntakeStats` exists (added by T-035).
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` — confirm `computeIntakeStats(summaries, metadataMap, defaultWeightGrams)` exists (added by T-035).
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt` — understand how it observes VM flows and binds data to views.
- `app/src/main/res/layout/fragment_history.xml` — understand the existing card layout so your new card matches the visual style.

## Change only these files
- `app/src/main/java/com/sbtracker/MainViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`
- `app/src/main/res/layout/fragment_history.xml`

## Steps

### 1 — Add `intakeStats` StateFlow to `MainViewModel`

Add the backing field and public StateFlow near the other analytics StateFlows:

```kotlin
private val _intakeStats = MutableStateFlow(com.sbtracker.analytics.IntakeStats())
val intakeStats: StateFlow<com.sbtracker.analytics.IntakeStats> = _intakeStats.asStateFlow()
```

Add a private suspend function:

```kotlin
private suspend fun refreshIntakeStats() {
    withContext(Dispatchers.IO) {
        val address = _activeDevice.value?.deviceAddress ?: return@withContext
        val sessions = db.sessionDao().getAllForAddress(address)
        if (sessions.isEmpty()) return@withContext
        val summaries = sessions.mapNotNull { session ->
            analyticsRepo.getSessionSummary(session)
        }
        val ids = summaries.map { it.id }
        val metaList = db.sessionMetadataDao().getMetadataForSessions(ids)
        val metaMap = metaList.associateBy { it.sessionId }
        val stats = analyticsRepo.computeIntakeStats(
            summaries,
            metaMap,
            _capsuleWeightGrams.value
        )
        _intakeStats.value = stats
    }
}
```

In the `init` block, launch a coroutine to refresh intake stats whenever the active device changes (add near other analytics launches):

```kotlin
viewModelScope.launch {
    _activeDevice.collect { refreshIntakeStats() }
}
```

Add the required import at the top:
```kotlin
import com.sbtracker.analytics.IntakeStats
```

### 2 — Add "Health & Intake" card to `fragment_history.xml`

Add after the existing stats card (before the session list or at the bottom of the scroll content):

```xml
<androidx.cardview.widget.CardView
    android:id="@+id/cardIntake"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="HEALTH &amp; INTAKE"
            android:textAppearance="?attr/textAppearanceCaption" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="8dp">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:id="@+id/tvIntakeTotalAll"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="—"
                    android:textAppearance="?attr/textAppearanceHeadline6" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="all time" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:id="@+id/tvIntakeWeek"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="—"
                    android:textAppearance="?attr/textAppearanceHeadline6" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="this week" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:id="@+id/tvIntakeSplit"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="—"
                    android:textAppearance="?attr/textAppearanceHeadline6" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="cap · free" />
            </LinearLayout>

        </LinearLayout>
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

### 3 — Bind data in `HistoryFragment.kt`

Inside the lifecycle-aware `collect` block (matching the pattern used for other cards), add:

```kotlin
launch {
    vm.intakeStats.collect { stats ->
        binding.tvIntakeTotalAll.text = "%.2fg".format(stats.totalGramsAllTime)
        binding.tvIntakeWeek.text     = "%.2fg".format(stats.totalGramsThisWeek)
        binding.tvIntakeSplit.text    = "${stats.capsuleSessionCount}·${stats.freePackSessionCount}"
    }
}
```

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `MainViewModel` exposes `intakeStats: StateFlow<IntakeStats>`
- [ ] `refreshIntakeStats()` correctly loads session data, fetches metadata, and calls `computeIntakeStats`
- [ ] History screen shows a "HEALTH & INTAKE" card with all-time grams, week grams, and capsule/free split
- [ ] Card updates reactively when `_activeDevice` changes
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Do not create any new Room entities or migrations.
- Do not add capsule weight editing UI here — that is T-037.
- Do not modify `AnalyticsModels.kt` or `AnalyticsRepository.kt` — those are T-035.
- Do not modify `SessionReportActivity` — that is T-036.
- Do not add charts or complex visualizations — a simple stat card is sufficient.
