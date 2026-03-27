2026-03-27 — Web UI Parity Cleanup & Product Reset (T-AUDIT)
- **Fixed** the WebView parity issue by rebuilding the React UI and re-syncing packaged Android assets so the app now reflects the actual `ui/` source.
- **Fixed** broken web-shell behavior including non-persistent onboarding, globally exposed developer mode, vertical scrolling issues, and a flaky radial temperature selector.
- **Improved** the telemetry bridge so the web UI receives the native analytics and battery fields it was missing.
- **Redesigned** the web UI copy and navigation to remove internal/dev jargon and make screens read like a real companion app instead of an engineering demo.
- **Restored** useful parity from the native app, including today/last-session context on Home, better battery context, and clearer analytics focused on sessions, habits, and device health.
- **Moved** backup/restore/history wipe controls out of Insights and into Settings.
- **Direction** for follow-up work: keep simplifying around product value, prefer spiritually equivalent parity over line-for-line recreation, and avoid reintroducing “future glass” or backend terminology into user-facing copy.
