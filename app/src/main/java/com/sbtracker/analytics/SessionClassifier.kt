package com.sbtracker.analytics

import com.sbtracker.data.SessionSummary

data class SessionClassification(
    val key: String,
    val label: String,
    val detail: String
)

object SessionClassifier {

    fun classify(summary: SessionSummary): SessionClassification {
        val durationMin = summary.durationMs / 60_000.0
        val totalHitSec = summary.totalHitDurationMs / 1000.0

        return when {
            summary.hitCount == 0 && summary.batteryConsumed <= 0 && durationMin < 2.0 -> SessionClassification(
                key = "check",
                label = "Quick Check",
                detail = "Heater toggled briefly with no real session signal."
            )

            summary.hitCount == 0 && summary.batteryConsumed > 0 -> SessionClassification(
                key = "warmup_only",
                label = "Warm-Up Only",
                detail = "Battery was used but no hit was detected."
            )

            summary.hitCount <= 2 && durationMin < 4.0 -> SessionClassification(
                key = "quick",
                label = "Quick Session",
                detail = "Short run with only a couple of detected hits."
            )

            summary.hitCount >= 20 || totalHitSec >= 120.0 || summary.batteryConsumed >= 20 -> SessionClassification(
                key = "heavy",
                label = "Heavy Session",
                detail = "Longer or more intense session with higher battery use."
            )

            summary.hitCount >= 10 || durationMin >= 7.0 -> SessionClassification(
                key = "full",
                label = "Full Session",
                detail = "A complete session with sustained use."
            )

            else -> SessionClassification(
                key = "steady",
                label = "Steady Session",
                detail = "A normal session without extreme duration or drain."
            )
        }
    }
}
