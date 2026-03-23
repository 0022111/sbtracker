package com.sbtracker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sbtracker.data.SessionSummary
import com.sbtracker.SessionTracker
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : AppCompatActivity() {

    lateinit var vm: MainViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    
    private var bleService: BleService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            bleService?.initialize(vm)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBound = false
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) ensureBluetoothEnabled()
            else Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.startScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_paged)

        vm = ViewModelProvider(this)[MainViewModel::class.java]

        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_navigation)

        val adapter = PagedAdapter(this)
        viewPager.adapter = adapter
        (viewPager.getChildAt(0) as? RecyclerView)?.isNestedScrollingEnabled = false

        viewPager.isUserInputEnabled = true // Enabled horizontal swipe

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_overview -> viewPager.currentItem = 0
                R.id.nav_session  -> viewPager.currentItem = 1
                R.id.nav_history  -> viewPager.currentItem = 2
                R.id.nav_battery  -> viewPager.currentItem = 3
                R.id.nav_settings -> viewPager.currentItem = 4
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.menu.getItem(position).isChecked = true
            }
        })

        lifecycleScope.launch {
            vm.latestStatus.collect { s ->
                if (s != null && s.heaterMode > 0 && viewPager.currentItem == 0) {
                    viewPager.currentItem = 1
                }
            }
        }

        startAndBindBleService()
        
        lifecycleScope.launch {
            vm.exportUri.collect { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export History"))
            }
        }
    }

    private fun startAndBindBleService() {
        Intent(this, BleService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun ensureBluetoothEnabled() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter?.isEnabled == false) {
            requestEnableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            vm.startScan()
        }
    }

    fun checkPermissionsAndScan() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            ensureBluetoothEnabled()
        } else {
            requestPermissions.launch(needed)
        }
    }

    /** Navigate to a tab from any fragment (0=Landing, 1=Session, 2=History, 3=Battery, 4=Settings). */
    fun navigateTo(tab: Int) {
        viewPager.currentItem = tab
    }

    private inner class PagedAdapter(fa: AppCompatActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 5
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> LandingFragment()
            1 -> SessionFragment()
            2 -> HistoryFragment()
            3 -> BatteryFragment()
            4 -> SettingsFragment()
            else -> LandingFragment()
        }
    }
}

// ── Fragments ─────────────────────────────────────────────────────────────

class LandingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_landing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val vm = activity.vm

        val tvSummary = view.findViewById<TextView>(R.id.tv_device_summary)
        val tvScan = view.findViewById<TextView>(R.id.tv_scan_status)
        val tvHeater = view.findViewById<TextView>(R.id.tv_power_status)
        val tvSessions = view.findViewById<TextView>(R.id.tv_profile_sessions)
        val tvWear = view.findViewById<TextView>(R.id.tv_profile_wear)
        val tvTemp = view.findViewById<TextView>(R.id.tv_big_temp)
        val tvStatus = view.findViewById<TextView>(R.id.tv_big_status)

        val tvBatteryPct = view.findViewById<TextView>(R.id.tv_dash_battery_pct)
        val tvOfflineNote = view.findViewById<TextView>(R.id.tv_dash_offline_note)
        val vBatteryBar = view.findViewById<View>(R.id.v_dash_battery_bar)
        val tvDashSessions = view.findViewById<TextView>(R.id.tv_dash_sessions)
        val tvDashCritical = view.findViewById<TextView>(R.id.tv_dash_sessions_critical)
        val tvProfileAvgHits = view.findViewById<TextView>(R.id.tv_profile_avg_hits)
        val tvProfileAvgLength = view.findViewById<TextView>(R.id.tv_profile_avg_length)
        val tvLastDate = view.findViewById<TextView>(R.id.tv_last_session_date)
        val tvLastDuration = view.findViewById<TextView>(R.id.tv_last_session_duration)
        val tvLastHits = view.findViewById<TextView>(R.id.tv_last_session_hits)
        val tvLastDrain = view.findViewById<TextView>(R.id.tv_last_session_drain)
        // TODAY card
        val tvTodaySessions = view.findViewById<TextView>(R.id.tv_today_sessions)
        val tvTodayHits     = view.findViewById<TextView>(R.id.tv_today_hits)
        val tvTodayDuration = view.findViewById<TextView>(R.id.tv_today_duration)
        val tvTodayDrain    = view.findViewById<TextView>(R.id.tv_today_drain)

        // ── Card tap → navigate to respective tab ──────────────────────
        view.findViewById<CardView>(R.id.card_home_hero).setOnClickListener {
            activity.navigateTo(1) // Session tab
        }
        view.findViewById<CardView>(R.id.card_home_battery).setOnClickListener {
            activity.navigateTo(3) // Battery tab
        }
        view.findViewById<CardView>(R.id.card_home_today).setOnClickListener {
            activity.navigateTo(2) // History tab
        }
        view.findViewById<CardView>(R.id.card_home_lifetime).setOnClickListener {
            activity.navigateTo(2) // History tab
        }
        view.findViewById<CardView>(R.id.card_home_last_session).setOnClickListener {
            activity.navigateTo(2) // History tab
        }

        val batteryInfoClickListener = View.OnClickListener {
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Battery Prediction")
                .setMessage("Calculated based on your average battery drain per session.\n\n'Sessions' shows how many remain before hitting the 15% critical level. 'Until Empty' shows the theoretical total before 0%.")
                .setPositiveButton("Got it", null)
                .show()
        }
        tvDashSessions.setOnClickListener(batteryInfoClickListener)
        tvDashCritical.setOnClickListener(batteryInfoClickListener)

        view.findViewById<CardView>(R.id.card_scan).setOnClickListener {
            if (vm.connectionState.value == BleManager.ConnectionState.Disconnected) {
                activity.checkPermissionsAndScan()
            } else {
                vm.disconnect()
            }
        }

        view.findViewById<CardView>(R.id.card_power).setOnClickListener {
            val isOn = (vm.latestStatus.value?.heaterMode ?: 0) > 0
            vm.setHeater(!isOn)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect {
                tvScan.text = when (it) {
                    is BleManager.ConnectionState.Disconnected -> "Connect"
                    is BleManager.ConnectionState.Scanning     -> "Scanning"
                    is BleManager.ConnectionState.Connecting   -> "Linking"
                    is BleManager.ConnectionState.Connected    -> "Online"
                }
                tvScan.setTextColor(if (it is BleManager.ConnectionState.Connected) Color.parseColor("#30D158") else Color.parseColor("#0A84FF"))
            }
        }

        // Device summary — shows live info when connected, last-known device when offline
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestInfo, vm.activeDevice, vm.connectionState) { info, device, state ->
                Triple(info, device, state)
            }.collect { (info, device, state) ->
                tvSummary.text = when {
                    info != null -> "${info.deviceType} · ${info.serialNumber}"
                    device != null -> "${device.deviceType} · ${device.serialNumber} · offline"
                    else -> "No device connected"
                }
            }
        }

        // Device picker when scan finds multiple S&B devices
        viewLifecycleOwner.lifecycleScope.launch {
            vm.scannedDevices.collect { devices ->
                if (devices.size > 1 && isAdded) {
                    val names = devices.map { "${it.name}  (${it.device.address})" }.toTypedArray()
                    AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog)
                        .setTitle("Select Device")
                        .setItems(names) { _, i -> vm.connectToDevice(devices[i].device) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestStatus, vm.isCelsius, vm.sessionSummaries) { s, celsius, sessions ->
                Triple(s, celsius, sessions)
            }.collect { (s, celsius, sessions) ->
                val lastSession = sessions.firstOrNull()

                // ── Battery card ───────────────────────────────────────────
                if (s == null) {
                    val lastBat = lastSession?.endBattery
                    if (lastBat != null && lastBat > 0) {
                        tvBatteryPct.text = "${lastBat}%"
                        tvOfflineNote.visibility = View.VISIBLE
                        vBatteryBar.pivotX = 0f
                        vBatteryBar.scaleX = lastBat / 100f
                        vBatteryBar.alpha = 0.45f
                        vBatteryBar.setBackgroundColor(Color.parseColor(if (lastBat <= 20) "#FF453A" else "#444444"))
                    } else {
                        tvBatteryPct.text = "--"
                        tvOfflineNote.visibility = View.GONE
                        vBatteryBar.scaleX = 0f
                        vBatteryBar.alpha = 1f
                    }
                    tvTemp.text = "---"
                    tvTemp.setTextColor(Color.parseColor("#3A3A3C"))
                    tvStatus.text = "OFFLINE"
                    tvStatus.setTextColor(Color.parseColor("#636366"))
                    tvHeater.text = "Heater"
                    tvHeater.setTextColor(Color.parseColor("#8E8E93"))
                } else {
                    tvOfflineNote.visibility = View.GONE
                    vBatteryBar.alpha = 1f
                    tvTemp.text = "${s.currentTempC.toDisplayTemp(celsius)}${celsius.unitSuffix()}"
                    tvHeater.text = if (s.heaterMode > 0) "HEATER ON" else "HEATER OFF"
                    tvHeater.setTextColor(if (s.heaterMode > 0) Color.parseColor("#FFD60A") else Color.parseColor("#30D158"))
                    tvStatus.text = when {
                        s.isCharging -> "CHARGING"
                        s.heaterMode > 0 && !s.setpointReached -> "HEATING"
                        s.heaterMode > 0 -> "READY"
                        else -> "IDLE"
                    }
                    val stateColor = when {
                        s.isCharging -> Color.parseColor("#0A84FF")
                        s.heaterMode > 0 && !s.setpointReached -> Color.parseColor("#FF9F0A")
                        s.heaterMode > 0 -> Color.parseColor("#30D158")
                        else -> Color.WHITE
                    }
                    tvTemp.setTextColor(stateColor)
                    tvStatus.setTextColor(stateColor)
                    tvBatteryPct.text = "${s.batteryLevel}%"
                    vBatteryBar.pivotX = 0f
                    vBatteryBar.scaleX = s.batteryLevel / 100f
                    vBatteryBar.setBackgroundColor(if (s.batteryLevel <= 20) Color.parseColor("#FF453A") else Color.parseColor("#30D158"))
                }

                // ── Last Session card ──────────────────────────────────────
                if (lastSession != null) {
                    tvLastDate.text = relativeDate(lastSession.startTimeMs)
                    tvLastDuration.text = formatDurationShort(lastSession.durationMs / 1000)
                    tvLastHits.text = lastSession.hitCount.toString()
                    tvLastDrain.text = "-${lastSession.batteryConsumed}%"
                } else {
                    tvLastDate.text = "No sessions yet"
                    tvLastDuration.text = "—"
                    tvLastHits.text = "—"
                    tvLastDrain.text = "—"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionStats.collect { ss ->
                tvDashSessions.text = "${ss.sessionsToCritical} sessions"
                tvDashCritical.text = "${ss.sessionsRemaining} until empty"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.profileStats, vm.historyStats) { ps, hs -> ps to hs }.collect { (ps, hs) ->
                tvSessions.text = ps.totalSessions.toString()
                val h = ps.lifetimeHeaterMinutes / 60
                val m = ps.lifetimeHeaterMinutes % 60
                tvWear.text = if (h > 0) "${h}h ${m}m" else "${m}m"
                tvProfileAvgHits.text = if (hs.avgHitsPerSession <= 0f) "—"
                    else if (hs.avgHitsPerSession == hs.avgHitsPerSession.toLong().toFloat()) hs.avgHitsPerSession.toInt().toString()
                    else "%.1f".format(hs.avgHitsPerSession)
                tvProfileAvgLength.text = formatDurationShort(hs.avgSessionDurationSec)
            }
        }

        // ── TODAY card — computed from today-filtered summaries ─────────
        viewLifecycleOwner.lifecycleScope.launch {
            vm.todaySummaries.collect { todaySessions ->
                tvTodaySessions.text = todaySessions.size.toString()
                val totalHits     = todaySessions.sumOf { it.hitCount }
                val totalDrainPct = todaySessions.sumOf { it.batteryConsumed }
                val totalDurSec   = todaySessions.sumOf { it.durationMs } / 1000
                tvTodayHits.text     = if (totalHits > 0) totalHits.toString() else "0"
                tvTodayDuration.text = if (totalDurSec > 0) formatDurationShort(totalDurSec) else "—"
                tvTodayDrain.text    = if (totalDrainPct > 0) "-${totalDrainPct}%" else "—"
            }
        }
    }
}

class SessionFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_session, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm
        
        val tvTemp = view.findViewById<TextView>(R.id.session_tv_temp)
        val tvStatus = view.findViewById<TextView>(R.id.session_tv_status)
        val tvHits = view.findViewById<TextView>(R.id.session_tv_hits)
        val tvDrain = view.findViewById<TextView>(R.id.session_tv_drain)
        val tvTime = view.findViewById<TextView>(R.id.session_tv_time)
        val tvBattery = view.findViewById<TextView>(R.id.session_tv_battery)
        val tvHeatUp = view.findViewById<TextView>(R.id.session_tv_heat_up)
        val tvReadyTime = view.findViewById<TextView>(R.id.session_tv_ready_time)
        
        val rowRunningStats = view.findViewById<View>(R.id.session_running_stats_row)
        val gridStats = view.findViewById<View>(R.id.stats_grid)
        val btnEnd = view.findViewById<View>(R.id.btn_end_session)
        val btnStartNormal = view.findViewById<View>(R.id.btn_start_normal)

        // Hit / Breath Tracking UI
        val cardActiveHit = view.findViewById<CardView>(R.id.card_active_hit)
        val tvHitTimer = view.findViewById<TextView>(R.id.tv_hit_timer)

        // Controls
        val tvTargetBase = view.findViewById<TextView>(R.id.tv_target_temp)
        val tvBoostOffset = view.findViewById<TextView>(R.id.tv_boost_offset)
        val tvSuperBoostOffset = view.findViewById<TextView>(R.id.tv_superboost_offset)
        val groupModeSelection = view.findViewById<View>(R.id.mode_selection_group)
        val groupBoostControls = view.findViewById<View>(R.id.group_boost_controls)

        val btnNormal = view.findViewById<Button>(R.id.btn_mode_normal)
        val btnBoost = view.findViewById<Button>(R.id.btn_mode_boost)
        val btnSuper = view.findViewById<Button>(R.id.btn_mode_superboost)

        btnStartNormal.setOnClickListener { vm.startSession() }
        btnEnd.setOnClickListener { vm.setHeater(false) }

        view.findViewById<ImageButton>(R.id.btn_temp_plus).setOnClickListener { vm.adjustTemp(1) }
        view.findViewById<ImageButton>(R.id.btn_temp_minus).setOnClickListener { vm.adjustTemp(-1) }
        view.findViewById<ImageButton>(R.id.btn_boost_plus).setOnClickListener { vm.adjustBoost(1) }
        view.findViewById<ImageButton>(R.id.btn_boost_minus).setOnClickListener { vm.adjustBoost(-1) }
        view.findViewById<ImageButton>(R.id.btn_superboost_plus).setOnClickListener { vm.adjustSuperBoost(1) }
        view.findViewById<ImageButton>(R.id.btn_superboost_minus).setOnClickListener { vm.adjustSuperBoost(-1) }

        btnNormal.setOnClickListener { vm.setHeaterMode(1) }
        btnBoost.setOnClickListener { vm.setHeaterMode(2) }
        btnSuper.setOnClickListener { vm.setHeaterMode(3) }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestStatus, vm.isCelsius) { s, celsius -> s to celsius }.collect { (s, celsius) ->
                val isRunning = s != null && s.heaterMode > 0

                rowRunningStats.visibility = if (isRunning) View.VISIBLE else View.GONE
                gridStats.visibility = if (isRunning) View.VISIBLE else View.GONE
                btnEnd.visibility = if (isRunning) View.VISIBLE else View.GONE
                btnStartNormal.visibility = if (isRunning) View.GONE else View.VISIBLE

                if (s == null) {
                    tvTemp.text = "---"
                    tvStatus.text = "OFFLINE"
                    tvStatus.setTextColor(Color.parseColor("#8E8E93"))
                    return@collect
                }

                tvTemp.text = "${s.currentTempC.toDisplayTemp(celsius)}${celsius.unitSuffix()}"
                tvBattery.text = "${s.batteryLevel}%"
                tvStatus.text = if (s.heaterMode == 0) "IDLE" else if (s.setpointReached) "READY" else "HEATING"
                tvStatus.setTextColor(if (s.heaterMode == 0) Color.parseColor("#8E8E93") else if (s.setpointReached) Color.parseColor("#30D158") else Color.parseColor("#FFD60A"))

                // Boost offsets are deltas — no +32 offset, just scale
                tvBoostOffset.text = "+${s.boostOffsetC.toDisplayTempDelta(celsius)}${celsius.unitSuffix()}"
                tvSuperBoostOffset.text = "+${s.superBoostOffsetC.toDisplayTempDelta(celsius)}${celsius.unitSuffix()}"

                // Update mode button states
                btnNormal.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (s.heaterMode == 1) Color.parseColor("#0A84FF") else Color.parseColor("#2C2C2E")))
                btnBoost.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (s.heaterMode == 2) Color.parseColor("#FFD60A") else Color.parseColor("#2C2C2E")))
                btnSuper.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (s.heaterMode == 3) Color.parseColor("#FF9F0A") else Color.parseColor("#2C2C2E")))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.targetTemp, vm.isCelsius) { t, celsius -> t to celsius }.collect { (t, celsius) ->
                tvTargetBase.text = "${t.toDisplayTemp(celsius)}${celsius.unitSuffix()}"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionStats.collect { ss ->
                tvHits.text = ss.hitCount.toString()
                tvDrain.text = "${maxOf(0, ss.batteryDrain)}%"

                fun format(sec: Long) = "%02d:%02d".format(sec / 60, sec % 60)

                tvTime.text = format(ss.durationSeconds)
                // HEAT-UP: time from heater-on to setpoint first reached.
                // Shows "—" until setpoint is reached, then locks in the measured duration.
                tvHeatUp.text = if (ss.heatUpTimeSecs > 0) "${ss.heatUpTimeSecs}s" else "—"
                tvReadyTime.text = format(ss.readyDurationSec)

                // Active Hit Feedback
                if (ss.isHitActive) {
                    cardActiveHit.visibility = View.VISIBLE
                    tvHitTimer.text = "${ss.currentHitDurationSec}s"
                } else {
                    cardActiveHit.visibility = View.GONE
                }
            }
        }
    }
}

class HistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val vm = activity.vm
        val rv = view.findViewById<RecyclerView>(R.id.rv_history)
        val tvCount = view.findViewById<TextView>(R.id.tv_history_count)
        val graph = view.findViewById<HistoryBarChartView>(R.id.history_timeline)
        val llDeviceFilter = view.findViewById<android.widget.LinearLayout>(R.id.ll_device_filter)
        val tvStatsAvgDuration = view.findViewById<TextView>(R.id.tv_stats_avg_duration)
        val tvStatsAvgHits     = view.findViewById<TextView>(R.id.tv_stats_avg_hits)
        val tvStatsAvgHitLen   = view.findViewById<TextView>(R.id.tv_stats_avg_hit_len)
        val tvStatsAvgDrain    = view.findViewById<TextView>(R.id.tv_stats_avg_drain)
        val tvStatsFavTemps    = view.findViewById<TextView>(R.id.tv_stats_fav_temps)
        // Usage insights card
        val tvStreakCurrent    = view.findViewById<TextView>(R.id.tv_streak_current)
        val tvStreakLongest    = view.findViewById<TextView>(R.id.tv_streak_longest)
        val tvAvgHeatUp        = view.findViewById<TextView>(R.id.tv_avg_heat_up)
        val tvHitsPerMin       = view.findViewById<TextView>(R.id.tv_hits_per_minute)
        val tvWeekSessions     = view.findViewById<TextView>(R.id.tv_week_sessions)
        val tvWeekHits         = view.findViewById<TextView>(R.id.tv_week_hits)
        val tvWeekSessionsDelta = view.findViewById<TextView>(R.id.tv_week_sessions_delta)
        val tvWeekHitsDelta    = view.findViewById<TextView>(R.id.tv_week_hits_delta)
        val tvPeakTime         = view.findViewById<TextView>(R.id.tv_peak_time)
        val tvBusiestDay       = view.findViewById<TextView>(R.id.tv_busiest_day)
        val tvAvgSessionsPerDay = view.findViewById<TextView>(R.id.tv_avg_sessions_per_day)
        val tvTotalDaysActive   = view.findViewById<TextView>(R.id.tv_total_days_active)
        // HistoryStats extra fields
        val tvStatsMedianDuration  = view.findViewById<TextView>(R.id.tv_stats_median_duration)
        val tvStatsSessPerDay7d    = view.findViewById<TextView>(R.id.tv_stats_sess_per_day_7d)
        val tvStatsSessPerDay30d   = view.findViewById<TextView>(R.id.tv_stats_sess_per_day_30d)
        val tvStatsPeakInDay       = view.findViewById<TextView>(R.id.tv_stats_peak_in_day)

        val tvSortDate     = view.findViewById<TextView>(R.id.tv_sort_date)
        val tvSortHits     = view.findViewById<TextView>(R.id.tv_sort_hits)
        val tvSortDuration = view.findViewById<TextView>(R.id.tv_sort_duration)
        val tvSortDrain    = view.findViewById<TextView>(R.id.tv_sort_drain)
        val tvSortTemp     = view.findViewById<TextView>(R.id.tv_sort_temp)

        val adapter = SessionHistoryAdapter(
            onSessionClick = { openSessionReport(it) },
            onDeleteClick = { activity.confirmDelete(it) }
        )
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        // Sort bar clicks
        tvSortDate.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.DATE) }
        tvSortHits.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.HITS) }
        tvSortDuration.setOnClickListener { vm.setSessionSort(MainViewModel.SessionSort.DURATION) }
        tvSortDrain.setOnClickListener    { vm.setSessionSort(MainViewModel.SessionSort.DRAIN) }
        tvSortTemp.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.TEMP) }

        // Sort bar visual state
        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionSort.collect { sort ->
                val sortViews = listOf(
                    tvSortDate     to MainViewModel.SessionSort.DATE,
                    tvSortHits     to MainViewModel.SessionSort.HITS,
                    tvSortDuration to MainViewModel.SessionSort.DURATION,
                    tvSortDrain    to MainViewModel.SessionSort.DRAIN,
                    tvSortTemp     to MainViewModel.SessionSort.TEMP
                )
                sortViews.forEach { (tv, s) ->
                    val active = s == sort
                    tv.setTextColor(Color.parseColor(if (active) "#0A84FF" else "#636366"))
                    tv.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    tv.setBackgroundResource(if (active) R.drawable.bg_badge_blue else 0)
                }
            }
        }

        // Device filter chips
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.knownDevices, vm.sessionFilter) { devices, filter ->
                devices to filter
            }.collect { (devices, filter) ->
                buildDeviceFilterChips(devices, filter, llDeviceFilter, vm)
            }
        }

        // Stats summary card
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.historyStats, vm.isCelsius) { stats, celsius -> stats to celsius }.collect { (stats, celsius) ->
                tvStatsAvgDuration.text = formatDurationShort(stats.avgSessionDurationSec)
                tvStatsMedianDuration.text = formatDurationShort(stats.medianSessionDurationSec)
                tvStatsAvgHits.text = if (stats.avgHitsPerSession <= 0f) "—"
                    else if (stats.avgHitsPerSession == stats.avgHitsPerSession.toLong().toFloat()) stats.avgHitsPerSession.toInt().toString()
                    else "%.1f".format(stats.avgHitsPerSession)
                tvStatsAvgHitLen.text = if (stats.avgHitDurationSec <= 0f) "—"
                    else "${stats.avgHitDurationSec.roundToInt()}s"
                tvStatsAvgDrain.text = if (stats.avgBatteryDrainPct <= 0f) "—"
                    else "%.1f%%".format(stats.avgBatteryDrainPct)
                tvStatsFavTemps.text = if (stats.favoriteTempsCelsius.isEmpty()) "No hit data yet"
                    else stats.favoriteTempsCelsius.mapIndexed { i, (tempC, count) ->
                        "#${i + 1}  ${tempC.toDisplayTemp(celsius)}${celsius.unitSuffix()}  ·  $count hits"
                    }.joinToString("\n")
                tvStatsSessPerDay7d.text  = if (stats.sessionsPerDay7d  > 0f) "%.1f".format(stats.sessionsPerDay7d)  else "—"
                tvStatsSessPerDay30d.text = if (stats.sessionsPerDay30d > 0f) "%.1f".format(stats.sessionsPerDay30d) else "—"
                tvStatsPeakInDay.text     = if (stats.peakSessionsInADay > 0) stats.peakSessionsInADay.toString() else "—"
            }
        }

        // Usage insights card
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.usageInsights, vm.historyStats) { ui, hs -> ui to hs }.collect { (ui, hs) ->
                // Streak
                tvStreakCurrent.text = if (ui.currentStreakDays > 0) "${ui.currentStreakDays}d" else "—"
                tvStreakLongest.text = if (ui.longestStreakDays > 0) "${ui.longestStreakDays}d" else "—"
                // Heat-up time (from historyStats — filter-aware)
                tvAvgHeatUp.text = if (hs.avgHeatUpTimeSec > 0) "${hs.avgHeatUpTimeSec}s" else "—"
                // Hits per minute
                tvHitsPerMin.text = if (ui.avgHitsPerMinute > 0f) "%.2f".format(ui.avgHitsPerMinute) else "—"
                // Weekly comparison
                tvWeekSessions.text = ui.sessionsThisWeek.toString()
                tvWeekHits.text     = ui.hitsThisWeek.toString()
                val sessionDelta = ui.sessionsThisWeek - ui.sessionsLastWeek
                val hitsDelta    = ui.hitsThisWeek - ui.hitsLastWeek
                fun deltaText(d: Int) = when { d > 0 -> "+$d"; d < 0 -> "$d"; else -> "—" }
                fun deltaColor(d: Int) = when {
                    d > 0 -> Color.parseColor("#30D158")
                    d < 0 -> Color.parseColor("#FF453A")
                    else  -> Color.parseColor("#636366")
                }
                tvWeekSessionsDelta.text = deltaText(sessionDelta)
                tvWeekSessionsDelta.setTextColor(deltaColor(sessionDelta))
                tvWeekHitsDelta.text = deltaText(hitsDelta)
                tvWeekHitsDelta.setTextColor(deltaColor(hitsDelta))
                // Peak time of day
                val todLabels = listOf("Night", "Morning", "Afternoon", "Evening")
                tvPeakTime.text = if (ui.peakTimeOfDay >= 0) todLabels[ui.peakTimeOfDay] else "—"
                // Busiest day
                val dowLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                tvBusiestDay.text = if (ui.busiestDayOfWeek >= 0) dowLabels[ui.busiestDayOfWeek] else "—"
                // Avg sessions per active day + total days active
                tvAvgSessionsPerDay.text = if (ui.avgSessionsPerDay > 0f) "%.1f".format(ui.avgSessionsPerDay) else "—"
                tvTotalDaysActive.text   = if (ui.totalDaysActive > 0) ui.totalDaysActive.toString() else "—"
            }
        }

        // History list
        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionHistory.collect { items ->
                adapter.submitList(items)
                val sessions = items.count { it is HistoryItem.SessionItem }
                val charges  = items.count { it is HistoryItem.ChargeItem }
                tvCount.text = when {
                    sessions > 0 && charges > 0 -> "$sessions sessions · $charges charges"
                    sessions > 0 -> "$sessions sessions"
                    charges  > 0 -> "$charges charges"
                    else -> "No history"
                }
            }
        }

        // ── Session activity bar chart ────────────────────────────────────────
        val tvPeriodDay  = view.findViewById<TextView>(R.id.tv_graph_period_day)
        val tvPeriodWeek = view.findViewById<TextView>(R.id.tv_graph_period_week)

        tvPeriodDay.setOnClickListener  { vm.setGraphPeriod(MainViewModel.GraphPeriod.DAY) }
        tvPeriodWeek.setOnClickListener { vm.setGraphPeriod(MainViewModel.GraphPeriod.WEEK) }

        // Keep toggle visual state in sync
        viewLifecycleOwner.lifecycleScope.launch {
            vm.graphPeriod.collect { p ->
                val dayActive = p == MainViewModel.GraphPeriod.DAY
                tvPeriodDay.setTextColor(Color.parseColor(if (dayActive)  "#0A84FF" else "#636366"))
                tvPeriodDay.setTypeface(null, if (dayActive)  android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvPeriodDay.setBackgroundResource(if (dayActive)  R.drawable.bg_badge_blue else 0)
                tvPeriodWeek.setTextColor(Color.parseColor(if (!dayActive) "#0A84FF" else "#636366"))
                tvPeriodWeek.setTypeface(null, if (!dayActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvPeriodWeek.setBackgroundResource(if (!dayActive) R.drawable.bg_badge_blue else 0)
            }
        }

        // Feed daily stats + period into the bar chart
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.dailyStats, vm.graphPeriod) { daily, period ->
                daily to period
            }.collect { (daily, period) ->
                val chartPeriod = if (period == MainViewModel.GraphPeriod.WEEK)
                    HistoryBarChartView.Period.WEEK else HistoryBarChartView.Period.DAY
                graph.setData(daily, chartPeriod)
            }
        }

        view.findViewById<TextView>(R.id.tv_history_clear).setOnClickListener {
            vm.clearSessionHistory()
        }

        view.findViewById<View>(R.id.btn_export_history).setOnClickListener {
            vm.exportHistoryCsv()
        }
    }

    private fun buildDeviceFilterChips(
        devices: List<MainViewModel.SavedDevice>,
        activeFilter: String?,
        container: android.widget.LinearLayout,
        vm: MainViewModel
    ) {
        container.removeAllViews()
        val activeSerial = vm.activeDevice.value?.serialNumber

        // "Mine" chip — current/last device, null filter
        val mineLabel = vm.activeDevice.value?.deviceType?.ifEmpty { "Device" } ?: "Mine"
        container.addView(makeChip(mineLabel, activeFilter == null) {
            vm.setSessionFilter(null)
        })

        // "All" chip — only shown when multiple devices known
        if (devices.size > 1) {
            container.addView(makeChip("All", activeFilter == "all") {
                vm.setSessionFilter("all")
            })
        }

        // Per-device chips — skip the active device since "Mine" covers it
        devices.filter { it.serialNumber != activeSerial }.forEach { device ->
            val label = device.deviceType.ifEmpty { device.serialNumber.takeLast(6) }
            val active = activeFilter == device.serialNumber || activeFilter == device.deviceAddress
            container.addView(makeChip(label, active, {
                vm.setSessionFilter(device.serialNumber)
            }).also { chip ->
                chip.setOnLongClickListener {
                    android.widget.Toast.makeText(requireContext(), device.serialNumber, android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }
    }

    private fun makeChip(label: String, selected: Boolean, onClick: (() -> Unit)? = null): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor(if (selected) "#0A84FF" else "#636366"))
            setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setPadding(24, 10, 24, 10)
            if (selected) setBackgroundResource(R.drawable.bg_badge_blue)
            onClick?.let { setOnClickListener { it() } }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
        }
    }

    private fun openSessionReport(s: SessionSummary) {
        val intent = Intent(requireContext(), SessionReportActivity::class.java).apply {
            putExtra("session_id", s.id)
        }
        startActivity(intent)
    }
}

class BatteryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_battery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm

        val tvPercent      = view.findViewById<TextView>(R.id.batt_tv_percent)
        val tvStatus       = view.findViewById<TextView>(R.id.batt_tv_status)
        val cardAnalysis   = view.findViewById<View>(R.id.card_charging_analysis)
        val tvEta80        = view.findViewById<TextView>(R.id.batt_tv_eta_80)
        val tvEtaFull      = view.findViewById<TextView>(R.id.batt_tv_eta_full)
        val tvChargeRate   = view.findViewById<TextView>(R.id.batt_tv_charge_rate)
        val tvDrainRate    = view.findViewById<TextView>(R.id.batt_tv_drain_rate)
        val tvAvgDrain     = view.findViewById<TextView>(R.id.batt_tv_avg_drain)
        val tvSessionsLeft = view.findViewById<TextView>(R.id.batt_tv_sessions_left)
        val tvSessionsRange = view.findViewById<TextView>(R.id.batt_tv_sessions_range)
        val tvChargeCycles = view.findViewById<TextView>(R.id.batt_tv_charge_cycles)
        val graph          = view.findViewById<BatteryGraphView>(R.id.batt_graph)
        val tvBattPeriodDay  = view.findViewById<TextView>(R.id.batt_graph_period_day)
        val tvBattPeriodWeek = view.findViewById<TextView>(R.id.batt_graph_period_week)

        tvBattPeriodDay.setOnClickListener  { vm.setGraphPeriod(MainViewModel.GraphPeriod.DAY) }
        tvBattPeriodWeek.setOnClickListener { vm.setGraphPeriod(MainViewModel.GraphPeriod.WEEK) }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.graphPeriod.collect { p ->
                val dayActive = p == MainViewModel.GraphPeriod.DAY
                tvBattPeriodDay.setTextColor(Color.parseColor(if (dayActive)  "#0A84FF" else "#636366"))
                tvBattPeriodDay.setTypeface(null, if (dayActive)  android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvBattPeriodDay.setBackgroundResource(if (dayActive)  R.drawable.bg_badge_blue else 0)
                tvBattPeriodWeek.setTextColor(Color.parseColor(if (!dayActive) "#0A84FF" else "#636366"))
                tvBattPeriodWeek.setTypeface(null, if (!dayActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tvBattPeriodWeek.setBackgroundResource(if (!dayActive) R.drawable.bg_badge_blue else 0)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                vm.graphStatuses,
                vm.graphWindowStartMs,
                vm.sessionStats,
                vm.latestStatus
            ) { statuses, windowStart, stats, latest ->
                val etaMin = stats.chargeEtaMinutes
                val charging = latest?.isCharging == true
                val etaMs = if (charging && etaMin != null && etaMin > 0) etaMin * 60_000L else 0L
                val projLevel = if (etaMs > 0L && latest != null) latest.batteryLevel else null
                graph.setData(statuses, windowStart, etaMs, projLevel)
                Unit
            }.collect { }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(30_000L)
                    graph.invalidate()
                }
            }
        }

        // Drain analysis card
        val tvRecentAvgDrain     = view.findViewById<TextView>(R.id.batt_tv_recent_avg_drain)
        val tvDrainTrend         = view.findViewById<TextView>(R.id.batt_tv_drain_trend)
        val tvSessionsPerCharge  = view.findViewById<TextView>(R.id.batt_tv_sessions_per_charge)
        val tvAvgChargeTime      = view.findViewById<TextView>(R.id.batt_tv_avg_charge_time)
        val tvAvgGained          = view.findViewById<TextView>(R.id.batt_tv_avg_gained)
        val tvLongestRun         = view.findViewById<TextView>(R.id.batt_tv_longest_run)
        val tvDrainStdDev        = view.findViewById<TextView>(R.id.batt_tv_drain_std_dev)
        val tvAvgDod             = view.findViewById<TextView>(R.id.batt_tv_avg_dod)
        val tvEstDays            = view.findViewById<TextView>(R.id.batt_tv_est_days)
        val tvMedianDrain        = view.findViewById<TextView>(R.id.batt_tv_median_drain)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.latestStatus.collect { s ->
                if (s == null) {
                    tvPercent.text = "--"
                    tvStatus.text = "OFFLINE"
                    tvStatus.setTextColor(Color.parseColor("#636366"))
                    cardAnalysis.visibility = View.GONE
                    return@collect
                }
                tvPercent.text = "${s.batteryLevel}%"
                tvStatus.text = when {
                    s.isCharging -> "CHARGING"
                    s.heaterMode > 0 -> "ACTIVE"
                    else -> "IDLE"
                }
                tvStatus.setTextColor(when {
                    s.isCharging -> Color.parseColor("#30D158")
                    s.heaterMode > 0 -> Color.parseColor("#FFD60A")
                    else -> Color.parseColor("#8E8E93")
                })
                cardAnalysis.visibility = if (s.isCharging) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionStats.collect { ss ->
                tvEta80.text = ss.chargeEta80Minutes?.let { "${it}m" } ?: "--"
                tvEtaFull.text = ss.chargeEtaMinutes?.let { "${it}m" } ?: "--"
                tvChargeRate.text = if (ss.chargeRatePctPerMin > 0) "%.1f%%/m".format(ss.chargeRatePctPerMin) else "--"
                tvDrainRate.text = if (ss.drainRatePctPerMin > 0 && ss.state == SessionTracker.State.ACTIVE)
                    "%.1f%%/min".format(ss.drainRatePctPerMin) else "--"
                tvSessionsLeft.text = if (ss.sessionsRemaining > 0) "${ss.sessionsRemaining}" else "--"
                // Show confidence range if we have enough samples for a meaningful std dev
                tvSessionsRange.text = if (ss.drainSampleCount >= 5 && ss.sessionsRemainingLow != ss.sessionsRemainingHigh)
                    "${ss.sessionsRemainingLow}–${ss.sessionsRemainingHigh}"
                    else ""
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.rawChargeHistory.collect { cycles ->
                tvChargeCycles.text = cycles.size.toString()
            }
        }

        // Drain analysis card + all-time avg (single source of truth for drain)
        viewLifecycleOwner.lifecycleScope.launch {
            vm.batteryInsights.collect { bi ->
                tvAvgDrain.text = if (bi.allTimeAvgDrain > 0f) "%.1f%%".format(bi.allTimeAvgDrain) else "--"
                tvRecentAvgDrain.text = if (bi.recentAvgDrain > 0f) "%.1f%%".format(bi.recentAvgDrain) else "—"
                // Drain trend: stable within ±0.5%, otherwise show direction
                val trendText  = when {
                    bi.drainTrend == 0f || (bi.drainTrend > -0.5f && bi.drainTrend < 0.5f) -> "Stable"
                    bi.drainTrend > 0f -> "+%.1f%%".format(bi.drainTrend)
                    else               -> "%.1f%%".format(bi.drainTrend)
                }
                val trendColor = when {
                    bi.drainTrend > 0.5f  -> Color.parseColor("#FF453A")  // increasing = bad
                    bi.drainTrend < -0.5f -> Color.parseColor("#30D158")  // decreasing = good
                    else                  -> Color.parseColor("#8E8E93")  // stable = neutral
                }
                tvDrainTrend.text = trendText
                tvDrainTrend.setTextColor(trendColor)
                tvSessionsPerCharge.text = if (bi.sessionsPerChargeCycle > 0f)
                    "%.1f".format(bi.sessionsPerChargeCycle) else "—"
                tvAvgChargeTime.text = when {
                    bi.avgChargeDurationMin <= 0 -> "—"
                    bi.avgChargeDurationMin >= 60 -> "${bi.avgChargeDurationMin / 60}h ${bi.avgChargeDurationMin % 60}m"
                    else -> "${bi.avgChargeDurationMin}m"
                }
                tvAvgGained.text = if (bi.avgBatteryGainedPct > 0f) "%.0f%%".format(bi.avgBatteryGainedPct) else "—"
                tvLongestRun.text = if (bi.longestRunSessions > 0) "${bi.longestRunSessions}" else "—"
                tvDrainStdDev.text = if (bi.drainStdDev > 0f) "±%.1f%%".format(bi.drainStdDev) else "—"
                tvAvgDod.text = if (bi.avgDepthOfDischarge > 0f) "%.0f%%".format(bi.avgDepthOfDischarge) else "—"
                tvEstDays.text = bi.avgDaysPerChargeCycle?.let { "%.1f".format(it) } ?: "—"
                tvMedianDrain.text = if (bi.medianDrain > 0f) "%.1f%%".format(bi.medianDrain) else "—"
            }
        }
    }
}

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm
        
        val swPhoneAlerts = view.findViewById<SwitchCompat>(R.id.switch_phone_alerts)
        val swHaptic = view.findViewById<SwitchCompat>(R.id.switch_vibration)
        val swCharge = view.findViewById<SwitchCompat>(R.id.switch_charge_opt)
        val swChargeLimit = view.findViewById<SwitchCompat>(R.id.switch_charge_limit)
        val swPermBle = view.findViewById<SwitchCompat>(R.id.switch_perm_ble)
        val swBoostTimeout = view.findViewById<SwitchCompat>(R.id.switch_boost_timeout)

        val tvUnit = view.findViewById<TextView>(R.id.tv_unit_value)
        val tvShutdown = view.findViewById<TextView>(R.id.tv_auto_shutdown_value)
        val sbBrightness = view.findViewById<SeekBar>(R.id.seek_brightness)

        val tvModel = view.findViewById<TextView>(R.id.tv_settings_model)
        val tvSerial = view.findViewById<TextView>(R.id.tv_settings_serial)
        val tvMac = view.findViewById<TextView>(R.id.tv_settings_mac)
        val tvFw = view.findViewById<TextView>(R.id.tv_settings_firmware)
        val tvColor = view.findViewById<TextView>(R.id.tv_settings_color)

        view.findViewById<View>(R.id.row_phone_alerts).setOnClickListener { vm.togglePhoneAlerts() }
        view.findViewById<View>(R.id.row_unit).setOnClickListener { vm.toggleUnit() }
        view.findViewById<View>(R.id.row_auto_shutdown).setOnClickListener { vm.adjustAutoShutdown(60) }
        view.findViewById<View>(R.id.row_vibration).setOnClickListener { vm.toggleVibrationLevel() }
        view.findViewById<View>(R.id.row_charge_opt).setOnClickListener { vm.toggleChargeCurrentOpt() }
        view.findViewById<View>(R.id.row_charge_limit).setOnClickListener { vm.toggleChargeVoltageLimit() }
        view.findViewById<View>(R.id.row_perm_ble).setOnClickListener { vm.togglePermanentBle() }
        view.findViewById<View>(R.id.row_boost_timeout).setOnClickListener { vm.toggleBoostTimeout() }
        view.findViewById<Button>(R.id.btn_find_device).setOnClickListener { vm.findDevice() }

        sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) vm.setBrightness(progress + 1)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            vm.phoneAlertsEnabled.collect { swPhoneAlerts.isChecked = it }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.latestStatus.collect { s ->
                if (s == null) return@collect
                swHaptic.isChecked = s.vibrationEnabled
                swCharge.isChecked = s.chargeCurrentOptimization
                swChargeLimit.isChecked = s.chargeVoltageLimit
                swPermBle.isChecked = s.permanentBluetooth
                tvUnit.text = if (s.isCelsius) "°C" else "°F"
                tvShutdown.text = "${s.autoShutdownSeconds / 60}m"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.displaySettings.collect { ds ->
                if (ds == null) return@collect
                sbBrightness.progress = (ds.brightness - 1).coerceIn(0, 8)
                swBoostTimeout.isChecked = ds.boostTimeout > 0
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.latestInfo.collect { i ->
                tvModel.text = "Model: ${i?.deviceType ?: "---"}"
                tvSerial.text = "Serial: ${i?.serialNumber ?: "---"}"
                tvMac.text = "Address: ${i?.deviceAddress ?: "---"}"
                tvColor.text = "Color Index: ${i?.colorIndex ?: "---"}"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.firmwareVersion.collect { f -> tvFw.text = "Firmware: ${f ?: "---"}" }
        }
    }
}

fun MainActivity.confirmDelete(summary: SessionSummary) {
    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        .setTitle("Delete Session")
        .setMessage("Remove this session?")
        .setPositiveButton("Delete") { _, _ -> vm.deleteSession(summary.session) }
        .setNegativeButton("Cancel", null)
        .show()
}

/** "Today", "Yesterday", or "Mar 5" */
fun relativeDate(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 24 * 3_600_000L -> "Today"
        diff < 48 * 3_600_000L -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

/** 0→"—", <60s→"Xs", whole minutes→"Xm", else→"Xm Ys" */
fun formatDurationShort(seconds: Long): String = when {
    seconds <= 0L  -> "—"
    seconds < 60L  -> "${seconds}s"
    seconds % 60L == 0L -> "${seconds / 60}m"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
