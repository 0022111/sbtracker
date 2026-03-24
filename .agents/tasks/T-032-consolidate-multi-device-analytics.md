# T-032 — Consolidate Multi-Device Analytics

**Status**: ready
**Phase**: 2
**Blocked by**: —
**Blocks**: —

---

## Goal
Analytics are currently computed per-device but the logic for switching or aggregating them is scattered between `MainViewModel` and `AnalyticsRepository`. Consolidate this to natively support multi-device streams.

---

## High-level steps
1.  **Enhance `AnalyticsRepository`**: Update it to return streams of data that include device identifiers by default.
2.  **Unified Session Stream**: Create a centralized way to observe "latest activity" across all devices without UI-layer filtering.
3.  **Battery Snapshot Pooling**: Formalize the polling of battery states across all known devices.
4.  **Simplify `MainViewModel`**: Remove ad-hoc multi-device filtering logic in favor of the new Repository APIs.

## Target Files
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt`

## Do NOT touch
- BLE protocol
- Custom View logic (unless specifically for analytics display)
- Migration paths
