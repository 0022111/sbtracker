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
import androidx.lifecycle.lifecycleScope
import com.sbtracker.*
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).vm
        
        val swPhoneAlerts = view.findViewById<SwitchCompat>(R.id.switch_phone_alerts)
        val swDimOnCharge = view.findViewById<SwitchCompat>(R.id.switch_dim_on_charge)
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

        val tvDayStartValue = view.findViewById<TextView>(R.id.tv_day_start_value)

        view.findViewById<View>(R.id.row_phone_alerts).setOnClickListener { vm.togglePhoneAlerts() }
        view.findViewById<View>(R.id.row_dim_on_charge).setOnClickListener { vm.toggleDimOnCharge() }
        view.findViewById<View>(R.id.row_day_start_hour).setOnClickListener {
            val hours = Array(24) { i -> if (i == 0) "12 AM" else if (i < 12) "$i AM" else if (i == 12) "12 PM" else "${i - 12} PM" }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Day Start Hour")
                .setItems(hours) { _, which -> vm.setDayStartHour(which) }
                .show()
        }
        view.findViewById<View>(R.id.row_unit).setOnClickListener { vm.toggleUnit() }
        view.findViewById<View>(R.id.row_auto_shutdown).setOnClickListener { vm.adjustAutoShutdown(60) }
        view.findViewById<View>(R.id.row_vibration).setOnClickListener { vm.toggleVibrationLevel() }
        view.findViewById<View>(R.id.row_charge_opt).setOnClickListener { vm.toggleChargeCurrentOpt() }
        view.findViewById<View>(R.id.row_charge_limit).setOnClickListener { vm.toggleChargeVoltageLimit() }
        view.findViewById<View>(R.id.row_perm_ble).setOnClickListener { vm.togglePermanentBle() }
        view.findViewById<View>(R.id.row_boost_timeout).setOnClickListener { vm.toggleBoostTimeout() }
        view.findViewById<Button>(R.id.btn_find_device).setOnClickListener { vm.findDevice() }
        view.findViewById<Button>(R.id.btn_dev_rebuild_history).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                vm.rebuildSessionHistoryFromLogs()
            }
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
