# T-006 — Introduce Hilt Dependency Injection

**Status**: blocked
**Phase**: 1
**Blocked by**: T-001 (stable deps required first)
**Blocks**: T-007, T-008, T-009, T-010

---

## Goal
Nothing in the app is currently injectable or testable in isolation.
`BleManager` is constructed directly inside `MainViewModel`.
`AppDatabase.getInstance()` is a global singleton called everywhere.
Introduce Hilt so every dependency can be injected and swapped in tests.

This is the unlock for all of Phase 1.

---

## Details
*(Fill in full steps when T-001 is done and this task is unblocked.)*

High-level work:
1. Add Hilt plugin + `hilt-android` dependency to `build.gradle`.
2. Create `SBTrackerApp : Application` annotated with `@HiltAndroidApp`.
3. Create `AppModule.kt` with `@Provides` for `AppDatabase`, `AnalyticsRepository`, `BleManager`.
4. Annotate `MainViewModel` with `@HiltViewModel`; replace direct construction with injected params.
5. Annotate `MainActivity` with `@AndroidEntryPoint`.
6. Remove `AppDatabase.getInstance()` singleton from `AppDatabase.kt`.
7. `./gradlew assembleDebug` must pass.

## Do NOT touch
- Any analytics logic
- Any BLE protocol parsing
- Database schema
