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
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.sbtracker.data.BackupRepository
import com.sbtracker.data.RestoreRepository
import com.sbtracker.data.RestoreResult
import com.sbtracker.data.UserPreferencesRepository
import com.sbtracker.databinding.ActivityMainPagedBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefsRepo: UserPreferencesRepository
    @Inject lateinit var backupRepo: BackupRepository
    @Inject lateinit var restoreRepo: RestoreRepository

    lateinit var bleVm: BleViewModel
    lateinit var historyVm: HistoryViewModel
    lateinit var batteryVm: BatteryViewModel
    lateinit var settingsVm: SettingsViewModel
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
            else Toast.makeText(this, "Permissions required for BLE", Toast.LENGTH_SHORT).show()
        }

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            bleVm.startScan()
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val pickRestoreFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    restoreRepo.restoreFrom(it)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // First-run onboarding check
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

        setupWebView()
        startAndBindBleService()
        checkPermissionsAndScan()

        // 1. Observe Backup URIs
        lifecycleScope.launch {
            backupRepo.backupUri.collect { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Save SBTracker Backup"))
            }
        }

        // 2. Observe Restore results
        lifecycleScope.launch {
            restoreRepo.restoreResult.collect { result ->
                when (result) {
                    is RestoreResult.Success -> {
                        Toast.makeText(this@MainActivity, "Restore successful! Restarting...", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@MainActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                    is RestoreResult.Failure -> {
                        Toast.makeText(this@MainActivity, "Restore failed: ${result.reason}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // 3. Observe Restore Trigger
        lifecycleScope.launch {
            bleVm.triggerRestorePicker.collect {
                pickRestoreFile.launch(arrayOf("*/*"))
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (!NotificationPermissionHelper.isGranted(this)) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { historyVm.rebuildSessionHistoryFromLogs() }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // AGGRESSIVE CACHE CLEANING
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        binding.webView.apply {
            clearCache(true)
            clearFormData()
            clearHistory()
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                
                // Disable caching
                cacheMode = WebSettings.LOAD_NO_CACHE
                
                // Allow local XHR/WebSockets
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                
                // Ensure viewport behaves
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            setBackgroundColor(android.graphics.Color.parseColor("#0a0a0a"))

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            
            // Add cache-buster timestamp to local URL
            val cacheBuster = System.currentTimeMillis()
            loadUrl("file:///android_asset/ui/index.html?v=$cacheBuster")
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

    private fun checkPermissionsAndScan() {
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
}
