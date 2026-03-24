# T-004 — Data Retention / Pruning for device_status

**Status**: ready
**Phase**: 0
**Effort**: medium (2–4h)
**Branch**: `claude/T-004-data-retention-pruning`
**Blocks**: —

---

## Goal
The `device_status` table inserts a row every ~500ms while the heater is on.
It grows unbounded and will exhaust device storage over months. Add a pruning
mechanism that deletes rows older than a user-configurable retention window.

No schema migration needed — this is a pure delete query.

---

## Read these files first
- `app/src/main/java/com/sbtracker/data/DeviceStatus.kt`
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (first 150 lines — init block and prefs)
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Change only these files
- The `DeviceStatusDao` file (find via Glob for `DeviceStatusDao.kt`)
- `app/src/main/java/com/sbtracker/analytics/AnalyticsRepository.kt` (add `pruneOldData`)
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (call prune on init)
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` (expose retention setting)

---

## Steps

1. In `DeviceStatusDao`, add:
   ```kotlin
   @Query("DELETE FROM device_status WHERE timestampMs < :thresholdMs")
   suspend fun deleteRowsOlderThan(thresholdMs: Long)
   ```

2. In `AnalyticsRepository`, add:
   ```kotlin
   suspend fun pruneOldData(retentionDays: Int) {
       val thresholdMs = System.currentTimeMillis() - retentionDays * 86_400_000L
       withContext(Dispatchers.IO) {
           db.deviceStatusDao().deleteRowsOlderThan(thresholdMs)
       }
   }
   ```

3. In `MainViewModel`, after the DB is ready in `init`, call:
   ```kotlin
   viewModelScope.launch {
       val days = appPrefs.getInt("retention_days", 90)
       analyticsRepo.pruneOldData(days)
   }
   ```

4. In `SettingsFragment`, add a retention days setting. Read the existing pattern
   for how `dayStartHour` dialog works and follow the same pattern. Options: 30 / 60 / 90 / 180 / Never. Default: 90.

5. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] `DeviceStatusDao` has `deleteRowsOlderThan` query
- [ ] `AnalyticsRepository.pruneOldData()` exists
- [ ] Prune is called on app startup in `MainViewModel`
- [ ] Retention days is configurable in Settings (default 90)
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- `sessions`, `hits`, `charge_cycles` tables — prune only `device_status`
- Any schema version or migration files
- `fallbackToDestructiveMigration()` — leave it for now (see T-016)
