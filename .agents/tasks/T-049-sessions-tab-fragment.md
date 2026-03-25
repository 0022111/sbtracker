# T-049 — SessionsTabFragment: Sessions List Sub-Page

**Phase**: Phase 3 — F-056 History/Analytics Page Organization
**Blocked by**: nothing
**Estimated diff**: ~120 lines across 3 files

## Goal
Extract the session list, sort bar, device filter chips, clear/export controls from `HistoryFragment` into a new `SessionsTabFragment` + layout, as the first of three sub-page tabs.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt` — identify the session list block: RecyclerView setup, sort bar, device filter chips, clear button, export button (~lines 77–334)
- `app/src/main/res/layout/fragment_history.xml` — find the IDs used by the session list section to replicate them in the new layout

## Change only these files
- `app/src/main/java/com/sbtracker/ui/SessionsTabFragment.kt` *(create new)*
- `app/src/main/res/layout/fragment_sessions_tab.xml` *(create new)*
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`

## Steps

1. **Create `fragment_sessions_tab.xml`**: Copy the XML blocks for these views from `fragment_history.xml`:
   - Session count `TextView` (`tv_history_count`)
   - Device filter `LinearLayout` (`ll_device_filter`)
   - Sort bar row (`tv_sort_date`, `tv_sort_hits`, `tv_sort_duration`, `tv_sort_drain`, `tv_sort_temp`)
   - `RecyclerView` (`rv_history`)
   - Clear button (`tv_history_clear`)
   - Export button (`btn_export_history`)
   Wrap in a `ScrollView` > `LinearLayout` root.

2. **Create `SessionsTabFragment.kt`**: Move all code from `HistoryFragment` that deals with the above views. This includes:
   - `RecyclerView` + `SessionHistoryAdapter` setup
   - Sort bar collectors and `setOnClickListener` wiring
   - Device filter chip building (`buildDeviceFilterChips`, `makeChip`)
   - `openSessionReport()` helper
   - Clear all dialog
   - Export button
   Use `activityViewModels()` for `bleVm` and `historyVm` (same VMs, shared with `HistoryFragment`).

3. **HistoryFragment.kt** — remove the extracted code blocks (RecyclerView setup, sort bar, device filter, clear, export). Do NOT remove the analytics or health sections — those stay for T-050 and T-051.

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `SessionsTabFragment` inflates `fragment_sessions_tab.xml`
- [ ] Session list, sort bar, device filter, clear, export all work in the new fragment
- [ ] `HistoryFragment` no longer contains the session list code
- [ ] `./gradlew assembleDebug` passes with no missing reference errors

## Do NOT
- Wire the tab into `HistoryFragment` yet — that is T-052's job
- Remove the analytics or health sections from `HistoryFragment`
- Duplicate ViewModel state — reuse `historyVm` and `bleVm` via `activityViewModels()`
