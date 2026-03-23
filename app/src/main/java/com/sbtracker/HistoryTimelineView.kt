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
import android.view.MotionEvent
import android.view.View
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session
import java.util.Calendar
import kotlin.math.abs

/**
 * History page timeline graph.
 *
 * Shows battery % over a day or week window, with color-coded segments (normal/low/charging),
 * session tap markers, and a scrubber tooltip.
 *
 * Touch: drag to scrub the timeline; short tap near a session marker fires [onSessionTapped].
 */
class HistoryTimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Period { DAY, WEEK }

    /** Called with the tapped [Session] when the user taps a session marker. */
    var onSessionTapped: ((Session) -> Unit)? = null

    // ── Data ─────────────────────────────────────────────────────────────────
    private var statuses: List<DeviceStatus> = emptyList()
    private var sessions: List<Session> = emptyList()
    private var windowStartMs: Long = 0L
    private var windowEndMs: Long = 0L
    private var period: Period = Period.DAY

    /** If two consecutive status points are more than this apart, break the line. */
    private val GAP_MS = 5L * 60 * 1000

    // ── Touch state ──────────────────────────────────────────────────────────
    private var scrubX: Float? = null
    private var isScrubbing = false
    private var touchDownX = 0f
    private var touchDownY = 0f

    // ── Density ──────────────────────────────────────────────────────────────
    private val dp = context.resources.displayMetrics.density

    // ── Paints ───────────────────────────────────────────────────────────────
    private val battNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44AAFF")
        strokeWidth = 2.5f * dp
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val battLowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF453A")
        strokeWidth = 2.5f * dp
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val battChargingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        strokeWidth = 2.5f * dp
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2C2C2E")
        strokeWidth = 1f
    }
    private val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#636366")
        textSize = 9f * dp
        textAlign = Paint.Align.RIGHT
    }
    private val timeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 9f * dp
        textAlign = Paint.Align.CENTER
    }

    // Session marker: amber dashed vertical line + filled circle with number
    private val sessionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9F0A")
        strokeWidth = 1.5f * dp
        alpha = 170
        pathEffect = DashPathEffect(floatArrayOf(5f * dp, 3f * dp), 0f)
    }
    private val sessionDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9F0A")
        style = Paint.Style.FILL
    }
    private val sessionNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 7.5f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sessionLinePaintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9F0A")
        strokeWidth = 2f * dp
        alpha = 255
    }

    // Charging fill under battery line
    private val chargeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // "Now" marker
    private val nowLinePaint = Paint().apply {
        color = Color.parseColor("#3AFFFFFF")
        strokeWidth = 1f * dp
    }

    // Scrubber
    private val scrubberLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1f * dp
        alpha = 200
    }
    private val scrubberDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E61C1C1E")
        style = Paint.Style.FILL
    }
    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48484A")
        strokeWidth = 1f * dp
        style = Paint.Style.STROKE
    }
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * dp
    }
    private val tooltipLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 10f * dp
    }
    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#636366")
        textSize = 12f * dp
        textAlign = Paint.Align.CENTER
    }

    // Re-used path objects to avoid allocations during draw
    private val chargeFillPath = Path()

    // ── Public API ────────────────────────────────────────────────────────────
    fun setData(
        statuses: List<DeviceStatus>,
        sessions: List<Session>,
        windowStartMs: Long,
        windowEndMs: Long,
        period: Period
    ) {
        this.statuses = statuses
        this.sessions = sessions
        this.windowStartMs = windowStartMs
        this.windowEndMs = if (windowEndMs > windowStartMs) windowEndMs else windowStartMs + 1L
        this.period = period
        invalidate()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val padLeft = 42f * dp
        val padRight = 16f * dp
        val w = width.toFloat() - padLeft - padRight

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                isScrubbing = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - touchDownX)
                val dy = abs(event.y - touchDownY)
                if (!isScrubbing && dx > 8f * dp && dx > dy) {
                    isScrubbing = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                if (isScrubbing) {
                    scrubX = event.x.coerceIn(padLeft, padLeft + w)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isScrubbing && event.action == MotionEvent.ACTION_UP) {
                    val dx = abs(event.x - touchDownX)
                    val dy = abs(event.y - touchDownY)
                    if (dx <= 8f * dp && dy <= 8f * dp) {
                        handleTap(event.x, padLeft, w)
                    }
                }
                scrubX = null
                isScrubbing = false
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(tapX: Float, padLeft: Float, w: Float) {
        if (windowEndMs <= windowStartMs || sessions.isEmpty() || w <= 0f) return
        val totalMs = (windowEndMs - windowStartMs).toFloat()
        var closest: Session? = null
        var closestDist = 40f * dp  // maximum tap radius

        val visibleSessions = sessions.filter { it.startTimeMs in windowStartMs..windowEndMs }
        for (s in visibleSessions) {
            val x = padLeft + ((s.startTimeMs - windowStartMs) / totalMs) * w
            val dist = abs(tapX - x)
            if (dist < closestDist) {
                closestDist = dist
                closest = s
            }
        }
        closest?.let { onSessionTapped?.invoke(it) }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padLeft  = 42f * dp
        val padRight = 16f * dp
        val padTop   = 18f * dp
        val padBot   = 30f * dp

        val w = width.toFloat()  - padLeft - padRight
        val h = height.toFloat() - padTop  - padBot
        if (w <= 0f || h <= 0f || windowEndMs <= windowStartMs) return

        val totalMs = (windowEndMs - windowStartMs).toFloat()

        fun xFor(ms: Long): Float = padLeft + ((ms - windowStartMs).toFloat() / totalMs) * w
        fun yForBatt(pct: Int): Float = padTop + h - (pct.coerceIn(0, 100) / 100f) * h

        // ── Grid ─────────────────────────────────────────────────────────────
        for (pct in listOf(0, 25, 50, 75, 100)) {
            val y = yForBatt(pct)
            canvas.drawLine(padLeft, y, padLeft + w, y, gridPaint)
            canvas.drawText("$pct%", padLeft - 5f * dp, y + gridLabelPaint.textSize / 3f, gridLabelPaint)
        }

        // ── "Now" line (day view — shows current position in day) ─────────────
        if (period == Period.DAY) {
            val nowX = xFor(System.currentTimeMillis())
            if (nowX >= padLeft && nowX <= padLeft + w) {
                canvas.drawLine(nowX, padTop, nowX, padTop + h, nowLinePaint)
            }
        }

        // ── Battery line (segmented, break on gaps > GAP_MS) ─────────────────
        if (statuses.size >= 2) {
            var i = 0
            while (i < statuses.size) {
                // Find the end of a contiguous segment
                var j = i + 1
                while (j < statuses.size &&
                    statuses[j].timestampMs - statuses[j - 1].timestampMs <= GAP_MS) j++
                drawBatterySegment(canvas, statuses.subList(i, j), padLeft, padTop, w, h, totalMs)
                i = j
            }
        } else {
            // No data — show hint
            val label = if (period == Period.DAY) "No data recorded today" else "No data in the last 7 days"
            canvas.drawText(label, padLeft + w / 2f, padTop + h / 2f, noDataPaint)
        }

        // ── Time axis labels ──────────────────────────────────────────────────
        val ticks = computeTimeTicks()
        for ((tickMs, label) in ticks) {
            val x = xFor(tickMs)
            if (x < padLeft - 2f * dp || x > padLeft + w + 2f * dp) continue
            canvas.drawLine(x, padTop + h, x, padTop + h + 4f * dp, gridPaint)
            canvas.drawText(label, x, padTop + h + padBot - 4f * dp, timeLabelPaint)
        }

        // ── Session markers ───────────────────────────────────────────────────
        val visibleSessions = sessions
            .filter { it.startTimeMs in windowStartMs..windowEndMs }
            .sortedBy { it.startTimeMs }

        for ((idx, s) in visibleSessions.withIndex()) {
            val x = xFor(s.startTimeMs)
            if (x < padLeft - 1f || x > padLeft + w + 1f) continue

            // Vertical dashed line
            canvas.drawLine(x, padTop, x, padTop + h, sessionLinePaint)

            // Filled circle
            val dotR = 7f * dp
            val cy = padTop + dotR + 2f * dp
            canvas.drawCircle(x, cy, dotR, sessionDotPaint)

            // Number inside circle
            val num = "${idx + 1}"
            canvas.drawText(num, x, cy + sessionNumberPaint.textSize * 0.35f, sessionNumberPaint)
        }

        // ── Scrubber ──────────────────────────────────────────────────────────
        val sx = scrubX
        if (sx != null && sx >= padLeft && sx <= padLeft + w) {
            drawScrubber(canvas, sx, padLeft, padTop, w, h)
        }
    }

    private fun drawBatterySegment(
        canvas: Canvas,
        seg: List<DeviceStatus>,
        padLeft: Float, padTop: Float,
        w: Float, h: Float,
        totalMs: Float
    ) {
        if (seg.size < 2) return
        val totalMsD = (windowEndMs - windowStartMs).toFloat()

        fun xFor(ms: Long) = padLeft + ((ms - windowStartMs).toFloat() / totalMsD) * w
        fun yForBatt(pct: Int) = padTop + h - (pct.coerceIn(0, 100) / 100f) * h

        // Draw each segment with its color
        for (i in 1 until seg.size) {
            val prev = seg[i - 1]; val curr = seg[i]
            val x0 = xFor(prev.timestampMs); val y0 = yForBatt(prev.batteryLevel)
            val x1 = xFor(curr.timestampMs); val y1 = yForBatt(curr.batteryLevel)
            val paint = when {
                curr.isCharging         -> battChargingPaint
                curr.batteryLevel <= 20 -> battLowPaint
                else                    -> battNormalPaint
            }
            canvas.drawLine(x0, y0, x1, y1, paint)
        }

        // Subtle green gradient fill during charging sub-segments
        chargeFillPath.reset()
        var inCharge = false
        for (i in seg.indices) {
            val s = seg[i]
            val x = xFor(s.timestampMs)
            val y = yForBatt(s.batteryLevel)
            if (s.isCharging && !inCharge) {
                chargeFillPath.moveTo(x, padTop + h)
                chargeFillPath.lineTo(x, y)
                inCharge = true
            } else if (s.isCharging) {
                chargeFillPath.lineTo(x, y)
            } else if (inCharge) {
                // Transition out of charging — close fill at the previous charging point
                val prev = seg[i - 1]
                chargeFillPath.lineTo(xFor(prev.timestampMs), padTop + h)
                chargeFillPath.close()
                inCharge = false
            }
        }
        if (inCharge) {
            val last = seg.last()
            chargeFillPath.lineTo(xFor(last.timestampMs), padTop + h)
            chargeFillPath.close()
        }
        chargeFillPaint.shader = LinearGradient(
            0f, padTop, 0f, padTop + h,
            Color.argb(35, 0x30, 0xD1, 0x58),
            Color.argb(0,  0x30, 0xD1, 0x58),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(chargeFillPath, chargeFillPaint)
    }

    private fun computeTimeTicks(): List<Pair<Long, String>> {
        val ticks = mutableListOf<Pair<Long, String>>()
        val cal = Calendar.getInstance()

        if (period == Period.DAY) {
            // Tick every 3 hours; first tick at the next 3-hour boundary after windowStart
            cal.timeInMillis = windowStartMs
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val h = cal.get(Calendar.HOUR_OF_DAY)
            cal.set(Calendar.HOUR_OF_DAY, ((h / 3) + 1) * 3)
            while (cal.timeInMillis <= windowEndMs) {
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val label = when {
                    hour == 0  -> "12AM"
                    hour == 12 -> "12PM"
                    hour < 12  -> "${hour}AM"
                    else       -> "${hour - 12}PM"
                }
                ticks.add(cal.timeInMillis to label)
                cal.add(Calendar.HOUR_OF_DAY, 3)
            }
        } else {
            // Tick at midpoint (noon) of each day in the week
            cal.timeInMillis = windowStartMs
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            while (cal.timeInMillis <= windowEndMs) {
                val midday = cal.timeInMillis + 12L * 3600_000L
                ticks.add(midday to dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1])
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return ticks
    }

    private fun drawScrubber(
        canvas: Canvas,
        sx: Float,
        padLeft: Float, padTop: Float,
        w: Float, h: Float
    ) {
        val totalMs = (windowEndMs - windowStartMs).toFloat()
        val scrubMs = windowStartMs + ((sx - padLeft) / w * totalMs).toLong()
        val closest = statuses.minByOrNull { abs(it.timestampMs - scrubMs) }

        // Vertical line
        canvas.drawLine(sx, padTop, sx, padTop + h, scrubberLinePaint)

        // Dot on battery line
        if (closest != null) {
            val cy = padTop + h - (closest.batteryLevel.coerceIn(0, 100) / 100f) * h
            canvas.drawCircle(sx, cy, 4f * dp, scrubberDotPaint)

            // Build time label
            val cal = Calendar.getInstance().also { it.timeInMillis = closest.timestampMs }
            val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val min    = "%02d".format(cal.get(Calendar.MINUTE))
            val amPm   = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            val timeStr = if (period == Period.WEEK) {
                val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]} $hour12:$min $amPm"
            } else "$hour12:$min $amPm"

            val statusStr = when {
                closest.isCharging      -> "CHARGING"
                closest.heaterMode > 0  -> "HEATING"
                else                    -> "IDLE"
            }

            // Check if any session is active at this timestamp
            val activeSession = sessions.firstOrNull { s ->
                closest.timestampMs >= s.startTimeMs && closest.timestampMs <= s.endTimeMs
            }

            val lines = buildList {
                add("Time"    to timeStr)
                add("Battery" to "${closest.batteryLevel}%")
                add("Status"  to statusStr)
                if (activeSession != null) add("Session" to "Active")
            }

            drawTooltip(canvas, sx, padLeft + w, padTop, lines)
        }
    }

    private fun drawTooltip(
        canvas: Canvas,
        sx: Float,
        rightEdge: Float,
        padTop: Float,
        lines: List<Pair<String, String>>
    ) {
        val lineH   = 16f * dp
        val padH    = 10f * dp
        val padV    = 8f * dp
        val tWidth  = 140f * dp
        val tHeight = padV * 2 + lines.size * lineH

        var tx = sx + 8f * dp
        if (tx + tWidth > rightEdge) tx = sx - tWidth - 8f * dp
        val ty = padTop + 4f * dp

        val rect = RectF(tx, ty, tx + tWidth, ty + tHeight)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, tooltipBgPaint)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, tooltipBorderPaint)

        lines.forEachIndexed { i, (label, value) ->
            val ly = ty + padV + (i + 1) * lineH - 2f * dp
            canvas.drawText(label, tx + padH, ly, tooltipLabelPaint)
            tooltipTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(value, tx + tWidth - padH, ly, tooltipTextPaint)
            tooltipTextPaint.textAlign = Paint.Align.LEFT
        }
    }
}
