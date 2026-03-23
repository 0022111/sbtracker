package com.sbtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.sbtracker.data.DeviceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Battery history: **one candle per charging event**, **one per session event**, with **grey idle**
 * filling gaps (no samples) and continuous idle runs. OHLC from samples in each segment; idle gaps
 * bridge open→close between neighbors. Dashed line = projected charge to full.
 */
class BatteryGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<DeviceStatus> = emptyList()
    private var windowStartMs: Long = 0L
    private var chargeEtaMs: Long = 0L
    private var chargeEta80Ms: Long = 0L
    private var projectionStartLevel: Int? = null

    private val chargeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC30D158")
        style = Paint.Style.FILL
    }
    private val chargeWick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val chargeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5CDE7A")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val sessionFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFF453A")
        style = Paint.Style.FILL
    }
    private val sessionWick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF453A")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val sessionOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B60")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val idleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#664C4C4E")
        style = Paint.Style.FILL
    }
    private val idleWick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val projectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        alpha = 200
    }

    private val target80Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A84FF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        alpha = 150
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#22FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        textSize = 20f
    }

    private val axisTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48484A")
        textSize = 17f
    }

    private val nowLinePaint = Paint().apply {
        color = Color.parseColor("#3A3A3C")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val fmtShortTime = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fmtDateTime = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())

    private enum class CandleKind { CHARGE, SESSION, IDLE }

    private data class EventSegment(
        val startMs: Long,
        val endMs: Long,
        val open: Int,
        val high: Int,
        val low: Int,
        val close: Int,
        val kind: CandleKind
    )

    fun setData(
        points: List<DeviceStatus>,
        windowStartMs: Long,
        chargeEtaMs: Long = 0L,
        chargeEta80Ms: Long = 0L,
        projectionStartLevel: Int? = null
    ) {
        this.dataPoints = points
        this.windowStartMs = windowStartMs
        this.chargeEtaMs = max(chargeEtaMs, 0L)
        this.chargeEta80Ms = max(chargeEta80Ms, 0L)
        this.projectionStartLevel = projectionStartLevel
        invalidate()
    }

    private fun timeAxisFormat(spanMs: Long): SimpleDateFormat =
        if (spanMs > 36L * 60L * 60_000L) fmtDateTime else fmtShortTime

    private fun pointKind(p: DeviceStatus): CandleKind = when {
        p.isCharging -> CandleKind.CHARGE
        p.heaterMode > 0 -> CandleKind.SESSION
        else -> CandleKind.IDLE
    }

    private fun segmentFromRun(samples: List<DeviceStatus>): EventSegment {
        val sorted = samples.sortedBy { it.timestampMs }
        val a = sorted.first()
        val b = sorted.last()
        return EventSegment(
            startMs = a.timestampMs,
            endMs = b.timestampMs,
            open = a.batteryLevel,
            close = b.batteryLevel,
            high = sorted.maxOf { it.batteryLevel },
            low = sorted.minOf { it.batteryLevel },
            kind = pointKind(a)
        )
    }

    private fun idleBridge(startMs: Long, endMs: Long, openLvl: Int, closeLvl: Int): EventSegment {
        val hi = max(openLvl, closeLvl)
        val lo = min(openLvl, closeLvl)
        return EventSegment(startMs, endMs, openLvl, hi, lo, closeLvl, CandleKind.IDLE)
    }

    /** Collapse adjacent idle bars (gap + run, lead-in + run, etc.) into one. */
    private fun mergeAdjacentIdle(segments: MutableList<EventSegment>) {
        var i = 0
        while (i < segments.size) {
            if (i + 1 < segments.size &&
                segments[i].kind == CandleKind.IDLE &&
                segments[i + 1].kind == CandleKind.IDLE
            ) {
                val a = segments[i]
                val b = segments[i + 1]
                segments[i] = EventSegment(
                    startMs = min(a.startMs, b.startMs),
                    endMs = max(a.endMs, b.endMs),
                    open = a.open,
                    high = max(a.high, b.high),
                    low = min(a.low, b.low),
                    close = b.close,
                    kind = CandleKind.IDLE
                )
                segments.removeAt(i + 1)
            } else {
                i++
            }
        }
    }

    private fun buildEventSegments(chron: List<DeviceStatus>, winStart: Long, winEnd: Long): List<EventSegment> {
        if (chron.isEmpty()) return emptyList()

        val raw = mutableListOf<EventSegment>()

        val firstTs = chron.first().timestampMs
        if (firstTs > winStart) {
            val b = chron.first().batteryLevel
            raw.add(idleBridge(winStart, firstTs, b, b))
        }

        val runs = mutableListOf<MutableList<DeviceStatus>>()
        for (p in chron) {
            if (runs.isEmpty() || pointKind(runs.last().last()) != pointKind(p)) {
                runs.add(mutableListOf(p))
            } else {
                runs.last().add(p)
            }
        }

        for (run in runs) {
            val seg = segmentFromRun(run)
            if (raw.isNotEmpty()) {
                val prev = raw.last()
                if (prev.endMs < seg.startMs) {
                    raw.add(idleBridge(prev.endMs, seg.startMs, prev.close, seg.open))
                }
            }
            raw.add(seg)
        }

        val lastEnd = raw.last().endMs
        if (lastEnd < winEnd) {
            val b = raw.last().close
            raw.add(idleBridge(lastEnd, winEnd, b, b))
        }

        mergeAdjacentIdle(raw)

        return raw.map { s ->
            if (s.endMs <= s.startMs) s.copy(endMs = s.startMs + 1L) else s
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padLeft = 10f
        val padRight = 10f
        val padTop = 22f
        val padBot = 34f

        val w = width.toFloat() - padLeft - padRight
        val h = height.toFloat() - padTop - padBot

        if (w <= 0f || h <= 0f) return

        val now = System.currentTimeMillis()
        val winStart = windowStartMs
        val winEnd = max(now + chargeEtaMs, max(now, winStart + 60_000L))
        val spanMs = max(winEnd - winStart, 1L)
        val span = spanMs.toFloat()
        val axisFmt = timeAxisFormat(winEnd - winStart)

        fun xFor(ts: Long): Float {
            val frac = ((ts - winStart).toFloat() / span).coerceIn(0f, 1f)
            return padLeft + frac * w
        }

        fun yFor(level: Int): Float =
            padTop + h - (level.coerceIn(0, 100) / 100f) * h

        // 80% Target Line
        val y80 = yFor(80)
        canvas.drawLine(padLeft, y80, padLeft + w, y80, target80Paint)
        canvas.drawText("80%", padLeft, y80 - 4f, Paint(labelPaint).apply { color = Color.parseColor("#0A84FF") })

        for (i in 0..4) {
            val y = padTop + h - (i * 0.25f * h)
            canvas.drawLine(padLeft, y, padLeft + w, y, gridPaint)
            val label = when (i) {
                0 -> "0%"
                2 -> "50%"
                4 -> "100%"
                else -> ""
            }
            if (label.isNotEmpty()) {
                canvas.drawText(label, padLeft, y - 4f, labelPaint)
            }
        }

        val leftLabel = axisFmt.format(Date(winStart))
        val rightLabel = axisFmt.format(Date(winEnd))
        val axisY = height - 10f
        canvas.drawText(leftLabel, padLeft, axisY, axisTimePaint)
        val rw = axisTimePaint.measureText(rightLabel)
        canvas.drawText(rightLabel, padLeft + w - rw, axisY, axisTimePaint)

        val chron = dataPoints
            .filter { it.timestampMs >= winStart && it.timestampMs <= winEnd }
            .sortedBy { it.timestampMs }

        val segments = buildEventSegments(chron, winStart, winEnd)

        val minSpanPx = 5f
        val minBodyPx = 3f

        fun drawSegment(seg: EventSegment) {
            var xL = xFor(seg.startMs)
            var xR = xFor(seg.endMs)
            if (xR - xL < minSpanPx) {
                val c = (xL + xR) / 2f
                xL = c - minSpanPx / 2f
                xR = c + minSpanPx / 2f
            }
            val xc = (xL + xR) / 2f
            val spanX = xR - xL
            val bodyHalfW = (spanX * 0.38f).coerceIn(3f, min(spanX / 2f - 1f, 56f))

            val yHi = yFor(seg.high)
            val yLo = yFor(seg.low)
            val yO = yFor(seg.open)
            val yC = yFor(seg.close)
            val bodyTop = min(yO, yC)
            val bodyBot = max(yO, yC)
            val bodyH = abs(bodyBot - bodyTop)
            val adjTop: Float
            val adjBot: Float
            if (bodyH < minBodyPx) {
                val mid = (bodyTop + bodyBot) / 2f
                adjTop = mid - minBodyPx / 2f
                adjBot = mid + minBodyPx / 2f
            } else {
                adjTop = bodyTop
                adjBot = bodyBot
            }

            val (fill, wick, outline) = when (seg.kind) {
                CandleKind.CHARGE -> Triple(chargeFill, chargeWick, chargeOutline)
                CandleKind.SESSION -> Triple(sessionFill, sessionWick, sessionOutline)
                CandleKind.IDLE -> Triple(idleFill, idleWick, idleWick)
            }

            canvas.drawLine(xc, yHi, xc, yLo, wick)

            val rect = RectF(xc - bodyHalfW, adjTop, xc + bodyHalfW, adjBot)
            canvas.drawRoundRect(rect, 2f, 2f, fill)
            if (seg.kind != CandleKind.IDLE) {
                canvas.drawRoundRect(rect, 2f, 2f, outline)
            }
        }

        for (kind in listOf(CandleKind.IDLE, CandleKind.SESSION, CandleKind.CHARGE)) {
            for (seg in segments.filter { it.kind == kind }.sortedBy { it.startMs }) {
                drawSegment(seg)
            }
        }

        val xNow = xFor(now)
        if (xNow in padLeft..(padLeft + w)) {
            canvas.drawLine(xNow, padTop, xNow, padTop + h, nowLinePaint)
        }

        val level = projectionStartLevel
        if (chargeEtaMs > 0L && level != null && level < 100) {
            val x0 = xFor(now)
            val y0 = yFor(level)
            
            // Draw dual-segment taper projection!
            if (level < 80 && chargeEta80Ms > 0L) {
                val x80 = xFor(now + chargeEta80Ms)
                val y80proj = yFor(80)
                if (x80 > x0 + 2f) {
                    canvas.drawLine(x0, y0, x80, y80proj, projectionPaint)
                    
                    val x100 = xFor(now + chargeEtaMs)
                    val y100proj = yFor(100)
                    if (x100 > x80 + 2f) {
                        canvas.drawLine(x80, y80proj, x100, y100proj, projectionPaint)
                    }
                }
            } else {
                // Already above 80% or no 80 target, just draw straight to 100
                val x100 = xFor(now + chargeEtaMs)
                val y100proj = yFor(100)
                if (x100 > x0 + 2f) {
                    canvas.drawLine(x0, y0, x100, y100proj, projectionPaint)
                }
            }
        }
    }
}
