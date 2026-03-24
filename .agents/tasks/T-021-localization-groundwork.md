# T-021 — Localization Groundwork

**Status**: blocked
**Phase**: 3
**Blocked by**: T-008 (Fragments must be clean before string extraction)

---

## Goal
All user-visible strings are currently hard-coded in Kotlin. Extract them to
`res/values/strings.xml`. No translations are needed yet — this is purely
infrastructure so future translations can be added without touching source code.

---

## Scope
Extract strings from:
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt`

## Do NOT extract
- Log messages (`Log.d`, `Log.e`)
- BLE command strings / constants
- Date format patterns

## Steps
1. Run Android Studio's "Extract String Resource" on each file, or do it manually.
2. Use descriptive keys: `landing_connect_button`, `session_heater_on_label`, etc.
3. Replace all `setText("...")` / `text = "..."` with `getString(R.string.key)` or `context.getString(...)`.
4. Run `./gradlew assembleDebug` — must pass.
5. Do a visual sanity check that nothing is missing or blank.

## Do NOT touch
- Any business logic
- Database
- BLE layer
