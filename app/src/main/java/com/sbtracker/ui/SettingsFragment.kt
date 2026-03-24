package com.sbtracker.ui

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
    }
}
