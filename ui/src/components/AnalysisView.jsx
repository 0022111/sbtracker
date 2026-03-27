import React from 'react'
import { motion } from 'framer-motion'
import { Activity, BarChart3, Battery, CalendarDays, Flame, TrendingUp, Wind } from 'lucide-react'
import useStore from '../store/useStore'

const AnalysisView = () => {
  const { telemetry } = useStore()
  const { status, batteryInsights, usageInsights, intake, historyStats, dailyStats } = telemetry
  const isCelsius = status?.isCelsius ?? true

  const favoriteTemp = historyStats?.favoriteTemps?.[0]?.temp
  const weeklySessionDelta = (usageInsights?.sessionsThisWeek || 0) - (usageInsights?.sessionsLastWeek || 0)
  const weeklyHitsDelta = (usageInsights?.hitsThisWeek || 0) - (usageInsights?.hitsLastWeek || 0)
  const recentDays = Array.isArray(dailyStats) ? dailyStats.slice(-14) : []
  const maxSessions = Math.max(1, ...recentDays.map((day) => day.sessionCount || 0))
  const productivePct = historyStats?.productiveSessionPct
  const bestEfficiencyTemp = historyStats?.bestEfficiencyTempC
  const lowYieldTemp = historyStats?.lowYieldTemp

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">Insights</p>
        <h1 className="page-title">Patterns worth paying attention to</h1>
        <p className="page-subtitle">
          This screen is for trends that change decisions: when you use the device, how sessions are trending, and whether battery behavior is holding up.
        </p>
      </header>

      <div className="summary-grid">
        <InsightCard
          icon={<Flame size={16} />}
          label="Sessions this week"
          value={usageInsights?.sessionsThisWeek || 0}
          note={formatDelta(weeklySessionDelta, 'vs last week')}
        />
        <InsightCard
          icon={<Wind size={16} />}
          label="Hits this week"
          value={usageInsights?.hitsThisWeek || 0}
          note={formatDelta(weeklyHitsDelta, 'vs last week')}
        />
        <InsightCard
          icon={<TrendingUp size={16} />}
          label="Favorite temp"
          value={favoriteTemp ? `${displayTemp(favoriteTemp, isCelsius)}°${isCelsius ? 'C' : 'F'}` : '—'}
          note={historyStats?.favoriteTemps?.[0]?.count ? `${historyStats.favoriteTemps[0].count} sessions` : 'Still learning'}
        />
        <InsightCard
          icon={<Battery size={16} />}
          label="Recent drain"
          value={batteryInsights?.avgDrainRecent ? `${batteryInsights.avgDrainRecent.toFixed(1)}%` : '—'}
          note={formatTrend(batteryInsights?.drainTrend)}
        />
      </div>

      <div className="glass-card future-glass" style={{ padding: '18px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', alignItems: 'center', marginBottom: '14px' }}>
          <div>
            <div className="section-heading" style={{ marginBottom: '8px' }}>Recent activity</div>
            <div style={{ fontSize: '14px', color: 'var(--text-muted)' }}>Last 14 days of detected sessions</div>
          </div>
          <span className="pill"><BarChart3 size={12} /> {recentDays.length} days</span>
        </div>

        {recentDays.length ? (
          <div className="daily-bars">
            {recentDays.map((day) => (
              <div key={day.dayStartMs} className="daily-bar">
                <div
                  className="daily-bar-fill"
                  style={{ height: `${Math.max(10, ((day.sessionCount || 0) / maxSessions) * 90)}px` }}
                  title={`${day.sessionCount || 0} sessions`}
                />
                <div className="daily-bar-label">
                  {new Date(day.dayStartMs).toLocaleDateString(undefined, { weekday: 'narrow' })}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ fontSize: '13px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
            Daily activity bars will appear once enough session history has been rebuilt.
          </div>
        )}
      </div>

      <div className="split-layout">
        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Usage patterns</div>
          <InfoRow label="Peak time" value={getPeakTimeLabel(usageInsights?.peakTimeOfDay)} />
          <InfoRow label="Busiest day" value={getDayLabel(usageInsights?.busiestDayOfWeek)} />
          <InfoRow label="Current streak" value={`${usageInsights?.currentStreak || 0} days`} />
          <InfoRow label="Longest streak" value={`${usageInsights?.longestStreak || 0} days`} />
          <InfoRow label="Active days" value={usageInsights?.totalDaysActive || '—'} />
          <InfoRow label="Avg sessions per active day" value={formatMaybeNumber(usageInsights?.avgSessionsPerDay)} />
        </div>

        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Session quality</div>
          <InfoRow label="Avg heat-up" value={historyStats?.avgHeatUpSec ? `${historyStats.avgHeatUpSec}s` : '—'} />
          <InfoRow label="Hits per minute" value={formatMaybeNumber(usageInsights?.avgHitsPerMinute)} />
          <InfoRow label="Sessions per day (7d)" value={formatMaybeNumber(historyStats?.sessionsPerDay7d)} />
          <InfoRow label="Sessions per day (30d)" value={formatMaybeNumber(historyStats?.sessionsPerDay30d)} />
          <InfoRow label="Peak sessions in a day" value={historyStats?.peakSessionsInDay || '—'} />
        </div>
      </div>

      <div className="split-layout">
        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>What history suggests</div>
          <InfoRow label="Productive sessions" value={productivePct ? `${productivePct.toFixed(0)}%` : '—'} />
          <InfoRow label="Warm-up only runs" value={historyStats?.warmupOnlySessionCount || '—'} />
          <InfoRow label="Productive runs" value={historyStats?.productiveSessionCount || '—'} />
          <InfoRow
            label="Best return temp"
            value={bestEfficiencyTemp ? `${displayTemp(bestEfficiencyTemp, isCelsius)}°${isCelsius ? 'C' : 'F'}` : 'Need more history'}
          />
          <InfoRow
            label="Low-yield temp"
            value={lowYieldTemp ? `${displayTemp(lowYieldTemp, isCelsius)}°${isCelsius ? 'C' : 'F'}` : 'No repeated pattern'}
          />
          <div style={{ marginTop: '12px', fontSize: '12px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
            Productive means at least one detected hit. Warm-up only means the heater consumed battery but no hit was detected, which usually points to aborted starts or low-yield temperature choices.
          </div>
        </div>

        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Dose context</div>
          <InfoRow label="Total used" value={`${(intake?.totalGramsAllTime || 0).toFixed(2)}g`} />
          <InfoRow label="This week" value={`${(intake?.totalGramsThisWeek || 0).toFixed(2)}g`} />
          <InfoRow label="This month" value={`${(intake?.totalGramsThisMonth || 0).toFixed(2)}g`} />
          <InfoRow label="Avg per session" value={`${(intake?.avgGramsPerSession || 0).toFixed(2)}g`} />
        </div>
      </div>

      <div className="glass-card" style={{ padding: '18px' }}>
        <div className="section-heading" style={{ marginBottom: '12px' }}>Battery trend</div>
        <div className="split-layout">
          <div>
            <InfoRow label="Drain trend" value={formatTrend(batteryInsights?.drainTrend)} />
            <InfoRow label="Median drain" value={batteryInsights?.medianDrain ? `${batteryInsights.medianDrain.toFixed(1)}%` : '—'} />
            <InfoRow label="Drain consistency" value={batteryInsights?.drainStdDev ? `±${batteryInsights.drainStdDev.toFixed(1)}%` : '—'} />
          </div>
          <div>
            <InfoRow label="Sessions per charge" value={formatMaybeNumber(batteryInsights?.sessionsPerCycle)} />
            <InfoRow label="Longest run" value={batteryInsights?.longestRunSessions || '—'} />
            <InfoRow label="Recent drain" value={batteryInsights?.avgDrainRecent ? `${batteryInsights.avgDrainRecent.toFixed(1)}%` : '—'} />
          </div>
        </div>
      </div>
    </motion.section>
  )
}

const InsightCard = ({ icon, label, value, note }) => (
  <div className="metric-card glass-card">
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent-cyan)' }}>
      {icon}
      <span className="metric-label">{label}</span>
    </div>
    <div className="metric-value" style={{ fontSize: '24px' }}>{value}</div>
    <div className="metric-note">{note}</div>
  </div>
)

const InfoRow = ({ label, value }) => (
  <div className="info-row">
    <span className="info-row-label">{label}</span>
    <span className="info-row-value">{value}</span>
  </div>
)

const getPeakTimeLabel = (value) => {
  switch (value) {
    case 0: return 'Night'
    case 1: return 'Morning'
    case 2: return 'Afternoon'
    case 3: return 'Evening'
    default: return '—'
  }
}

const getDayLabel = (value) => {
  switch (value) {
    case 0: return 'Monday'
    case 1: return 'Tuesday'
    case 2: return 'Wednesday'
    case 3: return 'Thursday'
    case 4: return 'Friday'
    case 5: return 'Saturday'
    case 6: return 'Sunday'
    default: return '—'
  }
}

const displayTemp = (celsiusValue, isCelsius) => (
  isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8 + 32)
)

const formatDelta = (delta, suffix) => {
  if (!delta) return `No change ${suffix}`
  return `${delta > 0 ? '+' : ''}${delta} ${suffix}`
}

const formatMaybeNumber = (value) => (
  value && value > 0 ? value.toFixed(1) : '—'
)

const formatTrend = (value) => {
  if (value === undefined || value === null) return '—'
  if (value > 0.5) return `Up ${value.toFixed(1)}%`
  if (value < -0.5) return `Down ${Math.abs(value).toFixed(1)}%`
  return 'Stable'
}

export default AnalysisView
