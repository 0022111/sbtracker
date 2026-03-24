# T-025 — Fix Day Start Hour Subtitle

**Status**: ready
**Phase**: 0
**Effort**: tiny (< 15 min)
**Branch**: `claude/T-025-fix-day-start-subtitle`
**Blocks**: —

---

## Goal
`tv_day_start_subtitle` in the Settings layout shows a static string
`"Day view begins at 4 AM"` but is never updated when the user changes the
hour. The subtitle should reflect the currently selected hour — just like
`tv_day_start_value` does.

---

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` (the `dayStartHour` collector)

## Change only these files
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

---

## Steps

1. Find the existing `dayStartHour` observer:
   ```kotlin
   viewLifecycleOwner.lifecycleScope.launch {
       vm.dayStartHour.collect { hour ->
           val text = if (hour == 0) "12 AM" ...
           tvDayStartValue.text = text
       }
   }
   ```

2. Add a reference to the subtitle TextView at the top of `onViewCreated`
   alongside `tvDayStartValue`:
   ```kotlin
   val tvDayStartSubtitle = view.findViewById<TextView>(R.id.tv_day_start_subtitle)
   ```

3. Inside the same collector, add:
   ```kotlin
   tvDayStartSubtitle.text = "Day view begins at $text"
   ```

4. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] Subtitle updates dynamically when `dayStartHour` changes
- [ ] Default (4 AM) shows "Day view begins at 4 AM"
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- The layout XML
- Any ViewModel logic
