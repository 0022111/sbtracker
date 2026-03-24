# T-002 — Enable R8 Minification in Release Builds

**Status**: ready
**Phase**: 0
**Effort**: small (1–2h)
**Branch**: `claude/T-002-enable-r8-minification`
**Blocks**: T-017

---

## Goal
Enable R8 code shrinking and minification for release builds.
The release APK currently ships with no minification — it's bloated and unobfuscated.

---

## Read these files first
- `app/build.gradle`
- `app/proguard-rules.pro`

## Change only these files
- `app/build.gradle`
- `app/proguard-rules.pro`

---

## Steps

1. In `app/build.gradle` `release` block, set:
   ```groovy
   minifyEnabled true
   shrinkResources true
   ```
2. Run `./gradlew assembleRelease` and inspect the output for errors.
3. For any R8 error caused by Room, Coroutines, or BLE reflection, add the appropriate keep rule to `proguard-rules.pro`. Common rules needed:
   - Room entities and DAOs: `-keep class com.sbtracker.data.** { *; }`
   - Kotlin coroutines: usually handled by the library's own consumer rules
4. Run `./gradlew assembleDebug` — must also still pass.
5. Confirm release APK size is smaller than debug APK.

---

## Done when
- [ ] `minifyEnabled true` and `shrinkResources true` in release build type
- [ ] `./gradlew assembleRelease` passes with no errors
- [ ] `./gradlew assembleDebug` still passes
- [ ] No runtime crashes introduced by R8 stripping (check logcat on a device/emulator if available)

## Do NOT touch
- Any `.kt` source files
- `debug` build type configuration
