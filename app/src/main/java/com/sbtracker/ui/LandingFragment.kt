package com.sbtracker.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import com.sbtracker.databinding.FragmentLandingBinding
import com.sbtracker.util.formatDurationShort
import com.sbtracker.util.relativeDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LandingFragment : Fragment() {
    private var _binding: FragmentLandingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLandingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val vm = activity.vm

        // Header
        val tvDeviceInfo = binding.tvCmdDeviceInfo

        // Hero
        val layoutOffline = binding.layoutCmdOffline
        val tvScanStatus = binding.tvCmdScanStatus
        val btnConnect = binding.btnCmdConnect
        val tvBtnConnectText = binding.tvBtnConnectText

        val layoutOnline = binding.layoutCmdOnline
        val tvLiveStatus = binding.tvCmdLiveStatus
        val btnDisconnect = binding.btnCmdDisconnect
        val tvLiveTemp = binding.tvCmdLiveTemp
        val tvLiveTarget = binding.tvCmdLiveTarget
        val cardHero = binding.cardCmdHero
        val btnHeater = binding.btnCmdHeater
        val tvBtnHeaterText = binding.tvBtnHeaterText

        // Tiles
        val tileSession = binding.tileSession
        val tvTileSessionVal = binding.tvTileSessionVal

        val tileBattery = binding.tileBattery
        val tvTileBatteryVal = binding.tvTileBatteryVal

        val tileAnalytics = binding.tileAnalytics
        val tvTileAnalyticsVal = binding.tvTileAnalyticsVal

        val tileSettings = binding.tileSettings
        val tvTileSettingsVal = binding.tvTileSettingsVal

        // Last Activity
        val cardLastSession = binding.cardCmdLastSession
        val tvLastDate = binding.tvCmdLastDate
        val tvLastSummary = binding.tvCmdLastSummary

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
                tvTileSettingsVal.text = "Configure"
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

                tvLiveTemp.text = s.currentTempC.toDisplayTemp(celsius).toString()
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
            combine(vm.sessionStats, vm.latestStatus, vm.connectionState) { ss, s, conn ->
                Triple(ss, s, conn)
            }.collect { (ss, s, conn) ->
                val isConnected = conn is BleManager.ConnectionState.Connected

                if (isConnected && s != null && s.heaterMode > 0) {
                    val sec = ss.durationSeconds
                    tvTileSessionVal.text = "%02d:%02d".format(sec / 60, sec % 60)
                    tvTileSessionVal.setTextColor(Color.parseColor("#FF9F0A"))
                } else {
                    tvTileSessionVal.text = "Ready"
                    tvTileSessionVal.setTextColor(Color.WHITE)
                }
            }
        }

        // Analytics tile (today count)
        viewLifecycleOwner.lifecycleScope.launch {
            vm.todaySummaries.collect { today ->
                tvTileAnalyticsVal.text = "${today.size} Today"
            }
        }

        // Last session (chronological across ALL devices)
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.lastSession, vm.knownDeviceBatteries) { last, snapshots -> last to snapshots }.collect { (lastSession, snapshots) ->
                if (lastSession != null) {
                    tvLastDate.text = relativeDate(lastSession.startTimeMs)
                    val deviceLabel = lastSession.serialNumber?.takeLast(6) ?: lastSession.deviceAddress.takeLast(5)
                    val prefix = if (snapshots.size > 1) "[$deviceLabel] " else ""
                    tvLastSummary.text = "${prefix}${formatDurationShort(lastSession.durationMs / 1000)} · ${lastSession.hitCount} hits"
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

        // Device battery snapshots (visible when 2+ devices known)
        val llDeviceStatusRow = binding.llDeviceStatusRow
        viewLifecycleOwner.lifecycleScope.launch {
            vm.knownDeviceBatteries.collect { snapshots ->
                if (snapshots.size < 2) {
                    llDeviceStatusRow.visibility = View.GONE
                    return@collect
                }
                llDeviceStatusRow.visibility = View.VISIBLE
                llDeviceStatusRow.removeAllViews()

                // Header
                val header = TextView(requireContext()).apply {
                    text = "DEVICES"
                    setTextColor(Color.parseColor("#636366"))
                    textSize = 13f
                    letterSpacing = 0.1f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 16)
                }
                llDeviceStatusRow.addView(header)

                for (snapshot in snapshots) {
                    val row = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(16, 12, 16, 12)
                    }
                    val nameLabel = TextView(requireContext()).apply {
                        text = snapshot.device.deviceType.ifEmpty { snapshot.device.serialNumber.takeLast(6) }
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val battLabel = TextView(requireContext()).apply {
                        val bat = snapshot.lastBattery
                        text = if (bat != null) "${bat}%" else "--%"
                        setTextColor(
                            if (bat != null && bat <= 20) Color.parseColor("#FF453A")
                            else Color.parseColor("#80A88F")
                        )
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    val seenLabel = TextView(requireContext()).apply {
                        val ms = snapshot.lastSeenMs
                        text = if (ms != null) " · ${relativeDate(ms)}" else ""
                        setTextColor(Color.parseColor("#636366"))
                        textSize = 12f
                    }
                    row.addView(nameLabel)
                    row.addView(battLabel)
                    row.addView(seenLabel)
                    llDeviceStatusRow.addView(row)
                }
            }
        }
    }
}
