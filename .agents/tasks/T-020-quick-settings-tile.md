# T-020 — Quick Settings Tile

**Status**: blocked
**Phase**: 3
**Blocked by**: T-008

---

## Goal
Add an Android Quick Settings tile so users can connect to their device and
start heating without opening the app.

---

## Tile behaviour
- **Inactive**: shows "SBTracker — Tap to connect". Subtitle: last known battery %.
- **Active (connected)**: shows "Connected — [device name]". Subtitle: current temp or "Heating".
- **Tap when inactive**: starts BLE scan and connects to last-known device.
- **Tap when active**: opens `MainActivity` (no disconnect — destructive, needs confirmation).

## Implementation notes
- Implement `TileService` subclass.
- Register in `AndroidManifest.xml` with `ACTION_QS_TILE_PREFERENCES` intent filter.
- Observe `BleViewModel.connectionState` Flow to update tile state.
- Keep the tile lightweight — no DB access.

*(Fill in full steps when T-008 is done.)*

## Do NOT touch
- BLE connection logic itself
- Database
- Analytics
