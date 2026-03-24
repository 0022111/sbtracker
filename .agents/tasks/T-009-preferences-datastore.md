# T-009 — Migrate Preferences to DataStore

**Status**: blocked
**Phase**: 1
**Blocked by**: T-006
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

*(Fill in full steps when T-006 is done.)*

## Do NOT touch
- Database schema
- BLE layer
- Analytics
