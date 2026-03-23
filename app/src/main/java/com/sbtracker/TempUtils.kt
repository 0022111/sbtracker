package com.sbtracker

import kotlin.math.roundToInt

/**
 * Convert an absolute temperature stored in °C to the user's display unit.
 *   180°C → 180  (Celsius)
 *   180°C → 356  (Fahrenheit)
 */
fun Int.toDisplayTemp(isCelsius: Boolean): Int =
    if (isCelsius) this else (this * 9.0 / 5.0 + 32).roundToInt()

/**
 * Convert a temperature *delta* stored in °C to the display unit.
 * Used for boost/superboost offsets where 0°C = 0°F (no +32 shift).
 *   +5°C → +5   (Celsius)
 *   +5°C → +9   (Fahrenheit, rounded)
 */
fun Int.toDisplayTempDelta(isCelsius: Boolean): Int =
    if (isCelsius) this else (this * 9.0 / 5.0).roundToInt()

/** Degree symbol + unit letter: "°C" or "°F". */
fun Boolean.unitSuffix(): String = if (this) "°C" else "°F"
