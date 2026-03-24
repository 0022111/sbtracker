# T-027 — Extract Hardcoded Colors to colors.xml

**Status**: ready
**Phase**: 1
**Effort**: small (< 1 h)
**Branch**: `claude/T-027-extract-colors-xml`
**Blocks**: —

---

## Goal
There are ~40 `Color.parseColor("#RRGGBB")` calls scattered across 4 fragment
files with no central palette. The same hex codes are repeated up to 11 times
each. Create `res/values/colors.xml`, define the theme colors, and replace every
call site with `ContextCompat.getColor(requireContext(), R.color.xxx)`.

---

## Read these files first
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`

## Change only these files
- `app/src/main/res/values/colors.xml` (**create new**)
- The 4 fragment files above

---

## Steps

### 1. Create `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Primary theme palette -->
    <color name="color_blue">#0A84FF</color>
    <color name="color_green">#30D158</color>
    <color name="color_red">#FF453A</color>
    <color name="color_yellow">#FFD60A</color>
    <color name="color_orange">#FF9F0A</color>

    <!-- Grays -->
    <color name="color_gray_mid">#636366</color>
    <color name="color_gray_dim">#8E8E93</color>
    <color name="color_surface">#2C2C2E</color>
    <color name="color_background">#111113</color>
</resources>
```

### 2. Replace call sites in all 4 fragments

Replace every `Color.parseColor("#RRGGBB")` with the corresponding resource
lookup:

| Hex | Resource name | Usage |
|-----|---------------|-------|
| `#0A84FF` | `R.color.color_blue` | active/selected tint |
| `#30D158` | `R.color.color_green` | good/normal state |
| `#FF453A` | `R.color.color_red` | error/hot state |
| `#FFD60A` | `R.color.color_yellow` | warning |
| `#FF9F0A` | `R.color.color_orange` | caution |
| `#636366` | `R.color.color_gray_mid` | inactive/dim text |
| `#8E8E93` | `R.color.color_gray_dim` | secondary text |
| `#2C2C2E` | `R.color.color_surface` | card surface |
| `#111113` | `R.color.color_background` | deep background |

Pattern:
```kotlin
// BEFORE
tvFoo.setTextColor(Color.parseColor("#0A84FF"))

// AFTER
tvFoo.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_blue))
```

Make sure `import androidx.core.content.ContextCompat` is added to each file
that uses it. Remove `import android.graphics.Color` from any file where
`Color.parseColor` is fully eliminated.

The three one-off colors (`#80A88F`, `#261905`, `#091F0D`) are custom per-usage
colors — add them to `colors.xml` with descriptive names based on their context
(e.g. `color_boost_bar_fill`, `color_heat_overlay_dark`, `color_cool_overlay_dark`)
rather than leaving them inline.

### 3. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] `app/src/main/res/values/colors.xml` exists with all 12 color entries
- [ ] No `Color.parseColor(` calls remain in any fragment file
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- Layout XML color references (those use `@color/` already or are separate)
- ViewModel / BLE / data layer
- Any custom View classes (`GraphView`, `BatteryGraphView`, etc.) — they use
  `Color.rgb()` or `Paint` internally, not `parseColor`, and are out of scope
