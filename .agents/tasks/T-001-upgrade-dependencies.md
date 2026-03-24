# T-001 — Upgrade Dependencies

**Status**: ready
**Phase**: 0
**Effort**: small (< 1h)
**Branch**: `claude/T-001-upgrade-dependencies`
**Blocks**: T-006, T-017

---

## Goal
Replace alpha/stale library versions with current stable releases.
Build must pass. No source code changes.

---

## Read these files first
- `app/build.gradle`

## Change only these files
- `app/build.gradle`

---

## Steps

1. Replace `room:2.7.0-alpha13` with latest stable Room (≥ 2.6.1).
   Check https://developer.android.com/jetpack/androidx/releases/room for current stable.
2. Upgrade `lifecycle-viewmodel-ktx`, `lifecycle-runtime-ktx`, `lifecycle-process` from `2.7.0` to `2.8.+`.
3. Upgrade `kotlinx-coroutines-android` from `1.7.3` to `1.9.+`.
4. Upgrade `core-ktx` from `1.12.0` to `1.15.+`.
5. Upgrade `appcompat` from `1.6.1` to `1.7.+`.
6. Upgrade `material` from `1.11.0` to `1.12.+`.
7. Set `compileSdk 35` and `targetSdk 35`.
8. Run `./gradlew assembleDebug` — must succeed with zero errors.

---

## Done when
- [ ] No `alpha` or `beta` Room dependency in `build.gradle`
- [ ] `targetSdk = 35`
- [ ] `./gradlew assembleDebug` passes
- [ ] No new lint errors introduced

## Do NOT touch
- Any `.kt` source files
- `proguard-rules.pro`
- Database schema files
