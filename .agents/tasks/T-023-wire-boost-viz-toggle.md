# T-023 — Wire Boost Visualization Toggle

**Status**: ready
**Phase**: 0
**Effort**: tiny (< 30 min)
**Branch**: `claude/T-023-wire-boost-viz-toggle`
**Blocks**: —

---

## Goal
`row_boost_viz` and `switch_boost_viz` exist in the Settings layout but have
no click handler and no state observer in `SettingsFragment`. The ViewModel
already has `vm.toggleBoostVisualization()`. Wire them up following the exact
same pattern as the adjacent `row_vibration` / `switch_vibration` rows.

---

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`
- `app/src/main/res/layout/fragment_settings.xml` (the `row_boost_viz` section)

## Change only these files
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

---

## Steps

1. In `onViewCreated`, find the block that wires `row_vibration`:
   ```kotlin
   val swHaptic = view.findViewById<SwitchCompat>(R.id.switch_vibration)
   ...
   view.findViewById<View>(R.id.row_vibration).setOnClickListener { vm.toggleVibrationLevel() }
   ```
2. Add directly below it:
   ```kotlin
   val swBoostViz = view.findViewById<SwitchCompat>(R.id.switch_boost_viz)
   ...
   view.findViewById<View>(R.id.row_boost_viz).setOnClickListener { vm.toggleBoostVisualization() }
   ```
3. In the `vm.latestStatus.collect { s -> ... }` block, add:
   ```kotlin
   swBoostViz.isChecked = s.boostVisualization
   ```
   — follow exactly where the other switch states are set in that same collector.
4. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] `row_boost_viz` has an `onClickListener` calling `vm.toggleBoostVisualization()`
- [ ] `switch_boost_viz` state is updated from `latestStatus`
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- The layout XML (it's already correct)
- Any other ViewModel or BLE logic
