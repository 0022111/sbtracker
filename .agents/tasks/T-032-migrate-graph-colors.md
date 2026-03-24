# T-032 — Migrate Hardcoded Colors in Graph Views

**Status**: ready
**Phase**: 1
**Blocked by**: —
**Blocks**: —

---

## Goal
T-027 extracted colors to `colors.xml`, but the custom graph Views still have 80+
instances of `Color.parseColor("#...")`. Migrate them all to use resource colors
via `ContextCompat.getColor(context, R.color.xxx)`.

---

## Read first
- `app/src/main/res/values/colors.xml` (see what's already defined)
- `app/src/main/java/com/sbtracker/GraphView.kt`
- `app/src/main/java/com/sbtracker/BatteryGraphView.kt`
- `app/src/main/java/com/sbtracker/SessionGraphView.kt`
- `app/src/main/java/com/sbtracker/HistoryBarChartView.kt`
- `app/src/main/java/com/sbtracker/HistoryTimelineView.kt`

## Change only these files
- `app/src/main/res/values/colors.xml` (add missing color entries)
- `app/src/main/java/com/sbtracker/GraphView.kt`
- `app/src/main/java/com/sbtracker/BatteryGraphView.kt`
- `app/src/main/java/com/sbtracker/SessionGraphView.kt`
- `app/src/main/java/com/sbtracker/HistoryBarChartView.kt`
- `app/src/main/java/com/sbtracker/HistoryTimelineView.kt`

## Steps
1. Audit all `Color.parseColor(...)` calls in the 5 graph Views.
2. For each unique hex color, check if it already exists in `colors.xml`. If not, add it with a semantic name (e.g., `graph_temp_line`, `graph_grid`, `graph_battery_high`).
3. Replace `Color.parseColor("#XXXXXX")` with `ContextCompat.getColor(context, R.color.xxx)`.
4. Initialize color values in `init {}` blocks or constructors (not in `onDraw()` — avoid repeated lookups).
5. Verify visual consistency by confirming hex values match exactly.
6. `./gradlew assembleDebug` — must pass.

## Do NOT touch
- Drawing logic or layout math
- Touch/gesture handling
- Any non-graph files
- Database schema
