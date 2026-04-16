# SBTracker

**Android BLE companion for Storz & Bickel vaporizers** (Veazy, Venty, Crafty+, Mighty+).
Connects over Bluetooth LE, logs every status packet, and derives sessions / hits / analytics from the log.

---

## Core idea

Poll the device. Log what it tells you. Derive everything from the log.

The god log is `device_status` — one row per BLE status packet. Sessions and hits are never stored as state; they're computed at query time from the log, which means **any algorithm improvement retroactively improves all history** without migrations.

---

## Module map

```
com.sbtracker
├── App.kt              Application + singleton graph (Db)
├── MainActivity.kt     Permissions + binds the service + hosts Compose
├── ble/
│   ├── Protocol.kt     UUIDs, command bytes, masks — S&B protocol constants
│   ├── Packet.kt       Pure codec (build / parse), no Android deps
│   ├── BleManager.kt   GATT lifecycle + scan + inbound packet channel
│   ├── WriteQueue.kt   Serializes GATT writes (stack rejects concurrent ops)
│   └── BleService.kt   Foreground service: owns connection, polls, logs
├── core/
│   ├── Sessions.kt     Pure: List<DeviceStatus> → List<Session>
│   ├── Hits.kt         Pure: List<DeviceStatus> → List<Hit>
│   └── Summary.kt      Pure: Session + slice of log → Summary stats
├── data/
│   └── Db.kt           Room DB, entities, DAOs (one file)
└── ui/
    ├── Theme.kt        Compose theme (ember on ink)
    ├── HomeViewModel.kt  Exposes service state to the UI
    └── HomeScreen.kt     Single Compose screen
```

## Data layer

| Table            | Purpose                                            | Notes                         |
|------------------|----------------------------------------------------|-------------------------------|
| `device_status`  | Time-series god log (every 0x01 packet)            | Indexed on (addr, ts)         |
| `device_info`    | Identity (serial, colour, type)                    | Upsert, one row per device    |
| `extended_data`  | Lifetime counters (heater minutes, charge minutes) | Upsert, one row per device    |
| `sessions`       | Session boundaries (start/end)                     | Only index into `device_status` |

**No other tables.** Hit counts, averages, drain rates, heat-up time, peaks — all derived from the log at query time via `core/`.

## BLE layer

Single GATT service, single characteristic. 20-byte packets keyed by byte 0 (`CMD_STATUS = 0x01`, `CMD_EXTENDED = 0x04`, `CMD_IDENTITY = 0x05`).

`BleService` keeps one connection alive and polls status at 500 ms while the heater is on, 30 s when idle. Every inbound packet → one Room insert.

## UI

One Compose screen: connection state, live status card, heat on/off, session list. When a feature needs its own screen, it gets added here — not to a growing web of fragments.

## Running

```bash
./gradlew assembleDebug        # requires JDK 21
./gradlew lint
```

## Principles

1. **Never store derived data** — compute from `device_status`.
2. **Pure core, impure shell** — `core/` has no Android imports and is trivially testable.
3. **One connection, one service** — `BleService` is the sole GATT owner.
4. **Explicit Room migrations** — increment version by one, export schema.
