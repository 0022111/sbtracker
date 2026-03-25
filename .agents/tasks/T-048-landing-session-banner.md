# T-048 — Landing Page: Active Session Banner

**Phase**: Phase 3 — F-055 Homepage Redesign
**Blocked by**: T-047
**Estimated diff**: ~50 lines across 2 files

## Goal
Show a persistent "Session in Progress" banner on the Landing page when the heater is active, so the user can tap it to jump to the Session tab — making the landing page accessible and useful during sessions.

## Read these files first
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt` — navigation calls: `activity.navigateTo(1)` goes to Session tab
- `app/src/main/res/layout/fragment_landing.xml` — top-level layout structure to add a banner above or below the hero card

## Change only these files
- `app/src/main/res/layout/fragment_landing.xml`
- `app/src/main/java/com/sbtracker/ui/LandingFragment.kt`

## Steps

1. **fragment_landing.xml** — add a session banner above the navigation tiles (or just below the hero card):
   ```xml
   <androidx.cardview.widget.CardView
       android:id="@+id/cardSessionBanner"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:visibility="gone"
       app:cardBackgroundColor="@color/color_tint_orange"
       app:cardCornerRadius="12dp"
       android:layout_marginBottom="12dp">

       <LinearLayout
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:orientation="horizontal"
           android:gravity="center_vertical"
           android:padding="14dp">

           <TextView
               android:id="@+id/tvSessionBannerLabel"
               android:layout_width="0dp"
               android:layout_height="wrap_content"
               android:layout_weight="1"
               android:text="🔥 Session in Progress"
               android:textColor="@color/color_orange"
               android:textSize="14sp"
               android:textStyle="bold" />

           <TextView
               android:id="@+id/tvSessionBannerTimer"
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:text="00:00"
               android:textColor="@color/color_orange"
               android:textSize="14sp" />
       </LinearLayout>
   </androidx.cardview.widget.CardView>
   ```

2. **LandingFragment.kt** — wire the banner:
   - Add references: `val cardSessionBanner = binding.cardSessionBanner`, `val tvSessionBannerTimer = binding.tvSessionBannerTimer`
   - In the existing `combine(bleVm.sessionStats, bleVm.latestStatus, bleVm.connectionState)` collector, show/hide the banner:
     ```kotlin
     if (isConnected && s != null && s.heaterMode > 0) {
         cardSessionBanner.visibility = View.VISIBLE
         val sec = ss.durationSeconds
         tvSessionBannerTimer.text = "%02d:%02d".format(sec / 60, sec % 60)
     } else {
         cardSessionBanner.visibility = View.GONE
     }
     ```
   - Set click listener: `cardSessionBanner.setOnClickListener { activity.navigateTo(1) }`

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] Banner is hidden when heater is OFF or device is disconnected
- [ ] Banner appears when heater is ON, showing a live timer
- [ ] Tapping the banner navigates to the Session tab (index 1)
- [ ] Banner timer updates in sync with the session tile timer
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Change any existing collector logic — add minimal code alongside existing flows
- Add new ViewModel state — reuse existing `bleVm.sessionStats`
