package com.sbtracker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.sbtracker.analytics.DailyStats
import java.util.Calendar

/**
 * History page session-activity bar chart.
 *
 * DAY mode  – last 7 days as individual daily bars.
 * WEEK mode – last 8 calendar weeks as aggregated weekly bars.
 *
 * Each bar renders two layers:
 *   • Blue gradient tall bar  → session count  (Y-axis: 0 … max sessions)
 *   • Amber narrow stripe     → total hits     (Y-axis: 0 … max hits, independently scaled)
 *
 * A dashed "average sessions" guide-line crosses the chart.
 * Tap (or drag) a bar to reveal a stats tooltip.
 */
class HistoryBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Period { DAY, WEEK }

    // ── Model ─────────────────────────────────────────────────────────────────

    private data class BarEntry(
        val label: String,
        val sessions: Int,
        val hits: Int,
        val totalDurationMs: Long,
        val avgDrainPct: Float,
        val isHighlighted: Boolean,  // today / this week
        val dateMs: Long             // UTC start-of-period for tooltip header
    )

    private var rawDaily: List<DailyStats> = emptyList()
    private var period: Period = Period.DAY
    private var bars: List<BarEntry> = emptyList()

    // Animation: parallel fraction per bar, clamped [0..1]
    private var animFracs: FloatArray = FloatArray(0)
    private var animator: ValueAnimator? = null

    // Touch state
    private var selectedIdx: Int = -1

    // Density shorthand
    private val dp = context.resources.displayMetrics.density

    // ── Pre-allocated draw objects (never allocate inside onDraw) ─────────────

    private val sessionBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hitStripePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#14FFFFFF")
    }
    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.STROKE
    }
    private val gridLinePaint = Paint().apply {
        color = Color.parseColor("#15FFFFFF")
        strokeWidth = 1f
    }
    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textAlign = Paint.Align.CENTER
    }
    private val xLabelActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44B2FF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48484A")
        textAlign = Paint.Align.RIGHT
    }
    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48484A")
        textAlign = Paint.Align.CENTER
    }
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0111113")
        style = Paint.Style.FILL
    }
    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D3D3F")
        style = Paint.Style.STROKE
    }
    private val tooltipTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tooltipValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.RIGHT
    }
    private val tooltipLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
    }
    private val tooltipDividerPaint = Paint().apply {
        color = Color.parseColor("#2C2C2E")
        strokeWidth = 1f
    }

    // Reusable geometry objects
    private val barRect     = RectF()
    private val selRect     = RectF()
    private val tooltipRect = RectF()

    init {
        xLabelPaint.textSize       = 9.5f * dp
        xLabelActivePaint.textSize = 9.5f * dp
        yLabelPaint.textSize       = 9f   * dp
        noDataPaint.textSize       = 12f  * dp
        tooltipTitlePaint.textSize = 12f  * dp
        tooltipValuePaint.textSize = 11f  * dp
        tooltipLabelPaint.textSize = 10f  * dp
        avgLinePaint.strokeWidth   = 1f   * dp
        avgLinePaint.pathEffect    = DashPathEffect(floatArrayOf(5f * dp, 4f * dp), 0f)
        tooltipBorderPaint.strokeWidth = 1f * dp
        gridLinePaint.strokeWidth  = 1f
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setData(daily: List<DailyStats>, period: Period) {
        this.rawDaily = daily
        this.period   = period
        buildBars()
        startAnimation()
        invalidate()
    }

    // ── Data processing ───────────────────────────────────────────────────────

    private fun utcDay(ms: Long): Long = ms / (24L * 3_600_000L)

    private fun buildBars() {
        val now      = System.currentTimeMillis()
        val todayDay = utcDay(now)
        val cal      = Calendar.getInstance()

        if (period == Period.DAY) {
            // Map daily stats by their UTC epoch day
            val map = rawDaily.associateBy { utcDay(it.dayStartMs) }
            val dayAbbr = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

            bars = (6 downTo 0).map { offset ->
                val epochDay = todayDay - offset
                val ms       = epochDay * 24L * 3_600_000L
                val d        = map[epochDay]
                cal.timeInMillis = ms
                BarEntry(
                    label           = if (offset == 0) "Today"
                                      else dayAbbr[cal.get(Calendar.DAY_OF_WEEK) - 1],
                    sessions        = d?.sessionCount ?: 0,
                    hits            = d?.totalHits ?: 0,
                    totalDurationMs = d?.totalDurationMs ?: 0L,
                    avgDrainPct     = d?.avgBatteryDrainPct ?: 0f,
                    isHighlighted   = offset == 0,
                    dateMs          = ms
                )
            }
        } else {
            // 8 calendar weeks, each = 7 UTC days; rightmost bar = current (partial) week
            bars = (7 downTo 0).map { weekOffset ->
                val weekEndDay   = todayDay - weekOffset * 7L
                val weekStartDay = weekEndDay - 6L
                val startMs      = weekStartDay * 24L * 3_600_000L
                val endMs        = (weekEndDay + 1L) * 24L * 3_600_000L - 1L

                val inWeek = rawDaily.filter { it.dayStartMs in startMs..endMs }

                cal.timeInMillis = startMs
                val mon = cal.get(Calendar.MONTH) + 1
                val dom = cal.get(Calendar.DAY_OF_MONTH)

                BarEntry(
                    label           = if (weekOffset == 0) "Now" else "$mon/$dom",
                    sessions        = inWeek.sumOf { it.sessionCount },
                    hits            = inWeek.sumOf { it.totalHits },
                    totalDurationMs = inWeek.sumOf { it.totalDurationMs },
                    avgDrainPct     = if (inWeek.isNotEmpty())
                        inWeek.map { it.avgBatteryDrainPct }.average().toFloat() else 0f,
                    isHighlighted   = weekOffset == 0,
                    dateMs          = startMs
                )
            }
        }

        animFracs = FloatArray(bars.size) { 0f }
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { va ->
                val t = va.animatedFraction
                for (i in animFracs.indices) animFracs[i] = t
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bars.isEmpty()) return false
        val padL   = 28f * dp
        val padR   = 12f * dp
        val chartW = width - padL - padR
        if (chartW <= 0f) return false
        val slotW  = chartW / bars.size

        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val idx = ((event.x - padL) / slotW).toInt().coerceIn(0, bars.size - 1)
                if (selectedIdx != idx) { selectedIdx = idx; invalidate() }
                parent.requestDisallowInterceptTouchEvent(true)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padL = 28f * dp
        val padR = 12f * dp
        val padT = 12f * dp
        val padB = 26f * dp

        val cw = width  - padL - padR
        val ch = height - padT - padB

        if (cw <= 0f || ch <= 0f) return

        // No-data state
        if (bars.isEmpty() || bars.all { it.sessions == 0 }) {
            val msg = if (period == Period.DAY) "No sessions in the last 7 days"
                      else "No sessions in the last 8 weeks"
            canvas.drawText(msg, padL + cw / 2f, padT + ch / 2f + noDataPaint.textSize / 3f, noDataPaint)
            return
        }

        val maxSessions = bars.maxOf { it.sessions }.coerceAtLeast(1)
        val maxHits     = bars.maxOf { it.hits }.coerceAtLeast(1)
        val avgSess     = bars.map { it.sessions }.average().toFloat()
        val n           = bars.size
        val slotW       = cw / n
        val barW        = slotW * 0.50f
        val hitW        = (slotW * 0.20f).coerceAtLeast(2f * dp)
        val barCr       = (5f * dp).coerceAtMost(barW / 2f)
        val hitCr       = (3f * dp).coerceAtMost(hitW / 2f)

        // ── Horizontal grid lines ─────────────────────────────────────────────

        val gridVals = when {
            maxSessions <= 3  -> listOf(0, maxSessions)
            maxSessions <= 6  -> listOf(0, maxSessions / 2, maxSessions)
            else              -> listOf(0, maxSessions / 4, maxSessions / 2, (maxSessions * 3) / 4, maxSessions)
        }
        for (gv in gridVals) {
            val gy = padT + ch - (gv.toFloat() / maxSessions) * ch
            canvas.drawLine(padL, gy, padL + cw, gy, gridLinePaint)
            if (gv > 0) {
                canvas.drawText(
                    "$gv",
                    padL - 4f * dp,
                    gy + yLabelPaint.textSize / 3f,
                    yLabelPaint
                )
            }
        }

        // ── Average guide line ────────────────────────────────────────────────

        val avgY = padT + ch - (avgSess / maxSessions) * ch
        canvas.drawLine(padL, avgY, padL + cw, avgY, avgLinePaint)

        // ── Bars ──────────────────────────────────────────────────────────────

        for (i in bars.indices) {
            val bar  = bars[i]
            val anim = if (i < animFracs.size) animFracs[i] else 1f
            val cx   = padL + i * slotW + slotW / 2f

            // Selection column highlight (drawn before everything else for this slot)
            if (i == selectedIdx) {
                selRect.set(
                    padL + i * slotW + 1f * dp, padT,
                    padL + (i + 1) * slotW - 1f * dp, padT + ch
                )
                canvas.drawRoundRect(selRect, 4f * dp, 4f * dp, selectionPaint)
            }

            // X-axis label
            canvas.drawText(
                bar.label,
                cx,
                padT + ch + padB - 4f * dp,
                if (bar.isHighlighted) xLabelActivePaint else xLabelPaint
            )

            if (bar.sessions == 0) continue  // nothing to draw for empty day

            // ── Session bar (blue gradient) ──────────────────────────────────

            val sessFrac = (bar.sessions.toFloat() / maxSessions) * anim
            val sessH    = sessFrac * ch
            val barL     = cx - barW / 2f
            val barR     = cx + barW / 2f
            val barT     = padT + ch - sessH
            val barB     = padT + ch

            sessionBarPaint.shader = LinearGradient(
                barL, barT, barL, barB,
                if (bar.isHighlighted) Color.parseColor("#55CCFF")
                else                   Color.parseColor("#1A8FFF"),
                if (bar.isHighlighted) Color.parseColor("#0A72E6")
                else                   Color.parseColor("#0A52AD"),
                Shader.TileMode.CLAMP
            )

            val effectiveCr = barCr.coerceAtMost(sessH / 2f)
            barRect.set(barL, barT, barR, barB)
            canvas.drawRoundRect(barRect, effectiveCr, effectiveCr, sessionBarPaint)
            // Fill in the rounded bottom so it sits flush with the axis
            if (sessH > effectiveCr) {
                barRect.set(barL, barT + effectiveCr, barR, barB)
                canvas.drawRect(barRect, sessionBarPaint)
            }

            // ── Hit stripe (amber, narrow, centred, independent Y-scale) ─────

            if (bar.hits > 0) {
                val hitFrac = (bar.hits.toFloat() / maxHits) * anim
                val hitH    = hitFrac * ch
                val hitL    = cx - hitW / 2f
                val hitR    = cx + hitW / 2f
                val hitT    = padT + ch - hitH
                val hitB    = padT + ch

                hitStripePaint.shader = LinearGradient(
                    hitL, hitT, hitL, hitB,
                    Color.parseColor("#FFC535"),
                    Color.parseColor("#C87A00"),
                    Shader.TileMode.CLAMP
                )

                val effHitCr = hitCr.coerceAtMost(hitH / 2f)
                barRect.set(hitL, hitT, hitR, hitB)
                canvas.drawRoundRect(barRect, effHitCr, effHitCr, hitStripePaint)
                if (hitH > effHitCr) {
                    barRect.set(hitL, hitT + effHitCr, hitR, hitB)
                    canvas.drawRect(barRect, hitStripePaint)
                }
            }
        }

        // ── Tooltip ───────────────────────────────────────────────────────────

        val sel = selectedIdx
        if (sel in bars.indices && bars[sel].sessions > 0) {
            drawTooltip(canvas, sel, bars[sel], slotW, padL, padT, cw, ch)
        }
    }

    private fun drawTooltip(
        canvas: Canvas,
        idx: Int,
        bar: BarEntry,
        slotW: Float,
        padL: Float, padT: Float,
        cw: Float, @Suppress("UNUSED_PARAMETER") ch: Float
    ) {
        val lines = buildList<Pair<String, String>> {
            add("Sessions" to "${bar.sessions}")
            if (bar.hits > 0) add("Hits" to "${bar.hits}")
            if (bar.hits > 0 && bar.sessions > 0)
                add("Hits / session" to "%.1f".format(bar.hits.toFloat() / bar.sessions))
            if (bar.totalDurationMs > 0) {
                val totalMin = bar.totalDurationMs / 60_000L
                add("Total time" to if (totalMin >= 60)
                    "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m")
            }
            if (bar.avgDrainPct > 0f)
                add("Avg drain" to "%.1f%%".format(bar.avgDrainPct))
        }
        if (lines.isEmpty()) return

        val cal    = Calendar.getInstance().apply { timeInMillis = bar.dateMs }
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val days   = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")

        val title = when {
            period == Period.DAY && bar.isHighlighted -> "Today"
            period == Period.DAY -> "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}, " +
                "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
            bar.isHighlighted   -> "This Week"
            else                -> "Wk of ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
        }

        val lineH  = 15f * dp
        val padH   = 10f * dp
        val padV   = 8f  * dp
        val titleH = 17f * dp
        val tW     = 148f * dp
        val tH     = padV * 2 + titleH + lines.size * lineH + 2f * dp

        val cx = padL + idx * slotW + slotW / 2f
        val tx = (cx - tW / 2f).coerceIn(padL, padL + cw - tW)
        val ty = padT + 4f * dp

        tooltipRect.set(tx, ty, tx + tW, ty + tH)
        canvas.drawRoundRect(tooltipRect, 8f * dp, 8f * dp, tooltipBgPaint)
        canvas.drawRoundRect(tooltipRect, 8f * dp, 8f * dp, tooltipBorderPaint)

        // Title
        canvas.drawText(title, tx + padH, ty + padV + titleH - 3f * dp, tooltipTitlePaint)

        // Divider
        val divY = ty + padV + titleH + 0.5f * dp
        canvas.drawLine(tx + padH, divY, tx + tW - padH, divY, tooltipDividerPaint)

        // Rows
        lines.forEachIndexed { i, (label, value) ->
            val ly = ty + padV + titleH + 2f * dp + (i + 1) * lineH - 2f * dp
            canvas.drawText(label, tx + padH, ly, tooltipLabelPaint)
            canvas.drawText(value, tx + tW - padH, ly, tooltipValuePaint)
        }
    }
}
