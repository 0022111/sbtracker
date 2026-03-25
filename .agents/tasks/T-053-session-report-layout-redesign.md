# T-053 — Session Report: Redesign Layout with Labeled Sections

**Phase**: Phase 3 — F-054 Session Page Complete Redesign
**Blocked by**: nothing
**Estimated diff**: ~120 lines across 1 file

## Goal
Redesign `activity_session_report.xml` to organize data into labeled sections with human-readable context, replacing the current flat unlabeled list of values.

## Read these files first
- `app/src/main/res/layout/activity_session_report.xml` — current layout: identify all existing view IDs to preserve them (worker must not break existing `SessionReportActivity.kt` references)
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt` — all `findViewById` calls, to confirm every existing ID remains present after the redesign

## Change only these files
- `app/src/main/res/layout/activity_session_report.xml`

## Steps

Redesign the layout into these clearly labeled sections. All existing view IDs must be retained exactly:

**Header Section**
- `report_tv_date` — session date/time (already exists)
- Add a label above it: "SESSION REPORT" in small caps

**Session Summary Section** (label: "SUMMARY")
- `report_tv_hits` with label "Hits"
- `report_tv_duration` with label "Duration"
- `report_tv_drain` with label "Battery Used"
- Add `report_tv_session_class` (new TextView, id: `report_tv_session_class`) for classification text like "Light Session" — needed for T-055

**Temperature Section** (label: "TEMPERATURE")
- `report_tv_peak_temp` with label "Peak Temp"
- `report_tv_latency` with label "Heat-up Time"

**Battery Section** (label: "BATTERY")
- `report_tv_battery_range` with label "Charge Range"
- Add `report_tv_start_battery` (new TextView, id: `report_tv_start_battery`) for starting battery context

**Extraction Timeline Section** (label: "EXTRACTION TIMELINE")
- `report_tv_hit_log` — the hit log text view (keep existing ID)

**Graph Section**
- `report_graph` — `SessionGraphView` (keep existing ID)

**Session Type Toggle** (label: "PACK TYPE")
- `report_btn_capsule` and `report_btn_free_pack` (keep existing IDs)

**Footer**
- `report_btn_close` (keep existing ID)
- `report_tv_wear` with small label "Heater Wear"

Use `CardView` groups per section with consistent `16dp` padding, `12dp` corner radius, and `@color/color_surface` background. Match the existing visual style.

Layout structure:
```
ScrollView
  LinearLayout (vertical)
    [Header Card]
    [Summary Card]
    [Temperature Card]
    [Battery Card]
    [Extraction Timeline Card]
    [Graph Card]
    [Pack Type Card]
    [Footer (wear + close button)]
```

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] All existing view IDs from `SessionReportActivity.kt` are present in the new layout
- [ ] Two new IDs are added: `report_tv_session_class`, `report_tv_start_battery`
- [ ] Layout groups data into visually distinct labeled sections
- [ ] `./gradlew assembleDebug` passes (no missing resource errors)

## Do NOT
- Change `SessionReportActivity.kt` — layout only
- Remove any existing IDs — only add new ones
- Use Compose — stay with XML Views
