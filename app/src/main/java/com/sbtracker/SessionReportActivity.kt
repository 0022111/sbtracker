package com.sbtracker

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sbtracker.data.AppDatabase
import com.sbtracker.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full detail view for a single completed session.
 *
 * Accepts only a [session_id] Intent extra.  Every stat and graph data point is
 * loaded from the database, so this view is always consistent with the current
 * hit-detection algorithm — there is no separate path for stats vs. graph data.
 */
@AndroidEntryPoint
class SessionReportActivity : AppCompatActivity() {

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var prefsRepo: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_report)

        val sessionId = intent.getLongExtra("session_id", -1L)
        // We observe the flow but since we only need it once for the report, 
        // we can just use the first value or collect in lifecycleScope.
        
        val hitLogView  = findViewById<TextView>(R.id.report_tv_hit_log)
        val graph       = findViewById<SessionGraphView>(R.id.report_graph)
        val btnCapsule  = findViewById<Button>(R.id.report_btn_capsule)
        val btnFreePack = findViewById<Button>(R.id.report_btn_free_pack)

        lifecycleScope.launch {
            val prefs = prefsRepo.userPreferencesFlow.first()
            val isCelsius = prefs.isCelsius

            val session = withContext(Dispatchers.IO) { db.sessionDao().getById(sessionId) }
            if (session == null) {
                // Session was deleted between tap and load — just close.
                finish()
                return@launch
            }

            // Load all data concurrently from a single IO context.
            val (points, hits, hitStats, startBat, endBat, peakTempC, firstSetpointMs,
                 startRuntime, endRuntime) = withContext(Dispatchers.IO) {
                val p   = db.deviceStatusDao().getStatusForRange(
                    session.deviceAddress, session.startTimeMs, session.endTimeMs
                )
                val h   = db.hitDao().getHitsForSession(session.id)
                val hs  = db.hitDao().getHitStatsForSession(session.id)
                val sb  = db.deviceStatusDao().getBatteryAtStart(session.deviceAddress, session.startTimeMs, session.endTimeMs) ?: 0
                val eb  = db.deviceStatusDao().getBatteryAtEnd(session.deviceAddress, session.endTimeMs) ?: 0
                val pt  = db.deviceStatusDao().getPeakTempForRange(
                    session.deviceAddress, session.startTimeMs, session.endTimeMs
                ) ?: 0
                val fsMs = db.deviceStatusDao().getFirstSetpointReachedMs(
                    session.deviceAddress, session.startTimeMs, session.endTimeMs
                )
                val sr  = db.extendedDataDao().getHeaterRuntime(session.deviceAddress) ?: 0
                val er  = db.extendedDataDao().getHeaterRuntime(session.deviceAddress) ?: 0
                // Pack into a data holder to return multiple values from withContext.
                SessionData(p, h, hs, sb, eb, pt, fsMs, sr, er)
            }

            val durationMs      = session.durationMs
            val heatUpTimeMs    = if (firstSetpointMs != null) firstSetpointMs - session.startTimeMs else 0L
            val heaterWearMin   = (endRuntime - startRuntime).coerceAtLeast(0)
            val batteryConsumed = (startBat - endBat).coerceAtLeast(0)

            // ── Basic stats ──────────────────────────────────────────────────────
            findViewById<TextView>(R.id.report_tv_hits).text     = hitStats.hitCount.toString()
            findViewById<TextView>(R.id.report_tv_duration).text = formatDuration(durationMs / 1000)
            findViewById<TextView>(R.id.report_tv_drain).text    = "-${batteryConsumed}%"

            // ── Technical data ───────────────────────────────────────────────────
            findViewById<TextView>(R.id.report_tv_peak_temp).text =
                if (peakTempC > 0) "${peakTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}" else "---"
            findViewById<TextView>(R.id.report_tv_latency).text     = "${heatUpTimeMs / 1000}s"
            findViewById<TextView>(R.id.report_tv_active_hit).text  = "${hitStats.totalDurationMs / 1000}s"
            findViewById<TextView>(R.id.report_tv_battery_range).text = "${startBat}% → ${endBat}%"
            findViewById<TextView>(R.id.report_tv_wear).text        = "+${heaterWearMin} min"

            // ── Date ─────────────────────────────────────────────────────────────
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            findViewById<TextView>(R.id.report_tv_date).text = sdf.format(Date(session.startTimeMs)).uppercase()

            // ── Hit log ──────────────────────────────────────────────────────────
            hitLogView.text = if (hits.isEmpty()) {
                "No individual hit data"
            } else {
                hits.mapIndexed { i, h ->
                    val durSec    = h.durationMs / 1000
                    val offsetSec = (h.startTimeMs - session.startTimeMs) / 1000
                    val tempStr   = if (h.peakTempC > 0) " @ ${h.peakTempC.toDisplayTemp(isCelsius)}${isCelsius.unitSuffix()}" else ""
                    "HIT ${i + 1}: ${durSec}s${tempStr}  (${formatDuration(offsetSec)})"
                }.joinToString("\n")
            }

            // ── Graph ────────────────────────────────────────────────────────────
            val hitMarkers = hits.map { h ->
                SessionGraphView.HitMarker(h.startTimeMs, h.durationMs / 1000, h.peakTempC)
            }
            graph.setSessionData(points, hitMarkers, session.startTimeMs, session.endTimeMs, isCelsius)
        }

        lifecycleScope.launch {
            db.sessionMetadataDao().observeMetadataForSession(sessionId).collect { meta ->
                val isCapsule = meta?.isCapsule ?: false
                btnCapsule.isEnabled  = !isCapsule
                btnFreePack.isEnabled = isCapsule
                btnCapsule.alpha  = if (isCapsule) 1.0f else 0.5f
                btnFreePack.alpha = if (!isCapsule) 1.0f else 0.5f
            }
        }

        btnCapsule.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.sessionMetadataDao().insertOrUpdate(
                    com.sbtracker.data.SessionMetadata(sessionId = sessionId, isCapsule = true)
                )
            }
        }

        btnFreePack.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.sessionMetadataDao().insertOrUpdate(
                    com.sbtracker.data.SessionMetadata(sessionId = sessionId, isCapsule = false)
                )
            }
        }

        findViewById<Button>(R.id.report_btn_close).setOnClickListener { finish() }
    }

    /** Scratch holder so withContext can return multiple values without a Pair nest. */
    private data class SessionData(
        val points:         List<com.sbtracker.data.DeviceStatus>,
        val hits:           List<com.sbtracker.data.Hit>,
        val hitStats:       com.sbtracker.data.HitStats,
        val startBat:       Int,
        val endBat:         Int,
        val peakTempC:      Int,
        val firstSetpointMs: Long?,
        val startRuntime:   Int,
        val endRuntime:     Int
    )

    private fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
