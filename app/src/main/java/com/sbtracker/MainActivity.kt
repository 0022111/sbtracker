package com.sbtracker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.sbtracker.ble.BleService
import com.sbtracker.ui.AppViewModel
import com.sbtracker.ui.SBTrackerApp
import com.sbtracker.ui.SBTrackerTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result-agnostic — the user can retry connecting if denied */ }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            vm.onBind((binder as BleService.LocalBinder).service)
        }
        override fun onServiceDisconnected(name: ComponentName) { vm.onUnbind() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPerms()
        val intent = Intent(this, BleService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            SBTrackerTheme { SBTrackerApp(vm) }
        }
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }

    private fun requestPerms() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permRequest.launch(needed.toTypedArray())
    }
}
