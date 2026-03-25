# T-052 — Wire History Tabs: TabLayout + ViewPager2 in HistoryFragment

**Phase**: Phase 3 — F-056 History/Analytics Page Organization
**Blocked by**: T-049, T-050, T-051
**Estimated diff**: ~80 lines across 2 files

## Goal
Replace the single-page `HistoryFragment` body with a `TabLayout` + `ViewPager2` that hosts `AnalyticsTabFragment`, `SessionsTabFragment`, and `HealthTabFragment` as swipeable tabs.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt` — current state after T-049/T-050/T-051 (should be mostly empty)
- `app/src/main/res/layout/fragment_history.xml` — current layout to be replaced with a TabLayout + ViewPager2 scaffold

## Change only these files
- `app/src/main/res/layout/fragment_history.xml`
- `app/src/main/java/com/sbtracker/ui/HistoryFragment.kt`

## Steps

1. **fragment_history.xml** — replace body with tab scaffold:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
       xmlns:app="http://schemas.android.com/apk/res-auto"
       android:layout_width="match_parent"
       android:layout_height="match_parent"
       android:orientation="vertical"
       android:background="@color/color_background">

       <com.google.android.material.tabs.TabLayout
           android:id="@+id/historyTabLayout"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           app:tabTextColor="@color/color_gray_mid"
           app:tabSelectedTextColor="@color/color_blue"
           app:tabIndicatorColor="@color/color_blue"
           app:tabMode="fixed" />

       <androidx.viewpager2.widget.ViewPager2
           android:id="@+id/historyViewPager"
           android:layout_width="match_parent"
           android:layout_height="match_parent" />
   </LinearLayout>
   ```

2. **HistoryFragment.kt** — set up the tab adapter:
   - Create a `HistoryPagerAdapter` inner class extending `FragmentStateAdapter`:
     ```kotlin
     private inner class HistoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
         override fun getItemCount() = 3
         override fun createFragment(position: Int) = when (position) {
             0 -> AnalyticsTabFragment()
             1 -> SessionsTabFragment()
             else -> HealthTabFragment()
         }
     }
     ```
   - In `onViewCreated`:
     ```kotlin
     val viewPager = binding.historyViewPager
     val tabLayout = binding.historyTabLayout
     viewPager.adapter = HistoryPagerAdapter(this)
     TabLayoutMediator(tabLayout, viewPager) { tab, position ->
         tab.text = when (position) { 0 -> "Analytics"; 1 -> "Sessions"; else -> "Health" }
     }.attach()
     ```
   - Add dependency to `build.gradle` if not already present: `implementation "com.google.android.material:material:1.x.x"` (already used for CardView — check the existing version) and `implementation "androidx.viewpager2:viewpager2:1.x.x"`.

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] History screen shows three tabs: "Analytics", "Sessions", "Health"
- [ ] Swiping between tabs loads the correct sub-fragment
- [ ] Tab indicator highlights the active tab in `color_blue`
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Re-add any direct content to `HistoryFragment` — it is now only a tab host
- Change the tab order without updating the acceptance criteria above
- Modify the three sub-fragment classes
