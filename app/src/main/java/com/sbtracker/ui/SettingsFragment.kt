package com.sbtracker.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sbtracker.*
import com.sbtracker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private val bleVm: BleViewModel by activityViewModels()
    private val sessionVm: SessionViewModel by activityViewModels()
    private val settingsVm: SettingsViewModel by activityViewModels()
    private val historyVm: HistoryViewModel by activityViewModels()
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val swPhoneAlerts = binding.switchPhoneAlerts
        val swDimOnCharge = binding.switchDimOnCharge
        val swHaptic = binding.switchVibration
        val swCharge = binding.switchChargeOpt
        val swChargeLimit = binding.switchChargeLimit
        val swPermBle = binding.switchPermBle
        val swBoostTimeout = binding.switchBoostTimeout

        val tvUnit = binding.tvUnitValue
        val tvShutdown = binding.tvAutoShutdownValue
        val sbBrightness = binding.seekBrightness

        val tvModel = binding.tvSettingsModel
        val tvSerial = binding.tvSettingsSerial
        val tvMac = binding.tvSettingsMac
        val tvFw = binding.tvSettingsFirmware
        val tvColor = binding.tvSettingsColor

        val tvDayStartValue = binding.tvDayStartValue
        val tvDayStartSubtitle = binding.tvDayStartSubtitle
        val tvRetentionValue = binding.tvRetentionValue

        // ── Developer mode: tap firmware version 7× to unlock ──
        var devTapCount = 0
        tvFw.setOnClickListener {
            devTapCount++
            val remaining = 7 - devTapCount
            when {
                devTapCount < 7 -> android.widget.Toast.makeText(
                    requireContext(), "$remaining steps away from developer options", android.widget.Toast.LENGTH_SHORT
                ).show()
                devTapCount == 7 -> {
                    binding.layoutDevTools.visibility = View.VISIBLE
                    android.widget.Toast.makeText(requireContext(), "Developer mode enabled", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.rowPhoneAlerts.setOnClickListener { bleVm.togglePhoneAlerts() }

        // Show notification permission disabled indicator if needed (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            updateNotificationPermissionStatus()
        }

        binding.rowDimOnCharge.setOnClickListener { bleVm.toggleDimOnCharge() }
        binding.rowDayStartHour.setOnClickListener {
            val hours = Array(24) { i -> if (i == 0) "12 AM" else if (i < 12) "$i AM" else if (i == 12) "12 PM" else "${i - 12} PM" }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Day Start Hour")
                .setItems(hours) { _, which -> settingsVm.setDayStartHour(which) }
                .show()
        }
        binding.rowRetentionDays.setOnClickListener {
            val options = arrayOf("Delete after 30 days", "Delete after 60 days", "Delete after 90 days", "Delete after 180 days", "Never")
            val values  = intArrayOf(30, 60, 90, 180, Int.MAX_VALUE)
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Data Retention")
                .setItems(options) { _, which -> settingsVm.setRetentionDays(values[which]) }
                .show()
        }
        binding.rowUnit.setOnClickListener { bleVm.toggleUnit() }
        binding.rowAutoShutdown.setOnClickListener {
            val current = bleVm.latestStatus.value?.autoShutdownSeconds ?: 120
            sessionVm.setAutoShutdown(current + 60)
        }
        binding.rowVibration.setOnClickListener {
            val currentLevel = if (bleVm.latestStatus.value?.vibrationEnabled == true) 1 else 0
            sessionVm.toggleVibrationLevel(currentLevel)
        }
        val swBoostViz = binding.switchBoostViz
        binding.rowBoostViz.setOnClickListener {
            sessionVm.toggleBoostVisualization(
                bleVm.latestStatus.value?.boostVisualization ?: false,
                bleVm.latestInfo.value?.deviceType ?: ""
            )
        }
        binding.rowChargeOpt.setOnClickListener {
            sessionVm.toggleChargeCurrentOpt(bleVm.latestStatus.value?.chargeCurrentOptimization ?: false)
        }
        binding.rowChargeLimit.setOnClickListener {
            sessionVm.toggleChargeVoltageLimit(bleVm.latestStatus.value?.chargeVoltageLimit ?: false)
        }
        binding.rowPermBle.setOnClickListener {
            sessionVm.togglePermanentBle(bleVm.latestStatus.value?.permanentBluetooth ?: false)
        }
        binding.rowBoostTimeout.setOnClickListener {
            sessionVm.toggleBoostTimeout(bleVm.displaySettings.value?.boostTimeout ?: 0)
        }
        binding.btnFindDevice.setOnClickListener { bleVm.findDevice() }
        binding.btnFactoryReset.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Factory Reset Device")
                .setMessage("This will reset the device to factory defaults. Are you sure?")
                .setPositiveButton("Reset") { _, _ -> bleVm.factoryReset() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnClearDeviceHistory.setOnClickListener {
            val device = bleVm.activeDevice.value ?: return@setOnClickListener
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear Device History")
                .setMessage("This will permanently delete all sessions, charges, and raw logs for ${device.serialNumber}. This cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    historyVm.clearSessionHistory(device)
                    android.widget.Toast.makeText(requireContext(), "History cleared", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnBackupDatabase.setOnClickListener {
            settingsVm.triggerBackup()
        }

        binding.btnDevRebuildHistory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                historyVm.rebuildSessionHistoryFromLogs()
            }
        }
        binding.btnDevInjectTestDevice.setOnClickListener {
            bleVm.injectTestDevice()
            android.widget.Toast.makeText(requireContext(), "Test Device Injected", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.btnDevRemoveTestDevice.setOnClickListener {
            bleVm.removeTestDevice()
            android.widget.Toast.makeText(requireContext(), "Test Device Removed", android.widget.Toast.LENGTH_SHORT).show()
        }

        sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val level = progress + 1
                    sessionVm.setBrightness(level)
                    bleVm.updateDisplaySettingsLocally(level)
                    bleVm.onManualBrightnessChange(level)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.phoneAlertsEnabled.collect { swPhoneAlerts.isChecked = it }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.dimOnChargeEnabled.collect { swDimOnCharge.isChecked = it }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.dayStartHour.collect { hour ->
                val text = if (hour == 0) "12 AM" else if (hour < 12) "$hour AM" else if (hour == 12) "12 PM" else "${hour - 12} PM"
                tvDayStartValue.text = text
                tvDayStartSubtitle.text = "Day view begins at $text"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.retentionDays.collect { days ->
                tvRetentionValue.text = if (days == Int.MAX_VALUE) "Never" else "Delete after $days days"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.latestStatus.collect { s ->
                if (s == null) return@collect
                swHaptic.isChecked = s.vibrationEnabled
                swCharge.isChecked = s.chargeCurrentOptimization
                swChargeLimit.isChecked = s.chargeVoltageLimit
                swPermBle.isChecked = s.permanentBluetooth
                tvUnit.text = if (s.isCelsius) "°C" else "°F"
                tvShutdown.text = "${s.autoShutdownSeconds / 60}m"
                swBoostViz.isChecked = s.boostVisualization
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.displaySettings.collect { ds ->
                if (ds == null) return@collect
                sbBrightness.progress = (ds.brightness - 1).coerceIn(0, 8)
                swBoostTimeout.isChecked = ds.boostTimeout > 0
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.latestInfo.collect { i ->
                tvModel.text = "Model: ${i?.deviceType ?: "---"}"
                tvSerial.text = "Serial: ${i?.serialNumber ?: "---"}"
                tvMac.text = "Address: ${i?.deviceAddress ?: "---"}"
                tvColor.text = "Color Index: ${i?.colorIndex ?: "---"}"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bleVm.firmwareVersion.collect { f -> tvFw.text = "Firmware: ${f ?: "---"}" }
        }

        // ── Alert toggles ──────────────────────────────────────────────────────
        val swAlertTempReady  = binding.switchAlertTempReady
        val swAlertCharge80   = binding.switchAlertCharge80
        val swAlertSessionEnd = binding.switchAlertSessionEnd

        binding.rowAlertTempReady.setOnClickListener  { settingsVm.setAlertTempReady(!settingsVm.alertTempReady.value) }
        binding.rowAlertCharge80.setOnClickListener   { settingsVm.setAlertCharge80(!settingsVm.alertCharge80.value) }
        binding.rowAlertSessionEnd.setOnClickListener { settingsVm.setAlertSessionEnd(!settingsVm.alertSessionEnd.value) }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.alertTempReady.collect { swAlertTempReady.isChecked = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.alertCharge80.collect { swAlertCharge80.isChecked = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.alertSessionEnd.collect { swAlertSessionEnd.isChecked = it }
        }

        val tvDefaultPackType = binding.tvDefaultPackTypeValue
        val tvCapsuleWeight   = binding.tvCapsuleWeightValue

        // Observe and display current values
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsVm.defaultIsCapsule.collect { isCapsule ->
                        tvDefaultPackType.text = if (isCapsule) "Capsule" else "Free Pack"
                    }
                }
                launch {
                    settingsVm.capsuleWeightGrams.collect { grams ->
                        tvCapsuleWeight.text = "%.2f g".format(grams)
                    }
                }
            }
        }

        // Click: toggle pack type
        binding.rowDefaultPackType.setOnClickListener {
            settingsVm.setDefaultIsCapsule(!settingsVm.defaultIsCapsule.value)
        }

        // Click: edit capsule weight
        binding.rowCapsuleWeight.setOnClickListener {
            val input = android.widget.EditText(requireContext()).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText("%.2f".format(settingsVm.capsuleWeightGrams.value))
                selectAll()
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Capsule Weight (grams)")
                .setMessage("Enter weight in grams (0.01 – 2.00)")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val grams = input.text.toString().toFloatOrNull()
                    if (grams != null) settingsVm.setCapsuleWeight(grams)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Tolerance Break Goal ─────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.breakGoalDays.collect { days ->
                binding.tvBreakGoalDays.text = "$days ${if (days == 1) "day" else "days"}"
            }
        }
        binding.rowBreakGoal.setOnClickListener {
            val input = android.widget.EditText(requireContext()).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(settingsVm.breakGoalDays.value.toString())
                selectAll()
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Break Goal")
                .setMessage("Target days without a session (1–365)")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val days = input.text.toString().toIntOrNull()
                    if (days != null) settingsVm.setBreakGoalDays(days)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Temperature Presets ───────────────────────────────────────────────
        val llPresetList = binding.llPresetList
        binding.rowAddPreset.setOnClickListener {
            val nameInput = android.widget.EditText(requireContext()).apply {
                hint = "Preset name"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            val tempInput = android.widget.EditText(requireContext()).apply {
                hint = "Temperature (°C)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            val container = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 16, 48, 0)
                addView(nameInput)
                addView(tempInput)
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Temperature Preset")
                .setView(container)
                .setPositiveButton("Add") { _, _ ->
                    val name = nameInput.text.toString().trim()
                    val tempC = tempInput.text.toString().toIntOrNull()
                    if (name.isNotEmpty() && tempC != null) {
                        settingsVm.addTempPreset(name, tempC)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.tempPresets.collect { presets ->
                llPresetList.removeAllViews()
                presets.forEachIndexed { index, preset ->
                    val row = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(48, 0, 48, 0)
                        minimumHeight = 56.dpToPx()
                        val tv = android.util.TypedValue()
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                        if (tv.resourceId != 0) setBackgroundResource(tv.resourceId)
                    }
                    val tvName = android.widget.TextView(requireContext()).apply {
                        text = "${preset.name}  —  ${preset.tempC}°C"
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 15f
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(tvName)
                    row.setOnLongClickListener {
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("Delete Preset")
                            .setMessage("Remove \"${preset.name}\"?")
                            .setPositiveButton("Delete") { _, _ -> settingsVm.deleteTempPreset(index) }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    llPresetList.addView(row)
                }
            }
        }

        // (Removed vestigial programs collection - now managed in SessionFragment)
    }


    private fun updateNotificationPermissionStatus() {
        val isGranted = NotificationPermissionHelper.isGranted(requireContext())
        if (!isGranted) {
            // Find and update the subtitle TextView within the row
            val linearLayout = binding.rowPhoneAlerts.getChildAt(0) as? android.widget.LinearLayout
            if (linearLayout != null && linearLayout.childCount > 1) {
                val subtitleView = linearLayout.getChildAt(1) as? TextView
                if (subtitleView != null) {
                    subtitleView.text = "Notifications disabled — tap to enable in system settings"
                    subtitleView.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                }
            }
            // Override click listener to open notification settings
            binding.rowPhoneAlerts.setOnClickListener {
                openNotificationSettings()
            }
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            action = "android.settings.APP_NOTIFICATION_SETTINGS"
            putExtra("android.provider.extra.APP_PACKAGE", requireContext().packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                putExtra("android.provider.extra.CHANNEL_ID", NotificationChannels.ALERTS)
            }
        }
        startActivity(intent)
    }

    private fun Int.dpToPx(): Int = (this * requireContext().resources.displayMetrics.density).toInt()
}
