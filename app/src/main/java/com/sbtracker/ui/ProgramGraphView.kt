package com.sbtracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class ProgramGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF41.toInt()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2200FF41.toInt()
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3380A88F.toInt()
        strokeWidth = 2f
    }

    private var steps = listOf<Pair<Int, Int>>() // Time(s), Temp(C)

    fun setSteps(newSteps: List<Pair<Int, Int>>) {
        steps = newSteps
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (steps.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 20f

        // Calculate scales
        val maxTime = steps.sumOf { it.first }.toFloat().coerceAtLeast(180f)
        val minTemp = steps.minOf { it.second }.toFloat().coerceAtMost(160f) - 5f
        val maxTemp = steps.maxOf { it.second }.toFloat().coerceAtLeast(210f) + 5f
        val tempRange = maxTemp - minTemp

        fun getX(timeSec: Float) = padding + (timeSec / maxTime) * (w - 2 * padding)
        fun getY(temp: Float) = (h - padding) - ((temp - minTemp) / tempRange) * (h - 2 * padding)

        // Draw grid lines
        canvas.drawLine(0f, h-padding, w, h-padding, gridPaint) // Baseline
        
        // Draw path
        val path = Path()
        val fillPath = Path()
        
        var currentTime = 0f
        
        // Start point
        val startX = getX(0f)
        val startY = getY(steps[0].second.toFloat())
        path.moveTo(startX, startY)
        fillPath.moveTo(startX, h)
        fillPath.lineTo(startX, startY)

        steps.forEach { (duration, temp) ->
            currentTime += duration
            val x = getX(currentTime)
            val y = getY(temp.toFloat())
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(getX(currentTime), h)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
