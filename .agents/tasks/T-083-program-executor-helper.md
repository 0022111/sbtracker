# T-083 — Program Executor Helper (JSON Parsing + Job Management)

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-045
**Unblocks**: T-084
**Estimated diff**: ~50 lines in SessionViewModel.kt

## Goal

Create the BoostStep data class and parseBoostSteps() helper function in SessionViewModel to transform `boostStepsJson` (JSON array of offsets + boosts) into executable step objects. Lay the foundation for T-084 (program execution lifecycle).

## Read these files first

- `app/src/main/java/com/sbtracker/SessionViewModel.kt` — understand existing structure
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` — review `boostStepsJson` format

## Change only these files

- `app/src/main/java/com/sbtracker/SessionViewModel.kt`

## Steps

### 1. Add BoostStep data class and parseBoostSteps() helper to SessionViewModel

```kotlin
private data class BoostStep(val offsetSec: Int, val boostC: Int)

private fun parseBoostSteps(json: String): List<BoostStep> {
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            BoostStep(obj.optInt("offsetSec", 0), obj.optInt("boostC", 0))
        }
    } catch (e: Exception) {
        emptyList()
    }
}
```

Place these **after** the class declaration and **before** any other methods. They are internal implementation details.

### 2. Add import for org.json if not present

```kotlin
import org.json.JSONArray
```

### 3. Verify the logic with a simple test scenario

- Typical boostStepsJson: `"[{"offsetSec":0,"boostC":0},{"offsetSec":60,"boostC":5},{"offsetSec":120,"boostC":10}]"`
- Expected parse result: 3 BoostSteps with (0,0), (60,5), (120,10)
- If JSON is invalid or empty, parseBoostSteps returns emptyList() — safe fallback

### 4. Run `./gradlew assembleDebug` and confirm it passes

No functional changes yet — just data structures. The build should pass without side effects.

## Acceptance criteria

- [ ] BoostStep data class is defined in SessionViewModel
- [ ] parseBoostSteps() parses valid boostStepsJson into List<BoostStep>
- [ ] parseBoostSteps() returns emptyList() on invalid/empty JSON (no crashes)
- [ ] org.json import added
- [ ] `./gradlew assembleDebug` passes

## Do NOT

- Add Job or execution logic — that's T-084
- Modify ProgramRepository or SessionProgram entity
- Add UI or Fragment changes
- Export parseBoostSteps as public — it's internal only
