# T-079 — Dose Visibility in Session Cards

**Phase**: Phase 3 — F-052 Analytics Display Refactoring
**Feature**: F-052
**Blocked by**: T-049 (SessionsTabFragment must exist)
**Estimated diff**: ~50 lines across 3 files

## Goal

Surface the **grams consumed** value on each session card in the Sessions tab.
When a session has `SessionMetadata` with `isCapsule = true`, the card should
display the capsule weight as "X.Xg" beside the hit count. Free-pack sessions
show nothing (or a "Free-pack" label).

## Background

`SessionMetadata` is joined to `SessionSummary` via the
`AnalyticsRepository` / `HistoryViewModel`. The existing adapter
`SessionHistoryAdapter` renders `SessionSummary` rows. The capsule weight
per-session comes from `SessionMetadata.capsuleWeightGrams`; a fallback of
`UserPreferences.capsuleWeightGrams` is used when it is 0.

`IntakeStats` in `AnalyticsModels.kt` already carries `avgGramsPerSession`
but per-session gram data is in `SessionMetadata`, not `SessionSummary`.
This task extends the session card, not the aggregate analytics.

## Read these files first

- `app/src/main/java/com/sbtracker/SessionHistoryAdapter.kt`
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt`
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt`

## Change only these files

- `app/src/main/java/com/sbtracker/SessionHistoryAdapter.kt`
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt`
- (layout file used by the adapter, if it exists as XML; if programmatic, stay in the adapter)

## Steps

1. **`HistoryViewModel`**: Ensure the sessions list emitted to the UI carries
   a `Map<Long, SessionMetadata>` (sessionId → metadata) alongside the
   `List<SessionSummary>`. If it already does, confirm the capsule weight
   fallback logic is applied (use `defaultCapsuleWeightGrams` from prefs when
   `SessionMetadata.capsuleWeightGrams == 0`).

2. **`SessionHistoryAdapter`**: Accept the metadata map as a secondary
   parameter. In `onBindViewHolder`, look up the session's metadata and:
   - If `isCapsule == true`: show `"${gramsForSession}g"` in a small
     `TextView` next to the hit count chip.
   - If `isCapsule == false` or metadata absent: hide the gram label.

3. No new DB columns, no migrations, no schema changes.

## Acceptance criteria

- [ ] Capsule sessions show gram weight on their card in the Sessions tab
- [ ] Free-pack sessions show no gram label
- [ ] Sessions with no metadata show no gram label (graceful null handling)
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Modify `SessionSummary` or `AnalyticsModels.kt`
- Touch the Analytics tab or Health tab layouts
- Add DB columns — `SessionMetadata` already stores the data
