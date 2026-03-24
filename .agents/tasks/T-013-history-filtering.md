# T-013 — History Search & Filtering

**Status**: blocked
**Phase**: 2
**Blocked by**: T-007

---

## Goal
The history screen shows a flat list with no way to search or filter.
Add a filter bar: date range, device selector, minimum hits. All filtering
runs over the in-memory `List<SessionSummary>` cache — no new DB queries needed.

---

## High-level steps
1. Add a collapsible filter bar at the top of `HistoryFragment`.
2. Controls: date range picker (start/end), device chip group, min hits slider.
3. Apply filters as a pure transform on the cached `List<SessionSummary>` in `HistoryViewModel`.
4. Filter state held in `HistoryViewModel` as a `StateFlow<HistoryFilter>`.
5. Once T-011 lands, add note text search to the filter bar.

*(Fill in full steps when T-007 is done.)*

## Do NOT touch
- Database queries (filter in memory only)
- Analytics computation
- Session detail view
