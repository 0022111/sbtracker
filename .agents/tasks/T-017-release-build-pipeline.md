# T-017 — Release Build Pipeline

**Status**: blocked
**Phase**: 3
**Blocked by**: T-001 (targetSdk 35), T-002 (R8 enabled)

---

## Goal
There is currently no signing config, no version management strategy, and no
CI release build. Set up the release pipeline so the app can be distributed.

---

## Steps
1. **Signing config**: Add `signingConfigs.release` to `build.gradle`.
   Pull keystore path, alias, passwords from environment variables (never hardcode).
   ```groovy
   signingConfigs {
       release {
           storeFile file(System.getenv("KEYSTORE_PATH") ?: "debug.keystore")
           storePassword System.getenv("KEYSTORE_PASSWORD")
           keyAlias System.getenv("KEY_ALIAS")
           keyPassword System.getenv("KEY_PASSWORD")
       }
   }
   ```
2. **Version management**: Set `versionCode` from `System.getenv("BUILD_NUMBER")?.toInt() ?: 1`.
3. **GitHub Actions**: Add a `release.yml` workflow that triggers on version tags (`v*`),
   runs `./gradlew assembleRelease`, and uploads the APK as a release artifact.
4. **Document** keystore setup in `AGENT_INFO.md` (what env vars are needed, where keystore lives).

## Do NOT touch
- Debug build configuration
- Any source files
