# T-051 — HealthTabFragment: Health & Intake Sub-Page

**Phase**: Phase 3 — F-056 History/Analytics Page Organization
**Blocked by**: nothing
**Estimated diff**: ~60 lines across 3 files

## Goal
Extract the Health & Intake card from `HistoryFragment` into a dedicated `HealthTabFragment` + layout, completing the three-tab content split.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt` — the health section: `tvIntakeTotalAll`, `tvIntakeWeek`, `tvIntakeSplit` collectors (~lines 87–92, 287–293)
- `app/src/main/res/layout/fragment_history.xml` — find the Health & Intake card XML block (contains `tvIntakeTotalAll`, `tvIntakeWeek`, `tvIntakeSplit`)

## Change only these files
- `app/src/main/java/com/sbtracker/ui/HealthTabFragment.kt` *(create new)*
- `app/src/main/res/layout/fragment_health_tab.xml` *(create new)*
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`

## Steps

1. **Create `fragment_health_tab.xml`**: Copy the Health & Intake card block from `fragment_history.xml`. Include:
   - Section title: "HEALTH & INTAKE"
   - `tvIntakeTotalAll` — lifetime grams
   - `tvIntakeWeek` — this week grams
   - `tvIntakeSplit` — capsule vs free-pack session count
   - Add descriptive label TextViews for context if missing (e.g., "Total all time", "This week", "Session split")
   Wrap in `ScrollView` > `LinearLayout`.

2. **Create `HealthTabFragment.kt`**: Move the intake stats collector:
   ```kotlin
   viewLifecycleOwner.lifecycleScope.launch {
       historyVm.intakeStats.collect { stats ->
           tvIntakeTotalAll.text = "%.2fg".format(stats.totalGramsAllTime)
           tvIntakeWeek.text     = "%.2fg".format(stats.totalGramsThisWeek)
           tvIntakeSplit.text    = "${stats.capsuleSessionCount}·${stats.freePackSessionCount}"
       }
   }
   ```

3. **HistoryFragment.kt** — remove the health section view references and collector. After this task, `HistoryFragment` should contain only the scaffold that hosts the three tab fragments (for T-052 to wire up).

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `HealthTabFragment` inflates `fragment_health_tab.xml`
- [ ] Intake stats (total grams, weekly grams, session split) render correctly
- [ ] `HistoryFragment` no longer contains health section code
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Wire into the tab host — that is T-052
- Add new analytics or charts here — scope is exactly what was in the existing Health & Intake card
