package com.sbtracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.sbtracker.data.ChargeCycle
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.Session

/**
 * Real-time sparkline graph for heater temperature and battery level.
 */
class GraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val TEMP_CEILING = 270f
    }

    private var dataPoints: List<DeviceStatus> = emptyList()
    private var sessions: List<Session> = emptyList()
    private var charges: List<ChargeCycle> = emptyList()
    private var isCelsius: Boolean = true

    private val tempReadyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158") // M3 Green
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val tempHeatingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9F0A") // M3 Orange
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val tempOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val battNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A84FF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val battLowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF453A")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val battChargingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val setpointPaint = Paint().apply {
        color = Color.parseColor("#555555")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 22f
    }

    private val axisPaint = Paint().apply {
        color = Color.parseColor("#2C2C2E")
        strokeWidth = 1f
    }

    private val markerSessionPaint = Paint().apply {
        color = Color.parseColor("#15FFD60A")
        style = Paint.Style.FILL
    }

    private val markerChargePaint = Paint().apply {
        color = Color.parseColor("#1530D158")
        style = Paint.Style.FILL
    }

    private val tempReadyPath   = Path()
    private val tempHeatingPath = Path()
    private val tempOffPath     = Path()

    fun setData(points: List<DeviceStatus>, sessions: List<Session> = emptyList(), charges: List<ChargeCycle> = emptyList(), isCelsius: Boolean = true) {
        this.dataPoints = points
        this.sessions = sessions
        this.charges = charges
        this.isCelsius = isCelsius
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padLeft = 10f
        val padRight = 10f
        val padTop = 10f
        val padBot = 20f

        val w = width.toFloat()  - padLeft - padRight
        val h = height.toFloat() - padTop  - padBot

        if (w <= 0f || h <= 0f) return

        // ── Draw Grid/Markers ───────────────────────────────────────────────
        val pts = dataPoints
        if (pts.size < 2) {
            canvas.drawText("Waiting for data…", padLeft + 10f, padTop + h / 2f, labelPaint)
            return
        }

        val ordered = pts.asReversed()
        val size = ordered.size
        val startTs = ordered.first().timestampMs
        val endTs = ordered.last().timestampMs
        val totalTs = (endTs - startTs).coerceAtLeast(1L)

        // Event Markers (Background)
        sessions.forEach { s ->
            if (s.endTimeMs >= startTs && s.startTimeMs <= endTs) {
                val xStart = padLeft + ((s.startTimeMs - startTs).toFloat() / totalTs).coerceIn(0f, 1f) * w
                val xEnd   = padLeft + ((s.endTimeMs - startTs).toFloat() / totalTs).coerceIn(0f, 1f) * w
                canvas.drawRect(xStart, padTop, xEnd, padTop + h, markerSessionPaint)
            }
        }

        charges.forEach { c ->
            if (c.endTimeMs >= startTs && c.startTimeMs <= endTs) {
                val xStart = padLeft + ((c.startTimeMs - startTs).toFloat() / totalTs).coerceIn(0f, 1f) * w
                val xEnd   = padLeft + ((c.endTimeMs - startTs).toFloat() / totalTs).coerceIn(0f, 1f) * w
                canvas.drawRect(xStart, padTop, xEnd, padTop + h, markerChargePaint)
            }
        }

        // Target Setpoint Guideline
        val latest = ordered.last()
        if (latest.heaterMode > 0) {
            val target = when (latest.heaterMode) {
                2    -> latest.targetTempC + latest.boostOffsetC
                3    -> latest.targetTempC + latest.superBoostOffsetC
                else -> latest.targetTempC
            }.toFloat().coerceIn(0f, TEMP_CEILING)
            val setY = padTop + h - (target / TEMP_CEILING) * h
            canvas.drawLine(padLeft, setY, padLeft + w, setY, setpointPaint)
        }

        // ── Draw Temperature Paths ──────────────────────────────────────────
        tempReadyPath.reset()
        tempHeatingPath.reset()
        tempOffPath.reset()
        
        var readyNeedsMove = true
        var heatingNeedsMove = true
        var offNeedsMove = true

        ordered.forEach { s ->
            val x = padLeft + ((s.timestampMs - startTs).toFloat() / totalTs) * w
            val tempY = padTop + h - (s.currentTempC.toFloat().coerceIn(0f, TEMP_CEILING) / TEMP_CEILING) * h

            when {
                s.heaterMode == 0 -> {
                    if (offNeedsMove) { tempOffPath.moveTo(x, tempY); offNeedsMove = false }
                    else tempOffPath.lineTo(x, tempY)
                    readyNeedsMove = true
                    heatingNeedsMove = true
                }
                s.setpointReached -> {
                    if (readyNeedsMove) { tempReadyPath.moveTo(x, tempY); readyNeedsMove = false }
                    else tempReadyPath.lineTo(x, tempY)
                    heatingNeedsMove = true
                    offNeedsMove = true
                }
                else -> {
                    if (heatingNeedsMove) { tempHeatingPath.moveTo(x, tempY); heatingNeedsMove = false }
                    else tempHeatingPath.lineTo(x, tempY)
                    readyNeedsMove = true
                    offNeedsMove = true
                }
            }
        }
        
        canvas.drawPath(tempOffPath, tempOffPaint)
        canvas.drawPath(tempHeatingPath, tempHeatingPaint)
        canvas.drawPath(tempReadyPath, tempReadyPaint)

        // ── Draw Battery ───────────────────────────────────────────────────
        for (i in 1 until size) {
            val prev = ordered[i - 1]
            val curr = ordered[i]
            val x0 = padLeft + ((prev.timestampMs - startTs).toFloat() / totalTs) * w
            val x1 = padLeft + ((curr.timestampMs - startTs).toFloat() / totalTs) * w
            val y0 = padTop + h - (prev.batteryLevel.toFloat() / 100f) * h
            val y1 = padTop + h - (curr.batteryLevel.toFloat() / 100f) * h
            
            val paint = when {
                curr.isCharging -> battChargingPaint
                curr.batteryLevel <= 20 -> battLowPaint
                else -> battNormalPaint
            }
            canvas.drawLine(x0, y0, x1, y1, paint)
        }

        // Labels
        val tempLabel = if (latest.heaterMode > 0) "${latest.currentTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}" else "${latest.currentTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()} (OFF)"
        canvas.drawText(tempLabel, padLeft + 4f, padTop + 24f, labelPaint)

        val battLabel = "${latest.batteryLevel}%"
        canvas.drawText(battLabel, padLeft + w - labelPaint.measureText(battLabel) - 4f, padTop + 24f, labelPaint)
    }
}
