package com.sbtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Centralized notification channel management for SBTracker.
 * All notification channels are defined and registered here.
 */
object NotificationChannels {

    // Channel IDs
    const val STATUS = "sb_status"
    const val ALERTS = "sb_alerts"
    const val CONTROLS = "sb_controls"

    /**
     * Creates and registers all notification channels with the system.
     * Safe to call multiple times (createNotificationChannel is idempotent on Android 8.0+).
     *
     * @param context Context used to get NotificationManager
     */
    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Device Status channel — persistent status updates, low importance
            val statusChannel = NotificationChannel(
                STATUS,
                "Device Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status notifications for device tracking"
            }
            manager.createNotificationChannel(statusChannel)

            // Device Alerts channel — urgent notifications, high importance
            val alertsChannel = NotificationChannel(
                ALERTS,
                "Device Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vibrations and notifications for temperature and charging events"
            }
            manager.createNotificationChannel(alertsChannel)

            // Quick Controls channel — default importance for interactive controls
            val controlsChannel = NotificationChannel(
                CONTROLS,
                "Quick Controls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Quick action controls from notifications"
            }
            manager.createNotificationChannel(controlsChannel)
        }
    }
}
