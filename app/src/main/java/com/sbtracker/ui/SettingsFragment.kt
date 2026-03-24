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
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import com.sbtracker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private val vm: MainViewModel by activityViewModels()
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

        binding.rowPhoneAlerts.setOnClickListener { vm.togglePhoneAlerts() }
        binding.rowDimOnCharge.setOnClickListener { vm.toggleDimOnCharge() }
        binding.rowDayStartHour.setOnClickListener {
            val hours = Array(24) { i -> if (i == 0) "12 AM" else if (i < 12) "$i AM" else if (i == 12) "12 PM" else "${i - 12} PM" }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Day Start Hour")
                .setItems(hours) { _, which -> vm.setDayStartHour(which) }
                .show()
        }
        binding.rowRetentionDays.setOnClickListener {
            val options = arrayOf("30 days", "60 days", "90 days", "180 days", "Never")
            val values  = intArrayOf(30, 60, 90, 180, Int.MAX_VALUE)
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Data Retention")
                .setItems(options) { _, which -> vm.setRetentionDays(values[which]) }
                .show()
        }
        binding.rowUnit.setOnClickListener { vm.toggleUnit() }
        binding.rowAutoShutdown.setOnClickListener { vm.adjustAutoShutdown(60) }
        binding.rowVibration.setOnClickListener { vm.toggleVibrationLevel() }
        val swBoostViz = binding.switchBoostViz
        binding.rowBoostViz.setOnClickListener { vm.toggleBoostVisualization() }
        binding.rowChargeOpt.setOnClickListener { vm.toggleChargeCurrentOpt() }
        binding.rowChargeLimit.setOnClickListener { vm.toggleChargeVoltageLimit() }
        binding.rowPermBle.setOnClickListener { vm.togglePermanentBle() }
        binding.rowBoostTimeout.setOnClickListener { vm.toggleBoostTimeout() }
        binding.btnFindDevice.setOnClickListener { vm.findDevice() }
        binding.btnFactoryReset.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Factory Reset Device")
                .setMessage("This will reset the device to factory defaults. Are you sure?")
                .setPositiveButton("Reset") { _, _ -> vm.factoryReset() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnDevRebuildHistory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                vm.rebuildSessionHistoryFromLogs()
            }
        }
        binding.btnDevInjectTestDevice.setOnClickListener {
            vm.injectTestDevice()
            android.widget.Toast.makeText(requireContext(), "Test Device Injected", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.btnDevRemoveTestDevice.setOnClickListener {
            vm.removeTestDevice()
            android.widget.Toast.makeText(requireContext(), "Test Device Removed", android.widget.Toast.LENGTH_SHORT).show()
        }

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
            vm.dimOnChargeEnabled.collect { swDimOnCharge.isChecked = it }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.dayStartHour.collect { hour ->
                val text = if (hour == 0) "12 AM" else if (hour < 12) "$hour AM" else if (hour == 12) "12 PM" else "${hour - 12} PM"
                tvDayStartValue.text = text
                tvDayStartSubtitle.text = "Day view begins at $text"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.retentionDays.collect { days ->
                tvRetentionValue.text = if (days == Int.MAX_VALUE) "Never" else "$days days"
            }
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
                swBoostViz.isChecked = s.boostVisualization
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
