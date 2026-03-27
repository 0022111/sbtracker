package com.sbtracker

import com.sbtracker.data.DeviceStatus
import com.sbtracker.SessionTracker
import com.sbtracker.analytics.SessionClassifier
import org.json.JSONObject

/**
 * Maps native telemetry objects to JSON for the React UI.
 */
object TelemetryMapper {

    fun toJson(
        status: DeviceStatus?,
        state: BleManager.ConnectionState,
        stats: SessionTracker.SessionStats,
        displaySettings: DisplaySettings? = null,
        firmware: String? = null,
        batteryInsights: com.sbtracker.analytics.BatteryInsights? = null,
        personalRecords: com.sbtracker.analytics.PersonalRecords? = null,
        usageInsights: com.sbtracker.analytics.UsageInsights? = null,
        dailyStats: List<com.sbtracker.analytics.DailyStats>? = null,
        historyStats: com.sbtracker.analytics.HistoryStats? = null,
        extended: com.sbtracker.data.ExtendedData? = null,
        intake: com.sbtracker.analytics.IntakeStats? = null
    ): String {
        val root = JSONObject()

        extended?.let { e ->
            val ext = JSONObject()
            ext.put("heaterRuntimeMinutes", e.heaterRuntimeMinutes)
            ext.put("batteryChargingTimeMinutes", e.batteryChargingTimeMinutes)
            root.put("extended", ext)
        }
        
        root.put("connectionState", state.javaClass.simpleName)
        if (state is BleManager.ConnectionState.Reconnecting) {
            root.put("reconnectAttempt", state.attempt)
        }

        firmware?.let { root.put("firmwareVersion", it) }

        status?.let { s ->
            val statusObj = JSONObject()
            statusObj.put("deviceAddress", s.deviceAddress)
            statusObj.put("deviceType", s.deviceType)
            statusObj.put("currentTempC", s.currentTempC)
            statusObj.put("targetTempC", s.targetTempC)
            statusObj.put("batteryLevel", s.batteryLevel)
            statusObj.put("heaterMode", s.heaterMode)
            statusObj.put("isCharging", s.isCharging)
            statusObj.put("setpointReached", s.setpointReached)
            statusObj.put("isCelsius", s.isCelsius)
            statusObj.put("autoShutdownSeconds", s.autoShutdownSeconds)
            statusObj.put("boostOffsetC", s.boostOffsetC)
            statusObj.put("superBoostOffsetC", s.superBoostOffsetC)
            
            statusObj.put("vibrationEnabled", s.vibrationEnabled)
            statusObj.put("chargeCurrentOptimization", s.chargeCurrentOptimization)
            statusObj.put("chargeVoltageLimit", s.chargeVoltageLimit)
            statusObj.put("permanentBluetooth", s.permanentBluetooth)
            statusObj.put("boostVisualization", s.boostVisualization)
            statusObj.put("isSynthetic", s.isSynthetic)
            
            root.put("status", statusObj)
        }

        displaySettings?.let { ds ->
            val dsObj = JSONObject()
            dsObj.put("brightness", ds.brightness)
            dsObj.put("vibrationLevel", ds.vibrationLevel)
            dsObj.put("boostTimeout", ds.boostTimeout)
            root.put("displaySettings", dsObj)
        }

        val statsObj = JSONObject()
        statsObj.put("durationSeconds", stats.durationSeconds)
        statsObj.put("hitCount", stats.hitCount)
        statsObj.put("isHitActive", stats.isHitActive)
        statsObj.put("currentHitDurationSec", stats.currentHitDurationSec)
        statsObj.put("avgHitDurationSec", stats.avgHitDurationSec)
        statsObj.put("chargeEtaMinutes", stats.chargeEtaMinutes ?: JSONObject.NULL)
        statsObj.put("chargeEta80Minutes", stats.chargeEta80Minutes ?: JSONObject.NULL)
        statsObj.put("sessionsRemaining", stats.sessionsRemaining)
        statsObj.put("sessionsToCritical", stats.sessionsToCritical)
        statsObj.put("drainEstimateReliable", stats.drainEstimateReliable)
        statsObj.put("sessionsRemainingLow", stats.sessionsRemainingLow)
        statsObj.put("sessionsRemainingHigh", stats.sessionsRemainingHigh)
        statsObj.put("chargeRatePctPerMin", stats.chargeRatePctPerMin)
        root.put("stats", statsObj)

        batteryInsights?.let { bi ->
            val insights = JSONObject()
            insights.put("avgDrainRecent", bi.recentAvgDrain)
            insights.put("avgDrainAll", bi.allTimeAvgDrain)
            insights.put("drainTrend", bi.drainTrend)
            insights.put("drainStdDev", bi.drainStdDev)
            insights.put("medianDrain", bi.medianDrain)
            insights.put("sessionsPerCycle", bi.sessionsPerChargeCycle)
            insights.put("avgChargeTime", bi.avgChargeDurationMin)
            insights.put("avgBatteryGainedPct", bi.avgBatteryGainedPct)
            insights.put("longestRunSessions", bi.longestRunSessions)
            insights.put("avgDepthOfDischarge", bi.avgDepthOfDischarge)
            insights.put("avgDaysPerChargeCycle", bi.avgDaysPerChargeCycle ?: JSONObject.NULL)
            root.put("batteryInsights", insights)
        }

        personalRecords?.let { pr ->
            val records = JSONObject()
            records.put("maxHits", pr.maxHitsEver)
            records.put("maxTemp", pr.maxPeakTempC)
            records.put("maxDuration", pr.maxDurationMs)
            root.put("personalRecords", records)
        }

        usageInsights?.let { ui ->
            val usage = JSONObject()
            usage.put("currentStreak", ui.currentStreakDays)
            usage.put("longestStreak", ui.longestStreakDays)
            usage.put("sessionsThisWeek", ui.sessionsThisWeek)
            usage.put("sessionsLastWeek", ui.sessionsLastWeek)
            usage.put("hitsThisWeek", ui.hitsThisWeek)
            usage.put("hitsLastWeek", ui.hitsLastWeek)
            usage.put("peakTimeOfDay", ui.peakTimeOfDay)
            usage.put("busiestDayOfWeek", ui.busiestDayOfWeek)
            usage.put("avgHitsPerMinute", ui.avgHitsPerMinute)
            usage.put("avgSessionsPerDay", ui.avgSessionsPerDay)
            usage.put("totalDaysActive", ui.totalDaysActive)
            root.put("usageInsights", usage)
        }

        intake?.let { itk ->
            val intakeObj = JSONObject()
            intakeObj.put("totalGramsAllTime", itk.totalGramsAllTime)
            intakeObj.put("totalGramsThisWeek", itk.totalGramsThisWeek)
            intakeObj.put("totalGramsThisMonth", itk.totalGramsThisMonth)
            intakeObj.put("avgGramsPerSession", itk.avgGramsPerSession)
            intakeObj.put("capsuleCount", itk.capsuleSessionCount)
            intakeObj.put("freePackCount", itk.freePackSessionCount)
            root.put("intake", intakeObj)
        }

        dailyStats?.let { dsList ->
            val array = org.json.JSONArray()
            dsList.reversed().take(30).forEach { ds ->
                val obj = JSONObject()
                obj.put("dayStartMs", ds.dayStartMs)
                obj.put("sessionCount", ds.sessionCount)
                obj.put("totalHits", ds.totalHits)
                obj.put("avgDrain", ds.avgBatteryDrainPct)
                array.put(obj)
            }
            root.put("dailyStats", array)
        }

        historyStats?.let { hs ->
            val hist = JSONObject()
            hist.put("avgHeatUpSec", hs.avgHeatUpTimeSec)
            hist.put("sessionsPerDay7d", hs.sessionsPerDay7d)
            hist.put("sessionsPerDay30d", hs.sessionsPerDay30d)
            hist.put("peakSessionsInDay", hs.peakSessionsInADay)
            hist.put("productiveSessionCount", hs.productiveSessionCount)
            hist.put("warmupOnlySessionCount", hs.warmupOnlySessionCount)
            hist.put("productiveSessionPct", hs.productiveSessionPct)
            hist.put("bestEfficiencyTempC", hs.bestEfficiencyTempC ?: JSONObject.NULL)
            hist.put("lowYieldTemp", hs.lowYieldTemp ?: JSONObject.NULL)
            val favTemps = org.json.JSONArray()
            hs.favoriteTempsCelsius.forEach { (temp, count) ->
                val pair = JSONObject()
                pair.put("temp", temp)
                pair.put("count", count)
                favTemps.put(pair)
            }
            hist.put("favoriteTemps", favTemps)
            root.put("historyStats", hist)
        }

        return root.toString()
    }

    fun programsToJson(programs: List<com.sbtracker.data.SessionProgram>): String {
        val root = JSONObject()
        root.put("type", "programs")
        val array = org.json.JSONArray()
        programs.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("targetTempC", p.targetTempC)
            obj.put("boostStepsJson", p.boostStepsJson)
            obj.put("isDefault", p.isDefault)
            array.put(obj)
        }
        root.put("data", array)
        return root.toString()
    }

    fun historyToJson(summaries: List<com.sbtracker.data.SessionSummary>): String {
        val root = JSONObject()
        root.put("type", "history")
        val array = org.json.JSONArray()
        summaries.forEach { s ->
            val classification = SessionClassifier.classify(s)
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("startTimeMs", s.startTimeMs)
            obj.put("endTimeMs", s.endTimeMs)
            obj.put("durationMs", s.durationMs)
            obj.put("hitCount", s.hitCount)
            obj.put("totalHitMs", s.totalHitDurationMs)
            obj.put("peakTempC", s.peakTempC)
            obj.put("avgTempC", s.avgTempC)
            obj.put("startBattery", s.startBattery)
            obj.put("endBattery", s.endBattery)
            obj.put("batteryConsumed", s.batteryConsumed)
            obj.put("heatUpTimeMs", s.heatUpTimeMs)
            obj.put("rating", s.rating ?: 0)
            obj.put("notes", s.notes ?: "")
            obj.put("sessionKind", classification.key)
            obj.put("sessionKindLabel", classification.label)
            obj.put("sessionKindDetail", classification.detail)
            array.put(obj)
        }
        root.put("data", array)
        return root.toString()
    }

    fun chargesToJson(charges: List<com.sbtracker.data.ChargeCycle>): String {
        val root = JSONObject()
        root.put("type", "charges")
        val array = org.json.JSONArray()
        charges.forEach { cycle ->
            val obj = JSONObject()
            obj.put("id", cycle.id)
            obj.put("startTimeMs", cycle.startTimeMs)
            obj.put("endTimeMs", cycle.endTimeMs)
            obj.put("startBattery", cycle.startBattery)
            obj.put("endBattery", cycle.endBattery)
            obj.put("durationMs", cycle.durationMs)
            obj.put("batteryGained", cycle.batteryGained)
            obj.put("avgRatePctPerMin", cycle.avgRatePctPerMin)
            obj.put("chargeVoltageLimit", cycle.chargeVoltageLimit)
            obj.put("chargeCurrentOptimization", cycle.chargeCurrentOptimization)
            array.put(obj)
        }
        root.put("data", array)
        return root.toString()
    }
}
