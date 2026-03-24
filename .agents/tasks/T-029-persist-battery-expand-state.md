# T-029 — Persist BatteryFragment Card Expand State

**Status**: ready
**Phase**: 0
**Effort**: tiny (< 20 min)
**Branch**: `claude/T-029-persist-battery-expand-state`
**Blocks**: —

---

## Goal
`BatteryFragment` stores the expand/collapse state of its two cards as plain
`var` locals in `onViewCreated`:

```kotlin
var drainExpanded = true    // line 94
var healthExpanded = false  // line 105
```

These are lost every time the Fragment view is destroyed (rotation, tab switch
with ViewPager2, back-stack pop). Move them to `MainViewModel` as
`MutableStateFlow` booleans so they survive configuration changes.

---

## Read these files first
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt` (lines 90–113)
- `app/src/main/java/com/sbtracker/MainViewModel.kt` (find a nearby simple
  StateFlow to use as a pattern — e.g. `_graphPeriod`)

## Change only these files
- `app/src/main/java/com/sbtracker/MainViewModel.kt`
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt`

---

## Steps

### 1. Add StateFlows to MainViewModel

Find the block where `_graphPeriod` is declared and add directly below it:

```kotlin
private val _drainCardExpanded  = MutableStateFlow(true)
val drainCardExpanded: StateFlow<Boolean> = _drainCardExpanded.asStateFlow()

private val _healthCardExpanded = MutableStateFlow(false)
val healthCardExpanded: StateFlow<Boolean> = _healthCardExpanded.asStateFlow()

fun toggleDrainCard()  { _drainCardExpanded.value  = !_drainCardExpanded.value }
fun toggleHealthCard() { _healthCardExpanded.value = !_healthCardExpanded.value }
```

### 2. Update BatteryFragment

Replace the local-var expand block (lines ~90–113):

```kotlin
// BEFORE
var drainExpanded = true
headerDrain.setOnClickListener {
    drainExpanded = !drainExpanded
    contentDrain.visibility = if (drainExpanded) View.VISIBLE else View.GONE
    tvExpandDrain.text = if (drainExpanded) "▼" else "◀"
}
var healthExpanded = false
contentHealth.visibility = View.GONE
tvExpandHealth.text = "◀"
headerHealth.setOnClickListener {
    healthExpanded = !healthExpanded
    ...
}

// AFTER
headerDrain.setOnClickListener { vm.toggleDrainCard() }
headerHealth.setOnClickListener { vm.toggleHealthCard() }

viewLifecycleOwner.lifecycleScope.launch {
    vm.drainCardExpanded.collect { expanded ->
        contentDrain.visibility = if (expanded) View.VISIBLE else View.GONE
        tvExpandDrain.text = if (expanded) "▼" else "◀"
    }
}

viewLifecycleOwner.lifecycleScope.launch {
    vm.healthCardExpanded.collect { expanded ->
        contentHealth.visibility = if (expanded) View.VISIBLE else View.GONE
        tvExpandHealth.text = if (expanded) "▼" else "◀"
    }
}
```

Remove the two local `var` declarations entirely.

### 3. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] No `var drainExpanded` or `var healthExpanded` locals in `BatteryFragment`
- [ ] Expand state survives rotation (driven by StateFlow)
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- Layout XML
- Any other ViewModel state or BLE logic
