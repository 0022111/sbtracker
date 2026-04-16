package com.sbtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sbtracker.core.Hit
import com.sbtracker.data.DeviceStatus

/**
 * Minimal temperature trace. One line (current temp), dashed target line,
 * shaded bands for detected hits. No axes, no ticks — the numbers live above.
 *
 * Pure composable. Takes the session's slice of the log and its hits.
 */
@Composable
fun TraceGraph(
    log: List<DeviceStatus>,
    hits: List<Hit>,
    modifier: Modifier = Modifier,
) {
    val line    = MaterialTheme.colorScheme.primary
    val muted   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val hitBand = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (log.isEmpty()) return@Canvas

        val startMs = log.first().timestampMs
        val endMs   = log.last().timestampMs.coerceAtLeast(startMs + 1)
        val minC    = (log.minOf { it.currentTempC } - 5).coerceAtLeast(0)
        val maxC    = (log.maxOf { maxOf(it.currentTempC, it.targetTempC) } + 5).coerceAtLeast(minC + 10)

        fun x(ms: Long) = (ms - startMs).toFloat() / (endMs - startMs) * size.width
        fun y(c:  Int)  = size.height - (c - minC).toFloat() / (maxC - minC) * size.height

        // Hit bands.
        for (h in hits) {
            val x0 = x(h.startMs)
            val x1 = x(h.startMs + h.durationMs)
            drawRect(
                color    = hitBand,
                topLeft  = Offset(x0, 0f),
                size     = Size((x1 - x0).coerceAtLeast(1f), size.height),
            )
        }

        // Dashed target line (from the last reading).
        val targetY = y(log.last().targetTempC)
        drawLine(
            color       = muted,
            start       = Offset(0f, targetY),
            end         = Offset(size.width, targetY),
            strokeWidth = 2f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )

        // Current temperature trace.
        val path = Path().apply {
            moveTo(x(log.first().timestampMs), y(log.first().currentTempC))
            for (i in 1 until log.size) {
                val s = log[i]
                lineTo(x(s.timestampMs), y(s.currentTempC))
            }
        }
        drawPath(path, color = line, style = Stroke(width = 3f))
    }
}

@Composable
fun TraceEmpty(modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        drawLine(
            color       = muted,
            start       = Offset(0f, size.height / 2),
            end         = Offset(size.width, size.height / 2),
            strokeWidth = 2f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )
    }
}
