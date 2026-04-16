package com.sbtracker.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formatting helpers — pure, no Android.
 */
object Format {

    private val HHMM  = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val DATE  = SimpleDateFormat("MMM d", Locale.getDefault())
    private val FULL  = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())

    fun temp(c: Int, useCelsius: Boolean): String =
        if (useCelsius) "$c°C" else "${(c * 9f / 5f + 32f).roundToInt()}°F"

    fun tempPlain(c: Int, useCelsius: Boolean): String =
        if (useCelsius) "$c" else "${(c * 9f / 5f + 32f).roundToInt()}"

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val h = m / 60
        return when {
            h > 0 -> "${h}h ${m % 60}m"
            m > 0 -> "${m}m ${s % 60}s"
            else  -> "${s}s"
        }
    }

    fun clock(ms: Long): String = HHMM.format(Date(ms))
    fun date(ms: Long):  String = DATE.format(Date(ms))
    fun full(ms: Long):  String = FULL.format(Date(ms))
}
