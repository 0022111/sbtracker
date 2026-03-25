# T-054 — Session Report: Human-Readable Extraction Timeline

**Phase**: Phase 3 — F-054 Session Page Complete Redesign
**Blocked by**: T-053
**Estimated diff**: ~50 lines across 1 file

## Goal
Replace the raw hit log format ("HIT 1: 5s @ 185°C  (0m 0s)") with a human-readable extraction timeline that provides context for each hit (e.g., size label, relative time offset, gap between hits).

## Read these files first
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt` — the hit log rendering block (~lines 103–112): `hits.mapIndexed { i, h -> ... }.joinToString("\n")`

## Change only these files
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt`

## Steps

Replace the hit log block in `SessionReportActivity.kt`:

```kotlin
hitLogView.text = if (hits.isEmpty()) {
    "No extraction data recorded"
} else {
    hits.mapIndexed { i, h ->
        val durSec    = h.durationMs / 1000
        val offsetSec = (h.startTimeMs - session.startTimeMs) / 1000
        val tempStr   = if (h.peakTempC > 0) " @ ${h.peakTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}" else ""

        // Size label
        val sizeLabel = when {
            durSec >= 8 -> "Big rip"
            durSec >= 4 -> "Full pull"
            durSec >= 2 -> "Sip"
            else        -> "Micro"
        }

        // Human-readable offset: "0:15 in"
        val offsetLabel = "%d:%02d in".format(offsetSec / 60, offsetSec % 60)

        // Gap from previous hit
        val gapStr = if (i > 0) {
            val gapSec = (h.startTimeMs - hits[i - 1].startTimeMs - hits[i - 1].durationMs) / 1000
            if (gapSec >= 3) "  (+${gapSec}s gap)" else ""
        } else ""

        "● $sizeLabel  ${durSec}s${tempStr}  —  $offsetLabel$gapStr"
    }.joinToString("\n\n")
}
```

This renders each hit as:
```
● Full pull  6s @ 185°C  —  0:12 in

● Sip  3s @ 190°C  —  1:05 in  (+47s gap)

● Big rip  9s @ 195°C  —  2:30 in  (+32s gap)
```

Also display the starting battery level in `report_tv_start_battery` (new ID from T-053):
```kotlin
findViewById<TextView>(R.id.report_tv_start_battery)?.text = "Started at ${startBat}%"
```

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Each hit entry has a size label (Micro/Sip/Full pull/Big rip)
- [ ] Offset is shown as "M:SS in" format instead of raw "(Xm Ys)"
- [ ] Gap from previous hit is shown for pauses ≥ 3s
- [ ] Starting battery is displayed in `report_tv_start_battery`
- [ ] Empty state reads "No extraction data recorded"
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Change the `SessionGraphView` or graph rendering
- Modify the DAO or data layer
- Change the capsule/free-pack toggle logic
