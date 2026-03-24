# T-010 — Unit Tests for Pure Functions

**Status**: blocked
**Phase**: 1
**Blocked by**: T-006 (Hilt needed to wire test dependencies cleanly)

---

## Goal
Zero test coverage today. All analytics functions are pure — they take a
`List<SessionSummary>` and return a result with no DB access. Start there.

---

## Scope (in priority order)

1. **`HitDetector.detect()`** — craft known `DeviceStatus` sequences, assert hit count and timing.
2. **`AnalyticsRepository` aggregate functions** — `computeHistoryStats`, `computeUsageInsights`, `computePersonalRecords` — fixed inputs, assert outputs.
3. **`SessionTracker` state machine** — heater-on/off transitions, grace period expiry, charge detection.
4. **`TempUtils`** — °C ↔ °F edge cases.

---

## Files to create
- `app/src/test/java/com/sbtracker/HitDetectorTest.kt`
- `app/src/test/java/com/sbtracker/analytics/AnalyticsTest.kt`
- `app/src/test/java/com/sbtracker/SessionTrackerTest.kt`
- `app/src/test/java/com/sbtracker/TempUtilsTest.kt`

*(Fill in full test cases when T-006 is done.)*

## Do NOT touch
- Any source files (tests only)
- Database schema
