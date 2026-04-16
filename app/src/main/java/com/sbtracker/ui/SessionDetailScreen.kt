package com.sbtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.sbtracker.core.Format
import com.sbtracker.core.Hit
import com.sbtracker.core.Summary

/**
 * Full-bleed session detail. Back → History. Numbers up top, trace in the
 * middle, hit list at the bottom. Nothing persisted here — it's all derived.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(vm: AppViewModel) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val unit   by vm.useCelsius.collectAsStateWithLifecycle()

    BackHandler { vm.closeDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.session?.let { Format.full(it.startTimeMs) } ?: "Session",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = vm::closeDetail) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        val inner = Modifier.fillMaxSize().padding(pad)
        val d = detail
        if (d == null) {
            Box(inner, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = inner.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(d.summary, unit) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Temperature", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        if (d.log.isEmpty()) TraceEmpty() else TraceGraph(d.log, d.hits)
                    }
                }
            }
            item { HitListHeader(d.hits.size) }
            items(d.hits, key = { it.startMs }) { HitRow(it, unit) }
        }
    }
}

@Composable
private fun SummaryCard(s: Summary, useCelsius: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Stat(Modifier.weight(1f), "Hits",     s.hitCount.toString())
                Stat(Modifier.weight(1f), "Duration", Format.duration(s.durationMs))
                Stat(Modifier.weight(1f), "Peak",     Format.temp(s.peakTempC, useCelsius))
            }
            Row {
                Stat(Modifier.weight(1f), "Avg temp",  Format.temp(s.avgTempC, useCelsius))
                Stat(Modifier.weight(1f), "Heat-up",   "${s.heatUpSeconds}s")
                Stat(Modifier.weight(1f), "Battery",   "-${s.batteryDrain}%")
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
private fun HitListHeader(count: Int) {
    Text(
        text  = if (count == 0) "No hits detected" else "Hits ($count)",
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun HitRow(h: Hit, useCelsius: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Format.clock(h.startMs), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text  = "${h.durationMs / 1000}s · peak ${Format.temp(h.peakTempC, useCelsius)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
