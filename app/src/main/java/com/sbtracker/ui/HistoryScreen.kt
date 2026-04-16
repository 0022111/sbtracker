package com.sbtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sbtracker.core.Analytics
import com.sbtracker.core.Format
import com.sbtracker.core.Summary

/**
 * Review screen. Big aggregate numbers up top, scrollable session list below.
 */
@Composable
fun HistoryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val unit      by vm.useCelsius.collectAsStateWithLifecycle()

    if (summaries.isEmpty()) {
        EmptyHistory(modifier)
        return
    }
    val totals = Analytics.totals(summaries)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        TotalsCard(totals, unit)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(summaries, key = { it.session.id }) { s ->
                SessionRow(s, unit, onOpen = { vm.openDetail(s.session.id) })
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No sessions yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Connect your device and ignite. Completed sessions will show up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TotalsCard(t: com.sbtracker.core.Totals, useCelsius: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("All time", style = MaterialTheme.typography.labelLarge)
            Row {
                Stat(Modifier.weight(1f), "Sessions", t.sessions.toString())
                Stat(Modifier.weight(1f), "Hits",     t.hits.toString())
                Stat(Modifier.weight(1f), "Active",   Format.duration(t.durationMs))
            }
            Row {
                Stat(Modifier.weight(1f), "Avg hits",     "%.1f".format(t.avgHits))
                Stat(Modifier.weight(1f), "Avg duration", Format.duration(t.avgDurationMs))
                Stat(Modifier.weight(1f), "Avg peak",     Format.temp(t.avgPeakC, useCelsius))
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SessionRow(s: Summary, useCelsius: Boolean, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(Format.full(s.session.startTimeMs), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${s.hitCount} hits · peak ${Format.temp(s.peakTempC, useCelsius)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(Format.duration(s.durationMs), style = MaterialTheme.typography.titleMedium)
        }
    }
}
