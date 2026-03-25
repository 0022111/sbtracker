# T-068 — Display Starting Battery in Active Session UI

**Phase**: Phase 3 — F-053 Session Battery Starting Level
**Blocked by**: T-067
**Estimated diff**: ~20 lines changed across 2 files

## Goal
Show the starting battery level on the active session screen so the user can see how much charge they had when the session began, providing drain context (e.g. "Started at 78% → now 71% = -7%").

## Read these files first
- `app/src/main/java/com/sbtracker/SessionTracker.kt` — confirm the new `startingBattery` field added in T-067 is present in `SessionStats`.
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt` — understand how `bleVm.sessionStats` is collected and how `tvDrain` / `tvBattery` are currently bound (lines ~156–177).
- `app/src/main/res/layout/fragment_session.xml` — locate the drain stat cell (id `session_tv_drain`) and the battery card cell (id `session_tv_battery`) to decide where to insert the new label.

## Change only these files
- `app/src/main/res/layout/fragment_session.xml`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`

## Steps

### 1. Add a new TextView to `fragment_session.xml`
Locate the drain stat cell block that contains `@+id/session_tv_drain`. Directly below the drain label/value pair in that same column, add a new `TextView` to show the starting battery:
```xml
<TextView
    android:id="@+id/session_tv_starting_battery"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="START: --%"
    android:textColor="#80FFFFFF"
    android:textSize="11sp" />
```
Place it as a child of the same `LinearLayout` that holds `session_tv_drain`, immediately after the existing drain `TextView`.

### 2. Bind the new view in `SessionFragment.kt`
Inside `onViewCreated`, add a binding reference alongside `tvDrain`:
```kotlin
val tvStartingBattery = binding.sessionTvStartingBattery
```

### 3. Populate the value in the `sessionStats` collector
Inside the `bleVm.sessionStats.collect { ss -> ... }` block (around line 156–177), add after the `tvDrain` assignment:
```kotlin
if (ss.startingBattery > 0) {
    tvStartingBattery.visibility = View.VISIBLE
    tvStartingBattery.text = "START: ${ss.startingBattery}%"
} else {
    tvStartingBattery.visibility = View.GONE
}
```

### 4. Build
Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] During an active session, a "START: XX%" label is visible in the drain area of the session screen.
- [ ] The label reflects the battery level at the moment the heater turned on (matches `SessionStats.startingBattery`).
- [ ] When no session is active (`startingBattery == 0`), the label is hidden (`View.GONE`).
- [ ] `./gradlew assembleDebug` passes with no errors.

## Do NOT
- Do not modify `SessionTracker.kt` — the data field is provided by T-067.
- Do not touch `SessionReportActivity.kt` — that screen already shows the full battery range for completed sessions via `report_tv_battery_range`.
- Do not add a new DAO query or Room entity — `startingBattery` is sourced from the live `SessionStats` flow, not from the database.
- Do not show the label in the idle/offline state — it must be hidden when `startingBattery == 0`.
