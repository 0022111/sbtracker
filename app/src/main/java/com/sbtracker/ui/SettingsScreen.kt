package com.sbtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Preferences + destructive housekeeping. Device identity lives here so users
 * can confirm which unit they're actually talking to.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val useCelsius by vm.useCelsius.collectAsStateWithLifecycle()
    val info       by vm.info.collectAsStateWithLifecycle()
    val extended   by vm.extended.collectAsStateWithLifecycle()

    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingCard("Display") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Celsius", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text  = if (useCelsius) "°C" else "°F",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = useCelsius, onCheckedChange = vm::setUseCelsius)
            }
        }

        SettingCard("Device") {
            if (info == null) {
                Text("No device seen yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                InfoRow("Type",    info!!.deviceType)
                InfoRow("Serial",  info!!.serialNumber)
                InfoRow("Address", info!!.deviceAddress)
                extended?.let {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    InfoRow("Heater runtime",  "${it.heaterRuntimeMinutes} min")
                    InfoRow("Charge time",     "${it.batteryChargingTimeMinutes} min")
                }
            }
        }

        SettingCard("Maintenance") {
            Text(
                "Sessions are derived from the god log. Rebuild re-runs detection. " +
                    "Clear wipes everything for the current device.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::rebuildSessions, modifier = Modifier.weight(1f)) {
                    Text("Rebuild")
                }
                OutlinedButton(
                    onClick  = { confirmClear = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Clear") }
            }
        }

        Text(
            text = "SBTracker · event-sourced",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title   = { Text("Clear history?") },
            text    = { Text("This permanently deletes every log entry and session for the current device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
