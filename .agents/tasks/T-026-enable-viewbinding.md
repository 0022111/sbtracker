# T-026 — Enable ViewBinding

**Status**: ready
**Phase**: 1
**Effort**: medium (2–3 h)
**Branch**: `claude/T-026-enable-viewbinding`
**Blocks**: —

---

## Goal
The project uses raw `view.findViewById<T>(R.id....)` everywhere — roughly 200+
calls spread across 5 fragments and `MainActivity`. ViewBinding is a one-line
Gradle change that generates a typed binding class per layout, eliminating
`NullPointerException` risks and all the `@SuppressLint("SetTextI18n")`
workarounds caused by concatenated strings.

Enable ViewBinding in `build.gradle` and migrate every UI class.

---

## Read these files first
- `app/build.gradle` (`android {}` block — where to add `viewBinding { enabled = true }`)
- `app/src/main/java/com/sbtracker/ui/SettingsFragment.kt` (use as template)
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`
- `app/src/main/java/com/sbtracker/ui/SessionFragment.kt`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`
- `app/src/main/java/com/sbtracker/ui/BatteryFragment.kt`
- `app/src/main/java/com/sbtracker/MainActivity.kt`

## Change only these files
- `app/build.gradle`
- All 5 fragment files above
- `app/src/main/java/com/sbtracker/MainActivity.kt`

---

## Steps

### 1. Enable ViewBinding in build.gradle
Inside the `android {}` block add:
```groovy
buildFeatures {
    viewBinding true
}
```

### 2. Fragment migration pattern
For each Fragment, replace the `onCreateView` / `onViewCreated` pair with the
standard ViewBinding pattern:

```kotlin
// BEFORE
class FooFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_foo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        ...
    }
}

// AFTER
class FooFragment : Fragment() {
    private var _binding: FragmentFooBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFooBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Replace every view.findViewById<T>(R.id.xxx) with binding.xxx
        binding.tvTitle.text = "hello"
        ...
    }
}
```

The generated binding class name is the layout file name converted to
PascalCase + `Binding`:
- `fragment_settings.xml` → `FragmentSettingsBinding`
- `fragment_landing.xml`  → `FragmentLandingBinding`
- `fragment_session.xml`  → `FragmentSessionBinding`
- `fragment_history.xml`  → `FragmentHistoryBinding`
- `fragment_battery.xml`  → `FragmentBatteryBinding`
- `activity_main.xml`     → `ActivityMainBinding`

### 3. MainActivity migration
```kotlin
// BEFORE
setContentView(R.layout.activity_main)
val pager = findViewById<ViewPager2>(R.id.view_pager)

// AFTER
private lateinit var binding: ActivityMainBinding

override fun onCreate(...) {
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.viewPager.adapter = ...
}
```

### 4. Run `./gradlew assembleDebug` — must pass.

---

## Done when
- [ ] `buildFeatures { viewBinding true }` present in `app/build.gradle`
- [ ] No `view.findViewById` calls remain in any of the 5 fragments
- [ ] No `findViewById` calls remain in `MainActivity`
- [ ] Each Fragment has a `_binding` field and nulls it in `onDestroyView`
- [ ] `./gradlew assembleDebug` passes

## Do NOT touch
- Layout XML files
- ViewModel / BLE / data layer code
- `SessionReportActivity` (separate scope — leave for later)
