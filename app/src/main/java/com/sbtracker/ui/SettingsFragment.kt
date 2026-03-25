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

        // Collect and display session programs
        viewLifecycleOwner.lifecycleScope.launch {
            settingsVm.programs.collect { programs ->
                updateProgramsList(programs)
            }
        }
    }

    private fun updateProgramsList(programs: List<com.sbtracker.data.SessionProgram>) {
        val container = binding.programsContainer
        container.removeAllViews()

        // Add each program as a row
        for ((index, program) in programs.withIndex()) {
            if (index > 0) {
                // Add divider between items
                val divider = View(requireContext()).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        marginStart = 16.dpToPx()
                        marginEnd = 16.dpToPx()
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#141E17"))
                }
                container.addView(divider)
            }

            // Program row: [name] [temp] [DELETE button if not default]
            val rowLayout = android.widget.LinearLayout(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    64.dpToPx()
                )
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A1A1A")),
                    null,
                    null
                )
            }

            // Program info column
            val infoLayout = android.widget.LinearLayout(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                orientation = android.widget.LinearLayout.VERTICAL
            }

            val nameView = android.widget.TextView(requireContext()).apply {
                text = program.name
                setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                textSize = 16f
            }
            infoLayout.addView(nameView)

            val tempView = android.widget.TextView(requireContext()).apply {
                text = "${program.targetTempC}°C"
                setTextColor(android.graphics.Color.parseColor("#80A88F"))
                textSize = 12f
            }
            infoLayout.addView(tempView)

            rowLayout.addView(infoLayout)

            // Delete button (only for non-default programs)
            if (!program.isDefault) {
                val deleteBtn = android.widget.Button(requireContext()).apply {
                    text = "DELETE"
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 16.dpToPx()
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#FF453A"))
                    setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                    textSize = 12f
                    setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                    setOnClickListener {
                        settingsVm.deleteProgram(program.id)
                        android.widget.Toast.makeText(requireContext(), "${program.name} deleted", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                rowLayout.addView(deleteBtn)
            }

            container.addView(rowLayout)
        }

        // Add "+ New Program" button
        val addBtnLayout = android.widget.LinearLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                64.dpToPx()
            )
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
        }

        val addBtn = android.widget.Button(requireContext()).apply {
            text = "+ New Program"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#00FF41"))
            setTextColor(android.graphics.Color.parseColor("#000000"))
            textSize = 14f
            setOnClickListener {
                showNewProgramDialog()
            }
        }
        addBtnLayout.addView(addBtn)
        container.addView(addBtnLayout)
    }

    private fun showNewProgramDialog() {
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "Program name (e.g., 'Morning Session')"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val tempInput = android.widget.EditText(requireContext()).apply {
            hint = "Target temperature (°C, 130-220)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val inputLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            addView(nameInput)
            addView(tempInput)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Create New Program")
            .setView(inputLayout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val tempStr = tempInput.text.toString().trim()
                if (name.isNotEmpty() && tempStr.isNotEmpty()) {
                    val temp = tempStr.toIntOrNull()
                    if (temp != null && temp in 130..220) {
                        // TODO: T-045 will implement the full create/edit dialog
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Program creation dialog (T-045)",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Invalid temperature",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Please fill in all fields",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
