# T-055 — Session Report: Session Classification Label

**Phase**: Phase 3 — F-054 Session Page Complete Redesign
**Blocked by**: T-054
**Estimated diff**: ~30 lines across 1 file

## Goal
Add a session classification badge to the report header that contextualizes the session at a glance (e.g., "Light Session", "Standard", "Heavy", "Marathon") based on duration and hit count.

## Read these files first
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt` — find the stats population block (~lines 85–100) where `report_tv_hits`, `report_tv_duration` are set; add classification after those lines

## Change only these files
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt`

## Steps

After the existing basic stats block (after `report_tv_drain` is set), add:

```kotlin
// ── Session classification ────────────────────────────────────────────
val durationMin  = durationMs / 60_000.0
val hitCount     = hitStats.hitCount
val classLabel   = when {
    durationMin < 3  && hitCount <= 3  -> "Quick Sip"
    durationMin < 5  && hitCount <= 6  -> "Light Session"
    durationMin < 10 && hitCount <= 12 -> "Standard Session"
    durationMin < 15                   -> "Heavy Session"
    else                               -> "Marathon"
}
val classColor = when (classLabel) {
    "Quick Sip"       -> ContextCompat.getColor(this, R.color.color_gray_mid)
    "Light Session"   -> ContextCompat.getColor(this, R.color.color_green)
    "Standard Session"-> ContextCompat.getColor(this, R.color.color_blue)
    "Heavy Session"   -> ContextCompat.getColor(this, R.color.color_orange)
    else              -> ContextCompat.getColor(this, R.color.color_red)
}
findViewById<TextView>(R.id.report_tv_session_class)?.apply {
    text = classLabel
    setTextColor(classColor)
}
```

Import `androidx.core.content.ContextCompat` if not already imported.

Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `report_tv_session_class` shows a classification label
- [ ] Label is colored appropriately (green for light, orange for heavy, etc.)
- [ ] Thresholds produce sensible labels for real-world sessions (1–3 min light, 10+ min heavy)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Add a new data class or analytics model — this is inline classification logic
- Change the layout file (that was T-053)
- Modify any DAO or ViewModel
