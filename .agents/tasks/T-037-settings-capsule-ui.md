# T-037 — Settings UI: Capsule Weight + Default Pack Type rows

**Phase**: Phase 2 — F-018 Health & Dosage Tracking
**Blocked by**: T-034
**Estimated diff**: ~60 lines changed across 2 files

## Goal
Add a "Health & Dosage" section to the Settings screen with two preference rows: default pack type toggle and capsule weight input — wired to the StateFlows added by T-034.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` — understand the existing pattern: how rows are bound from `binding`, how click handlers call `vm.*()` methods, and where to add new rows (after the last existing section).
- `app/src/main/res/layout/fragment_settings.xml` — understand the existing row layout pattern so your new rows are visually consistent. Copy the style of an existing row (e.g. `rowRetentionDays`).
- `app/src/main/java/com/sbtracker/MainViewModel.kt` — confirm `capsuleWeightGrams: StateFlow<Float>`, `defaultIsCapsule: StateFlow<Boolean>`, `setCapsuleWeight(Float)`, and `setDefaultIsCapsule(Boolean)` exist (added by T-034).

## Change only these files
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt`

## Steps

### 1 — Add rows to `fragment_settings.xml`

At the bottom of the scroll content (after the last existing section), add a new section group:

```xml
<!-- ── Health & Dosage ───────────────────────────────────────────── -->
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="HEALTH &amp; DOSAGE"
    android:textAppearance="?attr/textAppearanceCaption"
    android:paddingStart="16dp"
    android:paddingTop="20dp"
    android:paddingBottom="4dp" />

<!-- Default Pack Type row -->
<LinearLayout
    android:id="@+id/rowDefaultPackType"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="Default Pack Type" />

    <TextView
        android:id="@+id/tvDefaultPackTypeValue"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Free Pack"
        android:textAppearance="?attr/textAppearanceBody2" />
</LinearLayout>

<!-- Capsule Weight row -->
<LinearLayout
    android:id="@+id/rowCapsuleWeight"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="Capsule Weight" />

    <TextView
        android:id="@+id/tvCapsuleWeightValue"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="0.10 g"
        android:textAppearance="?attr/textAppearanceBody2" />
</LinearLayout>
```

### 2 — Wire rows in `SettingsFragment.kt`

Inside `onViewCreated`, add:

```kotlin
val tvDefaultPackType = binding.tvDefaultPackTypeValue
val tvCapsuleWeight   = binding.tvCapsuleWeightValue

// Observe and display current values
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
            vm.defaultIsCapsule.collect { isCapsule ->
                tvDefaultPackType.text = if (isCapsule) "Capsule" else "Free Pack"
            }
        }
        launch {
            vm.capsuleWeightGrams.collect { grams ->
                tvCapsuleWeight.text = "%.2f g".format(grams)
            }
        }
    }
}

// Click: toggle pack type
binding.rowDefaultPackType.setOnClickListener {
    vm.setDefaultIsCapsule(!vm.defaultIsCapsule.value)
}

// Click: edit capsule weight
binding.rowCapsuleWeight.setOnClickListener {
    val input = android.widget.EditText(requireContext()).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText("%.2f".format(vm.capsuleWeightGrams.value))
        selectAll()
    }
    android.app.AlertDialog.Builder(requireContext())
        .setTitle("Capsule Weight (grams)")
        .setMessage("Enter weight in grams (0.01 – 2.00)")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            val grams = input.text.toString().toFloatOrNull()
            if (grams != null) vm.setCapsuleWeight(grams)
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

Add the missing import if not already present:
```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
```

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Settings screen has a "HEALTH & DOSAGE" section header
- [ ] "Default Pack Type" row displays "Capsule" or "Free Pack" based on current setting; tapping toggles it
- [ ] "Capsule Weight" row displays formatted grams (e.g. "0.10 g"); tapping opens a number input dialog
- [ ] Changes persist across app restarts (confirmed by T-034 implementation)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Do not add any analytics computation — that is T-035.
- Do not modify `MainViewModel.kt` — T-034 already adds all needed methods.
- Do not add a capsule weight slider — AlertDialog with EditText is sufficient for now.
- Do not modify `SessionReportActivity` or any other file outside the two listed.
