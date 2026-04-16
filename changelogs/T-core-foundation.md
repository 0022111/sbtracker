2026-04-16 — Core Foundation Rebuild (T-core-foundation)
- **Changed** Rebuilt from scratch around one idea: poll → log → derive. Dropped the React/WebSocket hybrid layer, XML + fragments UI, Hilt, and every piece of derived state that had been accreted as its own table or ViewModel.
- **Added** Compose single-screen UI (`HomeScreen`), pure-core derivation modules (`core/Sessions`, `core/Hits`, `core/Summary`), and one-file data layer (`data/Db`).
- **Added** Single foreground `BleService` owning the GATT connection; adaptive poll cadence (500 ms heater-on, 30 s idle).
- **Removed** `ui/` React app, `VaporizerWebSocketServer`, `TelemetryMapper`, `SessionTracker` god class, `AnalyticsRepository` (931 lines), `HistoryViewModel`, `BatteryViewModel`, `SettingsViewModel`, fragment-based screens, onboarding, session programs, backup/restore, notification action receivers.
- **Result** 12,298 Kotlin LoC → ~1,200. Room schema reset to v1 with four tables.
