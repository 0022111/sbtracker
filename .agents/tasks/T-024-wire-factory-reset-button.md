# T-024 — Wire Factory Reset Button

**Status**: ready
**Phase**: 0
**Effort**: small (< 1h)
**Branch**: `claude/T-024-wire-factory-reset-button`
**Blocks**: —

---

## Goal
`btn_factory_reset` is defined in the Settings layout but has no click handler
in `SettingsFragment`. `MainViewModel.factoryReset()` already exists and sends
the correct BLE command. Wire up the button with a confirmation dialog so it
cannot be triggered accidentally.

---

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`
- `app/src/main/res/layout/fragment_settings.xml` (the `btn_factory_reset` section)

## Change only these files
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

---

## Steps

1. Find the `btn_find_device` click handler (near the bottom of `onViewCreated`) — use it as a reference.

2. Add directly below it:
   ```kotlin
   view.findViewById<Button>(R.id.btn_factory_reset).setOnClickListener {
       android.app.AlertDialog.Builder(requireContext())
           .setTitle("Factory Reset Device")
           .setMessage("This will reset the device to factory defaults. Are you sure?")
           .setPositiveButton("Reset") { _, _ -> vm.factoryReset() }
           .setNegativeButton("Cancel", null)
           .show()
   }
   ```

3. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] Tapping the button shows a confirmation dialog
- [ ] Confirming calls `vm.factoryReset()`
- [ ] Cancelling does nothing
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- The layout XML (button already exists and styled correctly)
- `MainViewModel.factoryReset()` — it already works
