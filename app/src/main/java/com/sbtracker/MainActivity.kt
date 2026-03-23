package com.sbtracker

import android.Manifest
import android.annotation.SuppressLint
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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val vm = activity.vm

        // Header
        val tvDeviceInfo = view.findViewById<TextView>(R.id.tv_cmd_device_info)

        // Hero
        val layoutOffline = view.findViewById<View>(R.id.layout_cmd_offline)
        val tvScanStatus = view.findViewById<TextView>(R.id.tv_cmd_scan_status)
        val btnConnect = view.findViewById<View>(R.id.btn_cmd_connect)
        val tvBtnConnectText = view.findViewById<TextView>(R.id.tv_btn_connect_text)

        val layoutOnline = view.findViewById<View>(R.id.layout_cmd_online)
        val tvLiveStatus = view.findViewById<TextView>(R.id.tv_cmd_live_status)
        val btnDisconnect = view.findViewById<View>(R.id.btn_cmd_disconnect)
        val tvLiveTemp = view.findViewById<TextView>(R.id.tv_cmd_live_temp)
        val tvLiveTarget = view.findViewById<TextView>(R.id.tv_cmd_live_target)
        val cardHero = view.findViewById<androidx.cardview.widget.CardView>(R.id.card_cmd_hero)
        val btnHeater = view.findViewById<View>(R.id.btn_cmd_heater)
        val tvBtnHeaterText = view.findViewById<TextView>(R.id.tv_btn_heater_text)

        // Tiles
        val tileSession = view.findViewById<View>(R.id.tile_session)
        val tvTileSessionVal = view.findViewById<TextView>(R.id.tv_tile_session_val)

        val tileBattery = view.findViewById<View>(R.id.tile_battery)
        val tvTileBatteryVal = view.findViewById<TextView>(R.id.tv_tile_battery_val)

        val tileAnalytics = view.findViewById<View>(R.id.tile_analytics)
        val tvTileAnalyticsVal = view.findViewById<TextView>(R.id.tv_tile_analytics_val)

        val tileSettings = view.findViewById<View>(R.id.tile_settings)
        val tvTileSettingsVal = view.findViewById<TextView>(R.id.tv_tile_settings_val)

        // Last Activity
        val cardLastSession = view.findViewById<View>(R.id.card_cmd_last_session)
        val tvLastDate = view.findViewById<TextView>(R.id.tv_cmd_last_date)
        val tvLastSummary = view.findViewById<TextView>(R.id.tv_cmd_last_summary)

        // ── Navigation ──
        tileSession.setOnClickListener { activity.navigateTo(1) }
        tileBattery.setOnClickListener { activity.navigateTo(3) }
        tileAnalytics.setOnClickListener { activity.navigateTo(2) }
        tileSettings.setOnClickListener { activity.navigateTo(4) }
        cardLastSession.setOnClickListener { activity.navigateTo(2) }

        // ── Connect / Disconnect / Power actions ──
        val scanToggle = {
            if (vm.connectionState.value is BleManager.ConnectionState.Disconnected) {
                activity.checkPermissionsAndScan()
            } else {
                vm.disconnect()
            }
        }
        btnConnect.setOnClickListener { scanToggle() }
        btnDisconnect.setOnClickListener { scanToggle() }

        btnHeater.setOnClickListener {
            val isOn = (vm.latestStatus.value?.heaterMode ?: 0) > 0
            vm.setHeater(!isOn)
        }

        // ── UI Updates ──
        
        // Device Info (Header) & Settings Tile
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestInfo, vm.activeDevice) { info, device -> info to device }.collect { (info, device) ->
                tvDeviceInfo.text = when {
                    info != null -> "${info.deviceType} · ${info.serialNumber}"
                    device != null -> "${device.deviceType} · ${device.serialNumber} (Offline)"
                    else -> "No Device Found"
                }
                tvTileSettingsVal.text = if (info != null) "FW ${info.firmwareVersion}" else "Configure"
            }
        }

        // Connection State logic (Hero Offline mode)
        viewLifecycleOwner.lifecycleScope.launch {
            vm.connectionState.collect { state ->
                when (state) {
                    is BleManager.ConnectionState.Disconnected -> {
                        tvScanStatus.text = "Tap to search for your device"
                        tvBtnConnectText.text = "Search Devices"
                        tvBtnConnectText.setTextColor(Color.parseColor("#0A84FF"))
                        layoutOffline.visibility = View.VISIBLE
                        layoutOnline.visibility = View.GONE
                        cardHero.setCardBackgroundColor(Color.parseColor("#111113"))
                    }
                    is BleManager.ConnectionState.Scanning -> {
                        tvScanStatus.text = "Looking for devices..."
                        tvBtnConnectText.text = "Cancel Search"
                        tvBtnConnectText.setTextColor(Color.parseColor("#FF453A"))
                    }
                    is BleManager.ConnectionState.Connecting -> {
                        tvScanStatus.text = "Connecting to device..."
                        tvBtnConnectText.text = "Cancel Connect"
                        tvBtnConnectText.setTextColor(Color.parseColor("#FF453A"))
                    }
                    is BleManager.ConnectionState.Connected -> {}
                }
            }
        }

        // Device picker
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

        // Hero online state + Live Session + Battery Tile
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.latestStatus, vm.connectionState, vm.isCelsius) { s, conn, celsius ->
                Triple(s, conn, celsius)
            }.collect { (s, conn, celsius) ->
                if (conn !is BleManager.ConnectionState.Connected || s == null) return@collect

                layoutOffline.visibility = View.GONE
                layoutOnline.visibility = View.VISIBLE
                
                tvLiveTemp.text = s.currentTempC.toDisplayTemp(celsius)
                tvLiveTarget.text = "/ ${s.targetTempC.toDisplayTemp(celsius)}${celsius.unitSuffix()}"
                
                val isOn = s.heaterMode > 0
                if (isOn) {
                    tvBtnHeaterText.text = "Stop Heater"
                    tvBtnHeaterText.setTextColor(Color.parseColor("#FF453A"))
                    btnHeater.setBackgroundResource(R.drawable.bg_badge_red)
                    
                    if (!s.setpointReached) {
                        tvLiveStatus.text = "HEATING"
                        tvLiveStatus.setTextColor(Color.parseColor("#FF9F0A"))
                        cardHero.setCardBackgroundColor(Color.parseColor("#261905")) // subtle orange tint
                    } else {
                        tvLiveStatus.text = "READY"
                        tvLiveStatus.setTextColor(Color.parseColor("#30D158"))
                        cardHero.setCardBackgroundColor(Color.parseColor("#091F0D")) // subtle green tint
                    }
                } else {
                    tvBtnHeaterText.text = "Start Heater"
                    tvBtnHeaterText.setTextColor(Color.parseColor("#30D158"))
                    btnHeater.setBackgroundResource(R.drawable.bg_badge_green)
                    
                    tvLiveStatus.text = "IDLE"
                    tvLiveStatus.setTextColor(Color.parseColor("#636366"))
                    cardHero.setCardBackgroundColor(Color.parseColor("#111113"))
                }
                
                tvTileBatteryVal.text = "${s.batteryLevel}%"
                tvTileBatteryVal.setTextColor(if (s.batteryLevel <= 20) Color.parseColor("#FF453A") else Color.parseColor("#30D158"))
            }
        }

        // Live Session duration tick
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val s = vm.latestStatus.value
                val start = vm.currentSessionStartMs.value
                val isConnected = vm.connectionState.value is BleManager.ConnectionState.Connected
                
                if (isConnected && s != null && s.heaterMode > 0 && start != null) {
                    val activeDur = System.currentTimeMillis() - start
                    tvTileSessionVal.text = formatDurationShort(activeDur / 1000)
                    tvTileSessionVal.setTextColor(Color.parseColor("#FF9F0A"))
                } else {
                    tvTileSessionVal.text = "Ready"
                    tvTileSessionVal.setTextColor(Color.WHITE)
                }
                delay(1000)
            }
        }

        // Analytics & Recent
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.sessionSummaries, vm.todaySummaries) { all, today -> all to today }.collect { (all, today) ->
                tvTileAnalyticsVal.text = "${today.size} Today"
                
                val lastSession = all.firstOrNull()
                if (lastSession != null) {
                    tvLastDate.text = relativeDate(lastSession.startTimeMs)
                    tvLastSummary.text = "${formatDurationShort(lastSession.durationMs / 1000)} · ${lastSession.hitCount} hits"
                } else {
                    tvLastDate.text = "No sessions yet"
                    tvLastSummary.text = "—"
                }

                // Battery offline fallback
                if (vm.latestStatus.value == null) {
                    val lastBat = lastSession?.endBattery
                    if (lastBat != null && lastBat > 0) {
                        tvTileBatteryVal.text = "${lastBat}%"
                        tvTileBatteryVal.setTextColor(if (lastBat <= 20) Color.parseColor("#FF453A") else Color.WHITE)
                    } else {
                        tvTileBatteryVal.text = "--%"
                        tvTileBatteryVal.setTextColor(Color.WHITE)
                    }
                }
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

        // ── TIER 1: Hero — Timeline + Quick Stats ─────────────────────────────
        val timeline       = view.findViewById<HistoryTimelineView>(R.id.analytics_timeline)
        val tvHeroSessions = view.findViewById<TextView>(R.id.tv_hero_sessions)
        val tvHeroAvgDur   = view.findViewById<TextView>(R.id.tv_hero_avg_duration)
        val tvHeroAvgHits  = view.findViewById<TextView>(R.id.tv_hero_avg_hits)
        val tvHeroAvgDrain = view.findViewById<TextView>(R.id.tv_hero_avg_drain)
        val tvHeroHeatUp   = view.findViewById<TextView>(R.id.tv_hero_avg_heatup)

        // ── TIER 2: Charts & Trends ───────────────────────────────────────────
        val barChart             = view.findViewById<HistoryBarChartView>(R.id.history_bar_chart)
        val tvWeekSessions       = view.findViewById<TextView>(R.id.tv_week_sessions)
        val tvWeekSessionsDelta  = view.findViewById<TextView>(R.id.tv_week_sessions_delta)
        val tvWeekHits           = view.findViewById<TextView>(R.id.tv_week_hits)
        val tvWeekHitsDelta      = view.findViewById<TextView>(R.id.tv_week_hits_delta)
        val tvStreakCurrent       = view.findViewById<TextView>(R.id.tv_streak_current)
        val tvStreakLongest       = view.findViewById<TextView>(R.id.tv_streak_longest)
        val tvPeakTime           = view.findViewById<TextView>(R.id.tv_peak_time)
        val tvBusiestDay         = view.findViewById<TextView>(R.id.tv_busiest_day)

        // ── TIER 3: Deep Dive (expandable) ────────────────────────────────────
        val headerAverages    = view.findViewById<View>(R.id.header_session_averages)
        val contentAverages   = view.findViewById<View>(R.id.content_session_averages)
        val tvExpandAverages  = view.findViewById<TextView>(R.id.tv_expand_averages)
        val headerInsights    = view.findViewById<View>(R.id.header_usage_insights)
        val contentInsights   = view.findViewById<View>(R.id.content_usage_insights)
        val tvExpandInsights  = view.findViewById<TextView>(R.id.tv_expand_insights)

        val tvStatsAvgDuration   = view.findViewById<TextView>(R.id.tv_stats_avg_duration)
        val tvStatsMedianDuration = view.findViewById<TextView>(R.id.tv_stats_median_duration)
        val tvStatsAvgHits       = view.findViewById<TextView>(R.id.tv_stats_avg_hits)
        val tvStatsAvgHitLen     = view.findViewById<TextView>(R.id.tv_stats_avg_hit_len)
        val tvStatsAvgDrain      = view.findViewById<TextView>(R.id.tv_stats_avg_drain)
        val tvStatsSessPerDay7d  = view.findViewById<TextView>(R.id.tv_stats_sess_per_day_7d)
        val tvStatsSessPerDay30d = view.findViewById<TextView>(R.id.tv_stats_sess_per_day_30d)
        val tvStatsPeakInDay     = view.findViewById<TextView>(R.id.tv_stats_peak_in_day)
        val tvStatsFavTemps      = view.findViewById<TextView>(R.id.tv_stats_fav_temps)

        val tvHitsPerMin        = view.findViewById<TextView>(R.id.tv_hits_per_minute)
        val tvAvgSessionsPerDay = view.findViewById<TextView>(R.id.tv_avg_sessions_per_day)
        val tvTotalDaysActive   = view.findViewById<TextView>(R.id.tv_total_days_active)
        val tvAvgHeatUp         = view.findViewById<TextView>(R.id.tv_avg_heat_up)

        // ── Session list + controls ───────────────────────────────────────────
        val rv           = view.findViewById<RecyclerView>(R.id.rv_history)
        val tvCount      = view.findViewById<TextView>(R.id.tv_history_count)
        val llDeviceFilter = view.findViewById<android.widget.LinearLayout>(R.id.ll_device_filter)
        val tvSortDate     = view.findViewById<TextView>(R.id.tv_sort_date)
        val tvSortHits     = view.findViewById<TextView>(R.id.tv_sort_hits)
        val tvSortDuration = view.findViewById<TextView>(R.id.tv_sort_duration)
        val tvSortDrain    = view.findViewById<TextView>(R.id.tv_sort_drain)
        val tvSortTemp     = view.findViewById<TextView>(R.id.tv_sort_temp)

        // Period toggle
        val tvPeriodDay  = view.findViewById<TextView>(R.id.tv_graph_period_day)
        val tvPeriodWeek = view.findViewById<TextView>(R.id.tv_graph_period_week)

        // ── Expand/Collapse logic ─────────────────────────────────────────────

        fun toggleSection(content: View, chevron: TextView) {
            val parent = view.findViewById<View>(R.id.analytics_root) as? ViewGroup
            if (parent != null) {
                androidx.transition.TransitionManager.beginDelayedTransition(parent,
                    androidx.transition.AutoTransition().setDuration(200))
            }
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                chevron.text = "▼"
            } else {
                content.visibility = View.GONE
                chevron.text = "▶"
            }
        }

        headerAverages.setOnClickListener { toggleSection(contentAverages, tvExpandAverages) }
        headerInsights.setOnClickListener { toggleSection(contentInsights, tvExpandInsights) }

        // ── RecyclerView setup ────────────────────────────────────────────────

        val adapter = SessionHistoryAdapter(
            onSessionClick = { openSessionReport(it) },
            onDeleteClick = { activity.confirmDelete(it) }
        )
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        // ── Sort bar ──────────────────────────────────────────────────────────

        tvSortDate.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.DATE) }
        tvSortHits.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.HITS) }
        tvSortDuration.setOnClickListener { vm.setSessionSort(MainViewModel.SessionSort.DURATION) }
        tvSortDrain.setOnClickListener    { vm.setSessionSort(MainViewModel.SessionSort.DRAIN) }
        tvSortTemp.setOnClickListener     { vm.setSessionSort(MainViewModel.SessionSort.TEMP) }

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

        // ── Period toggle ─────────────────────────────────────────────────────

        tvPeriodDay.setOnClickListener  { vm.setGraphPeriod(MainViewModel.GraphPeriod.DAY) }
        tvPeriodWeek.setOnClickListener { vm.setGraphPeriod(MainViewModel.GraphPeriod.WEEK) }

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

        // ── Device filter chips ───────────────────────────────────────────────

        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.knownDevices, vm.sessionFilter) { devices, filter ->
                devices to filter
            }.collect { (devices, filter) ->
                buildDeviceFilterChips(devices, filter, llDeviceFilter, vm)
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // DATA COLLECTORS
        // ══════════════════════════════════════════════════════════════════════

        // ── Timeline (battery % over time with session markers) ───────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                vm.graphStatuses,
                vm.graphWindowStartMs,
                vm.rawSessionHistory,
                vm.graphPeriod
            ) { statuses, windowStart, sessions, period ->
                val windowEnd = System.currentTimeMillis()
                val timelinePeriod = if (period == MainViewModel.GraphPeriod.WEEK)
                    HistoryTimelineView.Period.WEEK else HistoryTimelineView.Period.DAY
                timeline.setData(statuses, sessions, windowStart, windowEnd, timelinePeriod)
            }.collect { }
        }

        // ── Timeline tap → open session report ───────────────────────────────
        timeline.onSessionTapped = { session ->
            viewLifecycleOwner.lifecycleScope.launch {
                val summary = vm.analyticsRepo.getSessionSummary(session)
                openSessionReport(summary)
            }
        }

        // ── Hero Quick Stats + Averages deep-dive ────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.historyStats, vm.isCelsius) { stats, celsius -> stats to celsius }.collect { (stats, celsius) ->
                // HERO
                tvHeroSessions.text = stats.sessionCount.toString()
                tvHeroAvgDur.text = formatDurationShort(stats.avgSessionDurationSec)
                tvHeroAvgHits.text = if (stats.avgHitsPerSession <= 0f) "—"
                    else if (stats.avgHitsPerSession == stats.avgHitsPerSession.toLong().toFloat()) stats.avgHitsPerSession.toInt().toString()
                    else "%.1f".format(stats.avgHitsPerSession)
                tvHeroAvgDrain.text = if (stats.avgBatteryDrainPct <= 0f) "—"
                    else "%.0f%%".format(stats.avgBatteryDrainPct)
                tvHeroHeatUp.text = if (stats.avgHeatUpTimeSec > 0) "${stats.avgHeatUpTimeSec}s" else "—"

                // DEEP DIVE: Session Averages
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

        // ── Usage Insights (weekly comparison, streaks, patterns) ─────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.usageInsights, vm.historyStats) { ui, hs -> ui to hs }.collect { (ui, hs) ->
                // Streaks
                tvStreakCurrent.text = if (ui.currentStreakDays > 0) "${ui.currentStreakDays}d" else "—"
                tvStreakLongest.text = if (ui.longestStreakDays > 0) "${ui.longestStreakDays}d" else "—"

                // Peak time & busiest day
                val todLabels = listOf("Night", "Morning", "Afternoon", "Evening")
                tvPeakTime.text = if (ui.peakTimeOfDay >= 0) todLabels[ui.peakTimeOfDay] else "—"
                val dowLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                tvBusiestDay.text = if (ui.busiestDayOfWeek >= 0) dowLabels[ui.busiestDayOfWeek] else "—"

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

                // DEEP DIVE: Usage Insights
                tvAvgHeatUp.text = if (hs.avgHeatUpTimeSec > 0) "${hs.avgHeatUpTimeSec}s" else "—"
                tvHitsPerMin.text = if (ui.avgHitsPerMinute > 0f) "%.2f".format(ui.avgHitsPerMinute) else "—"
                tvAvgSessionsPerDay.text = if (ui.avgSessionsPerDay > 0f) "%.1f".format(ui.avgSessionsPerDay) else "—"
                tvTotalDaysActive.text   = if (ui.totalDaysActive > 0) ui.totalDaysActive.toString() else "—"
            }
        }

        // ── Bar chart (sessions + charges) ────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.dailyStats, vm.rawChargeHistory, vm.graphPeriod) { daily, charges, period ->
                Triple(daily, charges, period)
            }.collect { (daily, charges, period) ->
                val chartPeriod = if (period == MainViewModel.GraphPeriod.WEEK)
                    HistoryBarChartView.Period.WEEK else HistoryBarChartView.Period.DAY
                barChart.setData(daily, charges, chartPeriod)
            }
        }

        // ── Session History List ──────────────────────────────────────────────
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

        // ── Clear All (with confirmation) ─────────────────────────────────────
        view.findViewById<TextView>(R.id.tv_history_clear).setOnClickListener {
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Clear All History")
                .setMessage("This will permanently delete all sessions, hits, charge cycles, and device status logs for the current device.\n\nThis cannot be undone.")
                .setPositiveButton("Delete Everything") { _, _ -> vm.clearSessionHistory() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Export ────────────────────────────────────────────────────────────
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

        val mineLabel = vm.activeDevice.value?.deviceType?.ifEmpty { "Device" } ?: "Mine"
        container.addView(makeChip(mineLabel, activeFilter == null) {
            vm.setSessionFilter(null)
        })

        if (devices.size > 1) {
            container.addView(makeChip("All", activeFilter == "all") {
                vm.setSessionFilter("all")
            })
        }

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

        // Hero state containers
        val heroIdle = view.findViewById<View>(R.id.hero_idle)
        val heroCharging = view.findViewById<View>(R.id.hero_charging)

        // Hero Idle Views
        val tvHeroSessionsLeft = view.findViewById<TextView>(R.id.tv_hero_sessions_left)
        val tvHeroIdleSubtext = view.findViewById<TextView>(R.id.tv_hero_idle_subtext)

        // Hero Charging Views
        val tvHeroEta80 = view.findViewById<TextView>(R.id.tv_hero_eta_80)
        val tvHeroEtaFull = view.findViewById<TextView>(R.id.tv_hero_eta_full)
        val tvHeroChargeRate = view.findViewById<TextView>(R.id.tv_hero_charge_rate)

        // Status row
        val tvPercent = view.findViewById<TextView>(R.id.batt_tv_percent)
        val tvStatus = view.findViewById<TextView>(R.id.batt_tv_status)

        // Graph
        val graph = view.findViewById<BatteryGraphView>(R.id.batt_graph)
        val tvBattPeriodDay = view.findViewById<TextView>(R.id.batt_graph_period_day)
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
                val eta80Min = stats.chargeEta80Minutes
                val charging = latest?.isCharging == true
                val etaMs = if (charging && etaMin != null && etaMin > 0) etaMin * 60_000L else 0L
                val eta80Ms = if (charging && eta80Min != null && eta80Min > 0) eta80Min * 60_000L else 0L
                val projLevel = if (etaMs > 0L && latest != null) latest.batteryLevel else null
                graph.setData(statuses, windowStart, etaMs, eta80Ms, projLevel)
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

        // Expandable Cards
        val contentDrain = view.findViewById<View>(R.id.content_drain_analysis)
        val headerDrain = view.findViewById<View>(R.id.header_drain_analysis)
        val tvExpandDrain = view.findViewById<TextView>(R.id.tv_expand_drain)
        var drainExpanded = true

        headerDrain.setOnClickListener {
            drainExpanded = !drainExpanded
            contentDrain.visibility = if (drainExpanded) View.VISIBLE else View.GONE
            tvExpandDrain.text = if (drainExpanded) "▼" else "◀"
        }

        val contentHealth = view.findViewById<View>(R.id.content_charge_health)
        val headerHealth = view.findViewById<View>(R.id.header_charge_health)
        val tvExpandHealth = view.findViewById<TextView>(R.id.tv_expand_health)
        var healthExpanded = false
        contentHealth.visibility = View.GONE
        tvExpandHealth.text = "◀"

        headerHealth.setOnClickListener {
            healthExpanded = !healthExpanded
            contentHealth.visibility = if (healthExpanded) View.VISIBLE else View.GONE
            tvExpandHealth.text = if (healthExpanded) "▼" else "◀"
        }

        // Data bindings for Tier 3 Cards
        val tvAvgDrainAll = view.findViewById<TextView>(R.id.tv_stats_avg_drain_all)
        val tvAvgDrainRecent = view.findViewById<TextView>(R.id.tv_stats_avg_drain_recent)
        val tvDrainTrend = view.findViewById<TextView>(R.id.tv_stats_drain_trend)
        val tvDrainMedian = view.findViewById<TextView>(R.id.tv_stats_drain_median)
        val tvDrainStdDev = view.findViewById<TextView>(R.id.tv_stats_drain_std_dev)
        val tvSessionsPerCharge = view.findViewById<TextView>(R.id.tv_stats_sessions_per_charge)

        val tvAvgChargeTime = view.findViewById<TextView>(R.id.tv_stats_avg_charge_time)
        val tvAvgPctGained = view.findViewById<TextView>(R.id.tv_stats_avg_pct_gained)
        val tvAvgDod = view.findViewById<TextView>(R.id.tv_stats_avg_dod)
        val tvDaysPerCycle = view.findViewById<TextView>(R.id.tv_stats_days_per_cycle)
        val tvLongestRun = view.findViewById<TextView>(R.id.tv_stats_longest_run)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.latestStatus.collect { s ->
                if (s == null) {
                    heroIdle.visibility = View.VISIBLE
                    heroCharging.visibility = View.GONE
                    tvPercent.text = "--"
                    tvStatus.text = "OFFLINE"
                    tvStatus.setTextColor(Color.parseColor("#636366"))
                    return@collect
                }
                
                heroIdle.visibility = if (s.isCharging) View.GONE else View.VISIBLE
                heroCharging.visibility = if (s.isCharging) View.VISIBLE else View.GONE

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
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.sessionStats.collect { ss ->
                // Hero Idle Data
                tvHeroSessionsLeft.text = if (ss.sessionsRemaining > 0) "${ss.sessionsRemaining}" else "--"
                if (ss.drainSampleCount >= 5 && ss.sessionsRemainingLow != ss.sessionsRemainingHigh) {
                    tvHeroIdleSubtext.text = "Based on avg drain (Range: ${ss.sessionsRemainingLow} to ${ss.sessionsRemainingHigh})"
                } else {
                    tvHeroIdleSubtext.text = "Based on average drain"
                }

                // Hero Charging Data
                tvHeroEta80.text = ss.chargeEta80Minutes?.let { "$it" } ?: "--"
                tvHeroEtaFull.text = ss.chargeEtaMinutes?.let { "${it}m" } ?: "--"
                tvHeroChargeRate.text = if (ss.chargeRatePctPerMin > 0) "%.1f%%/m".format(ss.chargeRatePctPerMin) else "--"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.batteryInsights.collect { bi ->
                tvAvgDrainAll.text = if (bi.allTimeAvgDrain > 0f) "%.1f%%".format(bi.allTimeAvgDrain) else "—"
                tvAvgDrainRecent.text = if (bi.recentAvgDrain > 0f) "%.1f%%".format(bi.recentAvgDrain) else "—"
                
                val trendText  = when {
                    bi.drainTrend == 0f || (bi.drainTrend > -0.5f && bi.drainTrend < 0.5f) -> "Stable"
                    bi.drainTrend > 0f -> "+%.1f%%".format(bi.drainTrend)
                    else               -> "%.1f%%".format(bi.drainTrend)
                }
                val trendColor = when {
                    bi.drainTrend > 0.5f  -> Color.parseColor("#FF453A")
                    bi.drainTrend < -0.5f -> Color.parseColor("#30D158")
                    else                  -> Color.parseColor("#8E8E93")
                }
                tvDrainTrend.text = trendText
                tvDrainTrend.setTextColor(trendColor)
                
                tvDrainMedian.text = if (bi.medianDrain > 0f) "%.1f%%".format(bi.medianDrain) else "—"
                tvDrainStdDev.text = if (bi.drainStdDev > 0f) "±%.1f%%".format(bi.drainStdDev) else "—"
                tvSessionsPerCharge.text = if (bi.sessionsPerChargeCycle > 0f) "%.1f".format(bi.sessionsPerChargeCycle) else "—"

                tvAvgChargeTime.text = when {
                    bi.avgChargeDurationMin <= 0 -> "—"
                    bi.avgChargeDurationMin >= 60 -> "${bi.avgChargeDurationMin / 60}h ${bi.avgChargeDurationMin % 60}m"
                    else -> "${bi.avgChargeDurationMin}m"
                }
                tvAvgPctGained.text = if (bi.avgBatteryGainedPct > 0f) "%.0f%%".format(bi.avgBatteryGainedPct) else "—"
                tvAvgDod.text = if (bi.avgDepthOfDischarge > 0f) "%.0f%%".format(bi.avgDepthOfDischarge) else "—"
                tvDaysPerCycle.text = bi.avgDaysPerChargeCycle?.let { "%.1f".format(it) } ?: "—"
                tvLongestRun.text = if (bi.longestRunSessions > 0) "${bi.longestRunSessions}" else "—"
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
