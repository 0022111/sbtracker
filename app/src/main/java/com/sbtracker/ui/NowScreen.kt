package com.sbtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sbtracker.ble.BleManager
import com.sbtracker.core.Format
import com.sbtracker.core.Summary
import com.sbtracker.data.DeviceStatus

/**
 * Pre- and mid-session screen. Three user moments collapse into one:
 *  • Pre-session → connection row + temp control + heat button.
 *  • Mid-session → live session card + current-hit indicator.
 *  • Always-on → battery & charge card.
 */
@Composable
fun NowScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val conn    by vm.connection.collectAsStateWithLifecycle()
    val status  by vm.latestStatus.collectAsStateWithLifecycle()
    val session by vm.liveSession.collectAsStateWithLifecycle()
    val unit    by vm.useCelsius.collectAsStateWithLifecycle()
    val eta     by vm.chargeEta.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionStrip(conn, onScan = vm::scan, onDisconnect = vm::disconnect)

        if (status == null) {
            EmptyHero(conn)
        } else {
            HeroCard(status!!, unit)
            session?.let { LiveSessionCard(it, status!!, unit) }
            TempControl(status!!, unit, onSet = vm::setTemp)
            HeatControls(
                heaterOn = status!!.heaterMode > 0,
                enabled  = conn is BleManager.State.Connected,
                onHeat   = vm::heatOn,
                onCool   = vm::heatOff,
            )
            BatteryCard(status!!, eta)
        }
    }
}

@Composable
private fun ConnectionStrip(state: BleManager.State, onScan: () -> Unit, onDisconnect: () -> Unit) {
    val label = when (state) {
        BleManager.State.Disconnected  -> "Not connected"
        BleManager.State.Scanning      -> "Scanning…"
        BleManager.State.Connecting    -> "Connecting…"
        is BleManager.State.Connected  -> state.name ?: state.address
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.BluetoothSearching, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        when (state) {
            is BleManager.State.Connected  -> OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            BleManager.State.Scanning,
            BleManager.State.Connecting    -> OutlinedButton(onClick = onDisconnect) { Text("Cancel") }
            BleManager.State.Disconnected  -> Button(onClick = onScan) { Text("Connect") }
        }
    }
}

@Composable
private fun EmptyHero(state: BleManager.State) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No device yet", style = MaterialTheme.typography.titleLarge)
            val hint = when (state) {
                BleManager.State.Scanning   -> "Searching for a nearby Storz & Bickel device…"
                BleManager.State.Connecting -> "Handshaking…"
                else -> "Tap Connect. Power on your device and keep it close."
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HeroCard(s: DeviceStatus, useCelsius: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = Format.tempPlain(s.currentTempC, useCelsius),
                    style      = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text     = if (useCelsius) "°C" else "°F",
                    style    = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            val heatLabel = when (s.heaterMode) {
                0 -> "Off"
                1 -> "Heating"
                2 -> "Boost"
                3 -> "Superboost"
                else -> "Mode ${s.heaterMode}"
            }
            Text(
                text  = "$heatLabel · target ${Format.temp(s.targetTempC, useCelsius)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (s.setpointReached) {
                Text("Setpoint reached", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LiveSessionCard(summary: Summary, latest: DeviceStatus, useCelsius: Boolean) {
    val currentHit = summary.hitCount > 0 && isHitActive(latest)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Session in progress", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (currentHit) {
                    Text("HIT", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            StatsRow(
                labels = listOf("Duration", "Hits", "Peak"),
                values = listOf(
                    Format.duration(summary.durationMs),
                    summary.hitCount.toString(),
                    Format.temp(summary.peakTempC, useCelsius),
                ),
            )
        }
    }
}

/** Heuristic: heater is on and setpoint was reached — the live packet ought to overlap a hit. */
private fun isHitActive(s: DeviceStatus): Boolean =
    s.heaterMode > 0 && s.setpointReached

@Composable
private fun TempControl(s: DeviceStatus, useCelsius: Boolean, onSet: (Int) -> Unit) {
    // Always edit in °C — the device speaks °C regardless of its display unit.
    var pending by remember(s.targetTempC) { mutableStateOf(s.targetTempC.toFloat()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Target", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    text  = Format.temp(pending.toInt(), useCelsius),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value          = pending,
                onValueChange  = { pending = it },
                onValueChangeFinished = { onSet(pending.toInt()) },
                valueRange     = TEMP_MIN.toFloat()..TEMP_MAX.toFloat(),
                steps          = (TEMP_MAX - TEMP_MIN) - 1,
            )
        }
    }
}

@Composable
private fun HeatControls(heaterOn: Boolean, enabled: Boolean, onHeat: () -> Unit, onCool: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val off = @Composable {
            FilledTonalButton(onClick = onCool, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Power, null); Spacer(Modifier.width(6.dp)); Text("Heat off")
            }
        }
        val on  = @Composable {
            Button(onClick = onHeat, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ElectricBolt, null); Spacer(Modifier.width(6.dp)); Text("Heat on")
            }
        }
        // Surface the safer action as primary.
        if (heaterOn) { off(); on() } else { on(); off() }
    }
}

@Composable
private fun BatteryCard(s: DeviceStatus, etaMin: Int?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryFull, null, tint = batteryColor(s.batteryLevel))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${s.batteryLevel}%",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (s.isCharging) Text("Charging", color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { s.batteryLevel / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color    = batteryColor(s.batteryLevel),
            )
            if (s.isCharging && etaMin != null) {
                val target = if (s.batteryLevel >= 80) "full" else "80%"
                Text(
                    "About $etaMin min to $target",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(labels: List<String>, values: List<String>) {
    Row {
        for (i in labels.indices) {
            Column(Modifier.weight(1f)) {
                Text(values[i], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(labels[i], style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun batteryColor(pct: Int): Color = when {
    pct >= 50 -> MaterialTheme.colorScheme.primary
    pct >= 20 -> Color(0xFFFFB020)
    else      -> Color(0xFFE05050)
}

private const val TEMP_MIN = 40
private const val TEMP_MAX = 220
