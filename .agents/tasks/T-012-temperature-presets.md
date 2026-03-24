# T-012 — Temperature Presets

**Status**: blocked
**Phase**: 2
**Blocked by**: T-009

---

## Goal
Users have favourite temperatures but no way to save them. Add named presets
stored in DataStore. Expose quick-select in the Session screen.

---

## High-level steps
1. Store `List<TempPreset(name, tempC)>` as JSON in DataStore via `UserPreferencesRepository`.
2. Default presets: Low (170°C), Medium (185°C), High (200°C).
3. Add preset management UI to `SettingsFragment` (add / rename / delete / reorder).
4. Add horizontal preset chip strip to `SessionFragment` temperature controls.
5. Tapping a chip writes the selected temperature to the device (reuse existing `setTargetTemp` command).

*(Fill in full steps when T-009 is done.)*

## Do NOT touch
- Database schema
- BLE command protocol
- Analytics
