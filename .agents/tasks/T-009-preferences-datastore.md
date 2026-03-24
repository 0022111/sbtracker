# T-009 — Migrate Preferences to DataStore

**Status**: ready
**Phase**: 1
**Blocked by**: —
**Blocks**: T-012

---

## Goal
Preferences are currently read/written via `SharedPreferences` directly inside
`MainViewModel`. SharedPreferences is deprecated for structured app preferences
and is not Flow-compatible. Migrate to AndroidX DataStore (Preferences DataStore).

Keys to migrate: `day_start_hour`, `phone_alerts_enabled`, `dim_on_charge`,
`target_temp`, `retention_days` (from T-004).

---

## High-level steps
1. Add `androidx.datastore:datastore-preferences` dependency.
2. Create `UserPreferencesRepository` that reads/writes all keys via DataStore.
3. Expose each preference as a `Flow<T>` for reactive observation.
4. Inject via Hilt (T-006 must be done first).
5. Remove all `appPrefs.get*` / `appPrefs.edit()` calls from `MainViewModel`.

1. Add `androidx.datastore:datastore-preferences` dependency to `app/build.gradle`.
2. Create `UserPreferencesRepository` class.
3. Migrate `SharedPreferences` keys one by one.
4. Inject the new repository into the new ViewModels (from T-007).

## Do NOT touch
- Database schema
- BLE layer
- Analytics
