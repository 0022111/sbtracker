# T-033 — Squash Bugs B-001, B-004, B-009

**Status**: done
**Phase**: 0
**Blocked by**: —
**Blocks**: —

---

## Goal
Address three critical issues identified in the backlog:
1. **B-001**: Remove `fallbackToDestructiveMigration()` from `AppModule` to ensure that future schema changes require explicit migrations, preventing accidental data loss in production.
2. **B-004**: Correct the inconsistent "Data Retention" setting text. Labels now explicitly state "Delete after X days" to match the actual behavior (pruning old rows) rather than just "X days" which could be interpreted as "Keep only X days".
3. **B-009**: Fix the "Dim on Charge" feature. It now persists the original brightness level across app restarts using `SharedPreferences` and respects manual brightness overrides while charging (the manual level becomes the new "restore" target).

---

## High-level steps
1. Remove `.fallbackToDestructiveMigration()` from the Room builder in `AppModule.kt`. 
2. Update `SettingsFragment.kt` and `MainViewModel.kt` to use "Delete after X days" for data retention.
3. Add `pre_dim_brightness` to `appPrefs` and update `MainViewModel.kt` logic to persist and restore correctly.
4. Update `BACKLOG.md` and `CHANGELOG.md`.

## Detailed steps
1. **[MODIFY] [AppModule.kt]**: Remove builder method.
2. **[MODIFY] [SettingsFragment.kt]**: Update `options` array and `tvRetentionValue` text collector.
3. **[MODIFY] [MainViewModel.kt]**: 
    - Initialize `preDimBrightness` from prefs.
    - Update `checkChargeDim` to save/clear from prefs.
    - Update `setBrightness` to update `preDimBrightness` if charging.
    - Update `toggleDimOnCharge` to handle mid-charge enabling/disabling.
4. **[MODIFY] [BACKLOG.md]**: Mark B-001, B-004, B-009 as `done`.
5. **[MODIFY] [CHANGELOG.md]**: Append to `[Unreleased]` section.
6. **[MODIFY] [TASKS.md]**: Add T-033 to the index.

## Do NOT touch
- Hit detection logic
- BLE state machine (use existing `BlePacket` builders)
- Analytics repository computation
