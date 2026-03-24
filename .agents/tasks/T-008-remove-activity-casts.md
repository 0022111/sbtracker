# T-008 — Remove Fragment–Activity Cast Coupling

**Status**: blocked
**Phase**: 1
**Blocked by**: T-007
**Blocks**: T-019, T-020, T-021

---

## Goal
Every Fragment currently does `requireActivity() as MainActivity` to access
the shared ViewModel. This tightly couples Fragment to Activity, makes Fragments
untestable in isolation, and will crash if the Fragment is ever hosted elsewhere.

Replace with `activityViewModels<BleViewModel>()` (and other scoped ViewModels
from T-007) so Fragments depend only on ViewModel interfaces.

---

## Files to fix
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

*(Fill in full steps when T-007 is done.)*

## Do NOT touch
- ViewModel logic
- Analytics
- Database
