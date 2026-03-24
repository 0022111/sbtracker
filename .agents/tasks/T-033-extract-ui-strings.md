# T-033 — Extract Hardcoded UI Strings to strings.xml

**Status**: ready
**Phase**: 1
**Blocked by**: —
**Blocks**: T-021 (localization impossible without string resources)

---

## Goal
Status labels ("OFFLINE", "IDLE", "HEATING", "READY", "CHARGING", "ACTIVE"),
format templates, and UI text are hardcoded throughout Fragments and custom Views.
Move them all to `strings.xml` so T-021 (localization) can proceed.

---

## Read first
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Change only these files
- `app/src/main/res/values/strings.xml`
- All files in `app/src/main/java/com/sbtracker/ui/` (Fragments only)
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt`
- `app/src/main/java/com/sbtracker/SessionHistoryAdapter.kt`

## Steps
1. Grep all `.kt` files under `com/sbtracker/ui/` and activity files for quoted string literals used in `text =`, `setText()`, `String.format()`, etc.
2. For each user-visible string, add a `<string name="...">` entry in `strings.xml` with a descriptive key (e.g., `status_offline`, `status_heating`, `label_target_temp`).
3. Replace hardcoded strings with `getString(R.string.xxx)` or `context.getString(R.string.xxx)`.
4. For format strings (e.g., `"%.1f°C"`), use `<string name="format_temp_c">%.1f°C</string>` and `getString(R.string.format_temp_c, value)`.
5. Do NOT extract: log messages, debug strings, or BLE protocol constants.
6. `./gradlew assembleDebug` — must pass.

## Do NOT touch
- Custom View classes (graph views)
- BLE layer
- Database schema
- Analytics logic
