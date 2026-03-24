# T-018 — Crash Reporting

**Status**: blocked
**Phase**: 3
**Blocked by**: T-001 (stable deps first)

---

## Goal
The developer has zero visibility into production crashes. Add crash reporting.

---

## Options (choose one, confirm with user before starting)
- **Firebase Crashlytics** — standard choice, free tier sufficient, requires `google-services.json`.
- **Sentry** — no Google dependency, GDPR-friendlier, self-hostable.

## Steps (Firebase path)
1. Add `google-services.json` to `app/`.
2. Add `com.google.gms:google-services` plugin and `com.google.firebase:firebase-crashlytics` dependency.
3. Initialize in `SBTrackerApp.onCreate()` (from T-006 — Hilt app class).
4. Add a non-fatal log call at any caught BLE exception sites for signal.

## Steps (Sentry path)
1. Add `io.sentry:sentry-android` dependency.
2. Configure DSN in `AndroidManifest.xml` via `<meta-data>`.
3. Done — auto-captures uncaught exceptions.

## Do NOT touch
- Any business logic
- Database schema
- BLE protocol
