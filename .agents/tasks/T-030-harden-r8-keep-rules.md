# T-030 — Harden R8/ProGuard Keep Rules

**Status**: ready
**Phase**: 0
**Blocked by**: —
**Blocks**: T-017 (release build will crash without correct rules)

---

## Goal
The current `proguard-rules.pro` only keeps `com.sbtracker.data.**`. A release build
with R8 minification enabled (T-002) will strip Hilt-generated factories, Room DAO
method annotations, and Kotlin coroutine internals — causing runtime crashes.

Fix this before any release build work (T-017).

---

## Read first
- `app/proguard-rules.pro`
- `app/build.gradle` (to see which libraries are used)

## Change only these files
- `app/proguard-rules.pro`

## Steps
1. Read `app/build.gradle` to inventory all dependencies (Room, Coroutines, Lifecycle, Material, ViewBinding, and soon Hilt).
2. Add keep rules for:
   - **Room**: `@Dao`, `@Entity`, `@Database` annotated classes and their members
   - **Kotlin Coroutines**: `kotlinx.coroutines.internal.MainDispatcherFactory`, intrinsics
   - **Kotlin metadata**: `-keep class kotlin.Metadata { *; }`
   - **Hilt** (future-proof for T-006): `-keep class dagger.hilt.** { *; }`, `-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }`
   - **AndroidX Lifecycle**: keep `@OnLifecycleEvent` methods
3. Do NOT add rules for Firebase/Sentry (not yet added).
4. `./gradlew assembleRelease` — verify no R8 errors.
5. If `assembleRelease` fails due to missing signing config, verify with `assembleDebug` and note release verification is deferred to T-017.

## Do NOT touch
- Any source files
- Build configuration beyond proguard-rules.pro
- Database schema
