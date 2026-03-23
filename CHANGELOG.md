# SBTracker — Changelog

All notable changes to this project. Agents: append to the top of the relevant section after completing work.

---

## [Unreleased]

### 2026-03-23 — Project Management Setup
- **Added** `PROJECT.md` — architecture documentation for agent continuity
- **Added** `BACKLOG.md` — structured feature/bug tracker with IDs and statuses
- **Added** `CHANGELOG.md` — this file
- **Added** `.gitignore` — standard Android ignores
- **Added** Git version control (initial commit)

### Pre-history (collapsed into initial commit)
- BLE connection layer (scan, connect, GATT, notifications, command queue)
- S&B protocol parser (CMD 0x01 status, 0x04 extended, 0x05 identity, 0x06 display)
- Room database v2 with 6 entities and event-sourcing architecture
- Session detection state machine with grace periods
- Hit detection from temperature patterns
- Charge cycle tracking
- Full analytics engine (HistoryStats, UsageInsights, BatteryInsights, PersonalRecords, DailyStats)
- Analytics caching via `AnalyticsRepository`
- Session rebuild from raw `device_status` log
- Multi-device support with serial-based tracking
- Real-time graphs (temperature, battery)
- History views (bar chart, timeline, session list)
- Session detail report
- CSV export
- Phone alerts (temp ready, 80% charge)
- Dim-on-charge feature
- Device controls (temperature, heater, boost, brightness, vibration)
