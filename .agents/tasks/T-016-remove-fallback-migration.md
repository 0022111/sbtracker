# T-016 — Remove fallbackToDestructiveMigration

**Status**: blocked
**Phase**: 3
**Blocked by**: T-011 (schema v3 migration must exist and be tested first)

---

## Goal
`fallbackToDestructiveMigration()` is a development safety net that silently
wipes the database if Room encounters an unrecognised schema version. It must
be removed before any public release.

---

## Pre-conditions (all must be true before starting)
- Explicit `Migration(1, 2)` exists and is tested.
- Explicit `Migration(2, 3)` exists and is tested (from T-011).
- No unreleased schema versions are pending.

## Steps
1. Verify all migration objects are registered in `AppDatabase` builder via `.addMigrations(...)`.
2. Remove `.fallbackToDestructiveMigration()` from `AppDatabase.kt`.
3. Run `./gradlew assembleDebug`.
4. Manually verify on a device/emulator: install an older schema version, upgrade — data must survive.

## Do NOT touch
- Migration logic itself
- Any other database code
