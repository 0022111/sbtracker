# T-047 — Landing Page: Suppress Idle 0°C and Show Charge State

**Phase**: Phase 3 — F-055 Homepage Redesign
**Blocked by**: nothing
**Estimated diff**: ~60 lines across 2 files

## Goal
Remove misleading 0°C/32°F display when the heater is idle, and add a visible charging badge to the hero card.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt` — hero online state collector (~lines 172–212); current `tvLiveTemp` + `tvLiveTarget` display logic
- `app/src/main/res/layout/fragment_landing.xml` — find `tvCmdLiveTemp`, `tvCmdLiveTarget`, and the hero card layout to add a charge badge view

## Change only these files
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`
- `app/src/main/res/layout/fragment_landing.xml`

## Steps

1. **fragment_landing.xml** — add a charge badge inside the hero card (near `tvCmdLiveStatus`):
   ```xml
   <TextView
       android:id="@+id/tvCmdChargeBadge"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content"
       android:text="⚡ CHARGING"
       android:textSize="11sp"
       android:textColor="@color/color_green"
       android:visibility="gone"
       android:paddingStart="8dp"
       android:paddingEnd="8dp"
       android:paddingTop="4dp"
       android:paddingBottom="4dp" />
   ```
   Place it directly below `tvCmdLiveStatus` within the hero card's online layout.

2. **LandingFragment.kt** — in the hero online state collector (the `combine(bleVm.latestStatus, bleVm.connectionState, bleVm.isCelsius)` block):
   - **Suppress idle temp**: Only show `tvLiveTemp` and `tvLiveTarget` when `s.heaterMode > 0`:
     ```kotlin
     if (s.heaterMode > 0) {
         tvLiveTemp.visibility = View.VISIBLE
         tvLiveTarget.visibility = View.VISIBLE
         tvLiveTemp.text = s.currentTempC.toDisplayTemp(celsius).toString()
         tvLiveTarget.text = "/ ${s.targetTempC.toDisplayTemp(celsius)}${celsius.unitSuffix()}"
     } else {
         tvLiveTemp.visibility = View.INVISIBLE  // preserve layout space
         tvLiveTarget.visibility = View.GONE
     }
     ```
   - **Show charge badge**: Reference `binding.tvCmdChargeBadge` (add binding reference at top of `onViewCreated`):
     ```kotlin
     val tvChargeBadge = binding.tvCmdChargeBadge
     // Inside the collector:
     tvChargeBadge.visibility = if (s.isCharging) View.VISIBLE else View.GONE
     ```

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] When heater is OFF (heaterMode == 0): temperature text views are hidden/invisible
- [ ] When heater is ON: temperature and target display correctly as before
- [ ] When `isCharging == true`: "⚡ CHARGING" badge is visible in the hero
- [ ] When `isCharging == false`: charge badge is gone
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Change the offline layout (the `layoutOffline` section)
- Modify the Battery tile percentage display
- Rename or restructure the hero card — minimal change only
