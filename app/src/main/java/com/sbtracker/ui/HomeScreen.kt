package com.sbtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sbtracker.ble.BleManager
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val status     by vm.status.collectAsStateWithLifecycle()
    val sessions   by vm.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SBTracker") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionRow(connection, onScan = vm::scan, onDisconnect = vm::disconnect)
            StatusCard(status)
            ActionRow(
                enabled    = connection is BleManager.State.Connected,
                onHeatOn   = vm::igniteOn,
                onHeatOff  = vm::igniteOff,
                onRebuild  = vm::rebuildSessions,
            )
            HorizontalDivider()
            Text("Sessions · ${sessions.size}", fontWeight = FontWeight.SemiBold)
            SessionList(sessions)
        }
    }
}

@Composable
private fun ConnectionRow(
    state: BleManager.State,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val label = when (state) {
        BleManager.State.Disconnected  -> "Disconnected"
        BleManager.State.Scanning      -> "Scanning…"
        BleManager.State.Connecting    -> "Connecting…"
        is BleManager.State.Connected  -> "Connected · ${state.name ?: state.address}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        when (state) {
            is BleManager.State.Connected -> OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            BleManager.State.Disconnected -> Button(onClick = onScan) { Text("Connect") }
            else                          -> OutlinedButton(onClick = onDisconnect) { Text("Cancel") }
        }
    }
}

@Composable
private fun StatusCard(s: DeviceStatus?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s == null) {
                Text("No readings yet.", style = MaterialTheme.typography.bodyMedium)
                Text("Connect your device to start logging.", style = MaterialTheme.typography.bodySmall)
                return@Card
            }
            val unit = if (s.isCelsius) "°C" else "°F"
            val curr = if (s.isCelsius) s.currentTempC else s.currentTempC.toF()
            val tgt  = if (s.isCelsius) s.targetTempC  else s.targetTempC.toF()
            Text("$curr$unit", style = MaterialTheme.typography.headlineLarge)
            Text("Target $tgt$unit · mode ${s.heaterMode}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Battery ${s.batteryLevel}%${if (s.isCharging) " · charging" else ""}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (s.isSynthetic) {
                Text("(synthetic temp)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActionRow(
    enabled: Boolean,
    onHeatOn: () -> Unit,
    onHeatOff: () -> Unit,
    onRebuild: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onHeatOn,  enabled = enabled, modifier = Modifier.weight(1f)) { Text("Heat on") }
        Button(onClick = onHeatOff, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Heat off") }
        OutlinedButton(onClick = onRebuild, modifier = Modifier.weight(1f)) { Text("Rebuild") }
    }
}

@Composable
private fun SessionList(sessions: List<Session>) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "No sessions yet. Ignite to begin.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(sessions, key = { it.id }) { s ->
            SessionRow(s)
        }
    }
}

private val TIME_FMT = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())

@Composable
private fun SessionRow(s: Session) {
    val minutes = (s.endTimeMs - s.startTimeMs) / 60_000
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(TIME_FMT.format(Date(s.startTimeMs)), modifier = Modifier.weight(1f))
            Text("${minutes}m")
            Spacer(Modifier.height(0.dp))
        }
    }
}

private fun Int.toF(): Int = (this * 9 / 5) + 32
