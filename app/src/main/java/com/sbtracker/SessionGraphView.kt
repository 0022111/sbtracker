package com.sbtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.sbtracker.data.DeviceStatus

/**
 * Session report graph: temperature curve with hit markers and battery line.
 */
class SessionGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class HitMarker(val startMs: Long, val durationSec: Long, val peakTempC: Int)

    private var dataPoints: List<DeviceStatus> = emptyList()
    private var hitMarkers: List<HitMarker> = emptyList()
    private var sessionStartMs: Long = 0
    private var sessionEndMs: Long = 0
    private var isCelsius: Boolean = true

    private val dp = context.resources.displayMetrics.density

    private val tempHeatingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9F0A")
        strokeWidth = 2.5f * dp; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    private val tempReadyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        strokeWidth = 2.5f * dp; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    private val batteryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44AAFF")
        strokeWidth = 1.5f * dp; style = Paint.Style.STROKE
        alpha = 180
    }

    private val setpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        strokeWidth = 1f * dp; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f * dp, 4f * dp), 0f)
    }

    private val hitBandPaint = Paint().apply {
        color = Color.parseColor("#FFD60A")
        alpha = 100; style = Paint.Style.FILL
    }

    private val hitMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD60A")
        style = Paint.Style.FILL
    }

    private val hitLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD60A")
        strokeWidth = 1.5f * dp; alpha = 200
    }

    private val hitNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 9f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2C2C2E")
        strokeWidth = 1f * dp
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#636366")
        textSize = 10f * dp
    }

    private val labelRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44AAFF")
        textSize = 10f * dp; textAlign = Paint.Align.RIGHT; alpha = 180
    }

    private val timeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#636366")
        textSize = 9f * dp; textAlign = Paint.Align.CENTER
    }

    private val tempFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val scrubberLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 1f * dp; alpha = 200
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E61C1C1E"); style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48484A"); strokeWidth = 1f * dp; style = Paint.Style.STROKE
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 11f * dp
    }

    private val tooltipLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93"); textSize = 10f * dp
    }

    private val scrubberDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }

    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#636366"); textSize = 14f * dp
    }

    private val fillPath = Path()

    private var scrubX: Float? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                scrubX = event.x
                parent.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrubX = null
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setSessionData(
        points: List<DeviceStatus>,
        hits: List<HitMarker>,
        startMs: Long,
        endMs: Long,
        isCelsius: Boolean = true
    ) {
        this.dataPoints = points
        this.hitMarkers = hits
        this.sessionStartMs = startMs
        this.sessionEndMs = endMs
        this.isCelsius = isCelsius
        Log.d("SessionGraph", "setSessionData: ${points.size} points, ${hits.size} hits, range=${endMs - startMs}ms")
        if (points.isNotEmpty()) {
            val first = points.first()
            Log.d("SessionGraph", "  first point: temp=${first.currentTempC}, bat=${first.batteryLevel}, ts=${first.timestampMs}")
        }
        for ((i, h) in hits.withIndex()) {
            Log.d("SessionGraph", "  hit $i: startMs=${h.startMs}, dur=${h.durationSec}s, peak=${h.peakTempC}")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padLeft = 30f * dp
        val padRight = 30f * dp
        val padTop = 12f * dp
        val padBot = 18f * dp

        val w = width.toFloat() - padLeft - padRight
        val h = height.toFloat() - padTop - padBot
        if (w <= 0f || h <= 0f) return

        val pts = dataPoints
        if (pts.size < 2 || sessionEndMs <= sessionStartMs) {
            canvas.drawText("No graph data (${pts.size} points)", padLeft + 8f * dp, padTop + h / 2f, noDataPaint)
            return
        }

        val duration = (sessionEndMs - sessionStartMs).toFloat()

        // Determine temp range from data
        val temps = pts.map { it.currentTempC }.filter { it > 0 }
        val minTemp = (temps.minOrNull() ?: 0).let { (it - 20).coerceAtLeast(0) }
        val maxTemp = (temps.maxOrNull() ?: 220).let { (it + 20).coerceAtMost(300) }
        val tempRange = (maxTemp - minTemp).toFloat().coerceAtLeast(1f)

        fun xFor(ms: Long) = padLeft + ((ms - sessionStartMs) / duration) * w
        fun yForTemp(t: Int) = padTop + h - ((t - minTemp) / tempRange) * h
        fun yForBatt(b: Int) = padTop + h - (b / 100f) * h

        // ── Grid lines ──
        val tempStep = ((maxTemp - minTemp) / 4).coerceAtLeast(10)
        var gridTemp = ((minTemp / tempStep) + 1) * tempStep
        while (gridTemp < maxTemp) {
            val y = yForTemp(gridTemp)
            canvas.drawLine(padLeft, y, padLeft + w, y, gridPaint)
            canvas.drawText("${gridTemp.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}", padLeft - 4f * dp, y + 4f * dp,
                labelPaint.apply { textAlign = Paint.Align.RIGHT })
            gridTemp += tempStep
        }

        // Battery axis labels
        for (pct in listOf(25, 50, 75)) {
            val y = yForBatt(pct)
            canvas.drawText("${pct}%", padLeft + w + 4f * dp, y + 4f * dp,
                labelRightPaint.apply { textAlign = Paint.Align.LEFT })
        }

        // ── Hit highlight bands ──
        for ((idx, hit) in hitMarkers.withIndex()) {
            val x0 = xFor(hit.startMs).coerceIn(padLeft, padLeft + w)
            val x1 = xFor(hit.startMs + hit.durationSec * 1000).coerceIn(padLeft, padLeft + w)
            val bandWidth = (x1 - x0).coerceAtLeast(8f * dp)

            // Vertical band
            canvas.drawRect(x0, padTop, x0 + bandWidth, padTop + h, hitBandPaint)

            // Marker lines at hit start and end
            canvas.drawLine(x0, padTop, x0, padTop + h, hitLinePaint)
            if (bandWidth > 8f * dp) {
                canvas.drawLine(x0 + bandWidth, padTop, x0 + bandWidth, padTop + h, hitLinePaint)
            }

            // Numbered circle at top
            val cx = x0 + bandWidth / 2f
            val dotR = 8f * dp
            canvas.drawCircle(cx, padTop + dotR + 2f * dp, dotR, hitMarkerPaint)
            canvas.drawText("${idx + 1}", cx, padTop + dotR + 2f * dp + 3.5f * dp, hitNumberPaint)
        }

        // ── Setpoint line ──
        val targetTemps = pts.filter { it.heaterMode > 0 }.map {
            when (it.heaterMode) {
                2 -> it.targetTempC + it.boostOffsetC
                3 -> it.targetTempC + it.superBoostOffsetC
                else -> it.targetTempC
            }
        }
        val primaryTarget = targetTemps.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        if (primaryTarget != null) {
            val setY = yForTemp(primaryTarget)
            canvas.drawLine(padLeft, setY, padLeft + w, setY, setpointPaint)
        }

        // ── Temperature line with gradient fill ──
        // Only use points where the device actually reported a temperature (non-zero).
        // Devices like the Veazy may not broadcast current temp when idle/unknown.
        val tempPts = pts.filter { it.currentTempC > 0 }
        fillPath.reset()
        var lastX = 0f

        tempPts.forEachIndexed { i, s ->
            val x = xFor(s.timestampMs)
            val y = yForTemp(s.currentTempC)

            if (i == 0) {
                fillPath.moveTo(x, padTop + h)
                fillPath.lineTo(x, y)
            } else {
                fillPath.lineTo(x, y)
                val prevPt = tempPts[i - 1]
                val px = xFor(prevPt.timestampMs)
                val py = yForTemp(prevPt.currentTempC)
                val paint = if (s.setpointReached) tempReadyPaint else tempHeatingPaint
                canvas.drawLine(px, py, x, y, paint)
            }
            lastX = x
        }

        // Close fill path
        fillPath.lineTo(lastX, padTop + h)
        fillPath.close()

        tempFillPaint.shader = LinearGradient(
            0f, padTop, 0f, padTop + h,
            Color.argb(40, 0x30, 0xD1, 0x58),
            Color.argb(0, 0x30, 0xD1, 0x58),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, tempFillPaint)

        // ── Battery line (secondary) ──
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            canvas.drawLine(
                xFor(prev.timestampMs), yForBatt(prev.batteryLevel),
                xFor(curr.timestampMs), yForBatt(curr.batteryLevel),
                batteryPaint
            )
        }

        // ── Time axis labels ──
        val durationSec = (sessionEndMs - sessionStartMs) / 1000
        val timeLabels = when {
            durationSec < 120 -> listOf(0L, durationSec / 2, durationSec)
            durationSec < 600 -> (0..durationSec step 60).toList()
            else -> (0..durationSec step 120).toList()
        }
        for (sec in timeLabels) {
            val x = xFor(sessionStartMs + sec * 1000)
            if (x >= padLeft && x <= padLeft + w) {
                val m = sec / 60
                val s = sec % 60
                val label = if (m > 0) "${m}m" else "${s}s"
                canvas.drawText(label, x, padTop + h + 14f * dp, timeLabelPaint)
            }
        }

        // ── Scrubber tooltip ──
        val sx = scrubX
        if (sx != null && sx >= padLeft && sx <= padLeft + w) {
            val scrubMs = sessionStartMs + ((sx - padLeft) / w * duration).toLong()
            val closest = pts.minByOrNull { kotlin.math.abs(it.timestampMs - scrubMs) } ?: return

            val cx = xFor(closest.timestampMs).coerceIn(padLeft, padLeft + w)
            val cy = yForTemp(closest.currentTempC)
            val by = yForBatt(closest.batteryLevel)

            // Vertical scrubber line
            canvas.drawLine(cx, padTop, cx, padTop + h, scrubberLinePaint)

            // Dots on temp and battery lines
            canvas.drawCircle(cx, cy, 4f * dp, scrubberDotPaint)
            canvas.drawCircle(cx, by, 3f * dp, batteryPaint.apply { style = Paint.Style.FILL })
            batteryPaint.style = Paint.Style.STROKE

            // Check if scrub point is during a hit
            val activeHit = hitMarkers.firstOrNull { hit ->
                closest.timestampMs >= hit.startMs &&
                closest.timestampMs <= hit.startMs + hit.durationSec * 1000
            }

            // Build tooltip
            val elapsed = (closest.timestampMs - sessionStartMs) / 1000
            val timeStr = "${elapsed / 60}m ${elapsed % 60}s"
            val lines = mutableListOf<Pair<String, String>>()

            val state = when {
                activeHit != null -> "HIT"
                closest.setpointReached -> "READY"
                closest.heaterMode > 0 -> "HEATING"
                else -> "IDLE"
            }
            lines.add("Status" to state)
            lines.add("Time" to timeStr)
            lines.add("Temp" to "${closest.currentTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}")
            lines.add("Battery" to "${closest.batteryLevel}%")
            if (activeHit != null) {
                lines.add("Hit" to "${activeHit.durationSec}s @ ${activeHit.peakTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}")
            }

            val lineHeight = 16f * dp
            val tooltipPadH = 10f * dp
            val tooltipPadV = 8f * dp
            val tooltipW = 120f * dp
            val tooltipH = tooltipPadV * 2 + lines.size * lineHeight

            var tx = cx + 8f * dp
            if (tx + tooltipW > padLeft + w) tx = cx - tooltipW - 8f * dp
            val ty = padTop + 4f * dp

            val tooltipRect = RectF(tx, ty, tx + tooltipW, ty + tooltipH)
            canvas.drawRoundRect(tooltipRect, 8f * dp, 8f * dp, tooltipBgPaint)
            canvas.drawRoundRect(tooltipRect, 8f * dp, 8f * dp, tooltipBorderPaint)

            lines.forEachIndexed { i, (label, value) ->
                val ly = ty + tooltipPadV + (i + 1) * lineHeight - 2f * dp
                canvas.drawText(label, tx + tooltipPadH, ly, tooltipLabelPaint)
                canvas.drawText(value, tx + tooltipW - tooltipPadH, ly,
                    tooltipTextPaint.apply { textAlign = Paint.Align.RIGHT })
                tooltipTextPaint.textAlign = Paint.Align.LEFT
            }
        }
    }
}
