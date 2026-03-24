# T-030 — Migrate UI to Jetpack Compose

**Status**: ready
**Phase**: 2
**Blocked by**: —
**Blocks**: T-031

---

## Goal
The app currently uses programmatic Views which are difficult to maintain as UI complexity grows. Migrate the UI layer to Jetpack Compose to simplify state management and modernise the UI.

---

## High-level steps
1.  **Add Compose dependencies** to `app/build.gradle`.
2.  **Enable Compose** in build features.
3.  **Create a Pilot Fragment**: Migrate `SettingsFragment` or a simple card to Compose first.
4.  **Define Theme**: Create `SBTheme`, `SBColors`, and `SBTypography` using the existing "Matrix Green" aesthetic.
5.  **Migrate LandingFragment**: Turn the command center into a set of Composable components.
6.  **Refactor MainActivity**: Move from Fragment-based navigation to Compose Navigation if appropriate, or keep Fragment-Compose interop.

## Target Files
- `app/build.gradle`
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`
- `app/src/main/java/com/sbtracker/ui/theme/` (NEW)

## Do NOT touch
- BLE layer (`BleManager`, `BleService`)
- Database internals
- Analytics Repository logic
