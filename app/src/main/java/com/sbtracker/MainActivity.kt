package com.sbtracker

import dagger.hilt.android.AndroidEntryPoint

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.sbtracker.data.SessionSummary
import com.sbtracker.databinding.ActivityMainPagedBinding
import com.sbtracker.ui.LandingFragment
import com.sbtracker.ui.SessionFragment
import com.sbtracker.ui.HistoryFragment
import com.sbtracker.ui.BatteryFragment
import com.sbtracker.ui.SettingsFragment
import com.sbtracker.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @javax.inject.Inject lateinit var prefsRepo: UserPreferencesRepository

    lateinit var bleVm: BleViewModel
    lateinit var historyVm: HistoryViewModel
    lateinit var batteryVm: BatteryViewModel
    lateinit var settingsVm: SettingsViewModel
    private lateinit var navVm: NavigationViewModel
    private lateinit var binding: ActivityMainPagedBinding

    private var bleService: BleService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            bleService?.initialize(bleVm)
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
            bleVm.startScan()
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // Permission granted or denied; allow app to continue
            // Notification attempts will be gated by NotificationPermissionHelper.isGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // First-run onboarding check (reads DataStore; fast on subsequent runs)
        val onboardingDone = runBlocking { prefsRepo.userPreferencesFlow.first().onboardingComplete }
        if (!onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainPagedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleVm = ViewModelProvider(this)[BleViewModel::class.java]
        historyVm = ViewModelProvider(this)[HistoryViewModel::class.java]
        batteryVm = ViewModelProvider(this)[BatteryViewModel::class.java]
        settingsVm = ViewModelProvider(this)[SettingsViewModel::class.java]
        navVm = ViewModelProvider(this)[NavigationViewModel::class.java]

        // Cross-VM state sync: activeDevice
        lifecycleScope.launch {
            bleVm.activeDevice.collect { device ->
                historyVm.updateActiveDevice(device)
                batteryVm.updateActiveDevice(device)
            }
        }

        // Cross-VM state sync: dayStartHour
        lifecycleScope.launch {
            settingsVm.dayStartHour.collect { hour ->
                historyVm.updateDayStartHour(hour)
                batteryVm.updateDayStartHour(hour)
            }
        }

        // Refresh intake stats when active device or capsule settings change
        lifecycleScope.launch {
            bleVm.activeDevice.collect {
                historyVm.refreshIntakeStats(
                    settingsVm.capsuleWeightGrams.value,
                    settingsVm.defaultIsCapsule.value
                )
            }
        }

        // Backcompile sessions if needed
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { historyVm.rebuildSessionHistoryFromLogs() }
            }
        }

        // Request POST_NOTIFICATIONS permission on first run (API 33+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (!NotificationPermissionHelper.isGranted(this)) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val adapter = PagedAdapter(this)
        binding.viewPager.adapter = adapter
        (binding.viewPager.getChildAt(0) as? RecyclerView)?.isNestedScrollingEnabled = false
        binding.viewPager.isUserInputEnabled = true

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_overview -> binding.viewPager.currentItem = 0
                R.id.nav_session  -> binding.viewPager.currentItem = 1
                R.id.nav_history  -> binding.viewPager.currentItem = 2
                R.id.nav_battery  -> binding.viewPager.currentItem = 3
                R.id.nav_settings -> binding.viewPager.currentItem = 4
            }
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavigation.menu.getItem(position).isChecked = true
            }
        })

        // Navigation events from fragments
        lifecycleScope.launch {
            navVm.navigateTo.collect { tab -> navigateTo(tab) }
        }
        lifecycleScope.launch {
            navVm.requestScan.collect { checkPermissionsAndScan() }
        }

        // Auto-navigate to session tab when heater starts
        lifecycleScope.launch {
            bleVm.latestStatus.collect { s ->
                if (s != null && s.heaterMode > 0 && binding.viewPager.currentItem == 0) {
                    binding.viewPager.currentItem = 1
                }
            }
        }

        startAndBindBleService()

        // Handle CSV export
        lifecycleScope.launch {
            historyVm.exportUri.collect { uri ->
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
            bleVm.startScan()
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
        binding.viewPager.currentItem = tab
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

fun MainActivity.confirmDelete(summary: SessionSummary) {
    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        .setTitle("Delete Session")
        .setMessage("Remove this session?")
        .setPositiveButton("Delete") { _, _ -> historyVm.deleteSession(summary.session) }
        .setNegativeButton("Cancel", null)
        .show()
}
