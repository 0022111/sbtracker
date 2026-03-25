# T-065 — History Clear: Settings UI Entry Point

## Feature
F-025 — History Clear (per-device wipe of all 6 tables)

## Status
`ready`

## Blocked by
T-064

## Goal
Expose a "Clear Device History" action in `SettingsFragment` that triggers a confirmation
dialog and, on confirm, calls `historyVm.clearSessionHistory(device)` for the currently
connected device.

## Files to Read
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — for existing pattern (factory reset dialog)
- `app/src/main/res/layout/fragment_settings.xml` — to find where to insert the new row/button
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — to confirm `clearSessionHistory()` signature

## Files to Touch (max 2)
1. `app/src/main/res/layout/fragment_settings.xml`
2. `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Steps

### 1. Add a "Clear Device History" button to `fragment_settings.xml`
Add a new row/button element in the **Device** section (near `btnFactoryReset`).
Follow the same style as the existing factory reset button. Suggested id: `btnClearDeviceHistory`.
Label: **"Clear Device History"**.

### 2. Wire the button in `SettingsFragment.onViewCreated()`
Pattern to follow — the existing factory reset confirmation:
```kotlin
binding.btnClearDeviceHistory.setOnClickListener {
    val device = bleVm.savedDevices.value.firstOrNull { it.deviceAddress == bleVm.connectedAddress.value }
        ?: return@setOnClickListener
    android.app.AlertDialog.Builder(requireContext())
        .setTitle("Clear Device History")
        .setMessage("This will permanently delete all history for ${device.displayName}. This cannot be undone.")
        .setPositiveButton("Clear") { _, _ ->
            historyVm.clearSessionHistory(device)
            android.widget.Toast.makeText(requireContext(), "History cleared", android.widget.Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

> **Note on device reference**: Check how `bleVm` exposes the currently connected device
> (e.g., `bleVm.savedDevices`, `bleVm.connectedAddress`, or a dedicated `currentDevice` field).
> Adapt the snippet above to match the actual API. If no device is connected, the button
> should silently no-op (the `return@setOnClickListener` guard handles this).

## Acceptance Criteria
- A "Clear Device History" button is visible in Settings.
- Tapping it shows a confirmation dialog with a descriptive message.
- Confirming the dialog calls `historyVm.clearSessionHistory(device)`.
- Cancelling the dialog does nothing.
- If no device is connected/selected, tapping the button is a safe no-op (no crash).
- `./gradlew assembleDebug` passes.

## Notes
- Do NOT add this button for every known device in a list — only the currently active device.
  Multi-device management (list view) is a future scope item.
- Follow existing AlertDialog pattern in `SettingsFragment` for consistency.
- No new ViewModel methods are needed; `clearSessionHistory()` already exists in `HistoryViewModel`.
