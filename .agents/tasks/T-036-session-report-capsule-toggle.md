# T-036 — Session Report: Capsule / Free-Pack Toggle

**Phase**: Phase 2 — F-018 Health & Dosage Tracking
**Blocked by**: nothing
**Estimated diff**: ~45 lines changed across 2 files

## Goal
Add a two-button Capsule / Free-Pack toggle to the session report screen so users can retroactively classify a session, persisted to the `session_metadata` table.

## Read these files first
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt` — understand the existing structure: how session data is loaded (`lifecycleScope.launch` + `withContext(Dispatchers.IO)`), how views are bound, and where to insert new logic.
- `app/src/main/res/layout/activity_session_report.xml` — understand the current layout so you know where to add the toggle row.
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt` — the entity and DAO you will use (`SessionMetadataDao.insertOrUpdate`, `observeMetadataForSession`).

## Change only these files
- `app/src/main/res/layout/activity_session_report.xml`
- `app/src/main/java/com/sbtracker/SessionReportActivity.kt`

## Steps

### 1 — Add toggle row to `activity_session_report.xml`

Find a logical spot near the top of the stat grid (after the date or hits row). Add:

```xml
<!-- Capsule / Free-Pack toggle -->
<LinearLayout
    android:id="@+id/report_pack_type_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:layout_marginTop="12dp"
    android:layout_marginBottom="4dp">

    <Button
        android:id="@+id/report_btn_capsule"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="CAPSULE"
        android:layout_marginEnd="4dp" />

    <Button
        android:id="@+id/report_btn_free_pack"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="4dp"
        android:text="FREE PACK" />

</LinearLayout>
```

### 2 — Wire the toggle in `SessionReportActivity.kt`

Inside `onCreate`, after `val sessionId = intent.getLongExtra("session_id", -1L)`, add:

```kotlin
val btnCapsule  = findViewById<Button>(R.id.report_btn_capsule)
val btnFreePack = findViewById<Button>(R.id.report_btn_free_pack)
```

After the main `lifecycleScope.launch { ... }` block (after the session is loaded and views are bound), add a **second** `lifecycleScope.launch` that observes metadata and drives the button state:

```kotlin
lifecycleScope.launch {
    db.sessionMetadataDao().observeMetadataForSession(sessionId).collect { meta ->
        val isCapsule = meta?.isCapsule ?: false
        btnCapsule.isEnabled  = !isCapsule
        btnFreePack.isEnabled = isCapsule
        btnCapsule.alpha  = if (isCapsule) 1.0f else 0.5f
        btnFreePack.alpha = if (!isCapsule) 1.0f else 0.5f
    }
}
```

Wire the button click handlers (add before the `finish()` button):

```kotlin
btnCapsule.setOnClickListener {
    lifecycleScope.launch(Dispatchers.IO) {
        db.sessionMetadataDao().insertOrUpdate(
            com.sbtracker.data.SessionMetadata(sessionId = sessionId, isCapsule = true)
        )
    }
}

btnFreePack.setOnClickListener {
    lifecycleScope.launch(Dispatchers.IO) {
        db.sessionMetadataDao().insertOrUpdate(
            com.sbtracker.data.SessionMetadata(sessionId = sessionId, isCapsule = false)
        )
    }
}
```

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Session report screen shows two buttons: "CAPSULE" and "FREE PACK"
- [ ] The active selection is visually distinct (alpha difference) and its button is disabled
- [ ] Tapping a button writes to `session_metadata` via `insertOrUpdate`
- [ ] Default state (no metadata row) shows FREE PACK as active (isCapsule = false)
- [ ] The toggle state updates reactively via `observeMetadataForSession` — reopening the screen reflects the last saved value
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Do not add capsule weight display — that requires T-034's StateFlow and is out of scope here.
- Do not modify `MainViewModel.kt` or any analytics files.
- Do not add any other UI elements beyond the toggle.
- Do not read or write SharedPreferences — only the Room `session_metadata` table.
