# T-034 — Health Prefs: capsule_weight_grams + default_is_capsule in MainViewModel

**Phase**: Phase 2 — F-018 Health & Dosage Tracking
**Blocked by**: nothing
**Estimated diff**: ~25 lines added across 1 file

## Goal
Add two new user-preference StateFlows to `MainViewModel` so the rest of F-018 (settings UI, analytics, history card) has a stable data source for capsule weight and default session type.

## Read these files first
- `app/src/main/java/com/sbtracker/MainViewModel.kt` — study the existing prefs pattern (lines 99–135): `appPrefs` lazy property, `MutableStateFlow` fields, how values are loaded in `init`, and setter functions like `setDayStartHour`. Your implementation must match this pattern exactly.

## Change only these files
- `app/src/main/java/com/sbtracker/MainViewModel.kt`

## Steps

1. After the `_retentionDays` / `retentionDays` block (around line 129), add:

```kotlin
/** Global default capsule weight in grams. Range: 0.01–2.00. Default 0.10 (100 mg). */
private val _capsuleWeightGrams = MutableStateFlow(0.10f)
val capsuleWeightGrams: StateFlow<Float> = _capsuleWeightGrams.asStateFlow()

/** Whether new sessions default to capsule type. Default false (free pack). */
private val _defaultIsCapsule = MutableStateFlow(false)
val defaultIsCapsule: StateFlow<Boolean> = _defaultIsCapsule.asStateFlow()
```

2. Add setter functions (same region, after `setRetentionDays`):

```kotlin
fun setCapsuleWeight(grams: Float) {
    val clamped = grams.coerceIn(0.01f, 2.00f)
    _capsuleWeightGrams.value = clamped
    appPrefs.edit().putFloat("capsule_weight_grams", clamped).apply()
}

fun setDefaultIsCapsule(isCapsule: Boolean) {
    _defaultIsCapsule.value = isCapsule
    appPrefs.edit().putBoolean("default_is_capsule", isCapsule).apply()
}
```

3. In the `init` block, alongside the other `appPrefs` reads (around line 176–182), add:

```kotlin
_capsuleWeightGrams.value = appPrefs.getFloat("capsule_weight_grams", 0.10f)
_defaultIsCapsule.value   = appPrefs.getBoolean("default_is_capsule", false)
```

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `capsuleWeightGrams` StateFlow exists, default 0.10f, persisted under key `"capsule_weight_grams"`
- [ ] `defaultIsCapsule` StateFlow exists, default false, persisted under key `"default_is_capsule"`
- [ ] `setCapsuleWeight(g)` clamps input to 0.01..2.00 before saving
- [ ] Both values are restored from SharedPrefs on app restart (loaded in `init`)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Do not change anything in SettingsFragment, fragment_settings.xml, or AnalyticsRepository — those are separate tasks.
- Do not add UI of any kind.
- Do not alter the `appPrefs` lazy property itself.
