import React from 'react'
import { motion } from 'framer-motion'
import { Activity, Battery, Calendar, Flame, TrendingUp, Wind } from 'lucide-react'
import useStore from '../store/useStore'

const AnalysisView = () => {
  const { telemetry } = useStore()
  const { status, batteryInsights, usageInsights, intake, historyStats } = telemetry
  const isCelsius = status?.isCelsius ?? true

  const favoriteTempC = historyStats?.favoriteTemps?.[0]?.temp ?? null
  const favoriteTemp = favoriteTempC
    ? `${isCelsius ? favoriteTempC : Math.round(favoriteTempC * 1.8 + 32)}°${isCelsius ? 'C' : 'F'}`
    : '—'

  const weeklySessionDelta = (usageInsights?.sessionsThisWeek || 0) - (usageInsights?.sessionsLastWeek || 0)
  const weeklyHitsDelta = (usageInsights?.hitsThisWeek || 0) - (usageInsights?.hitsLastWeek || 0)
  const drainTrend = batteryInsights?.drainTrend || 0

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="view-container"
      style={{ overflowX: 'hidden', paddingBottom: '160px' }}
    >
      <h2 className="view-title">Insights</h2>

      <SectionLabel>Session Trends</SectionLabel>
      <motion.div layout className="glass-card future-glass" style={{ padding: '24px', width: '100%' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '20px' }}>
          <MetricCard
            icon={<Flame size={16} color="var(--accent-orange)" />}
            label="Sessions This Week"
            value={usageInsights?.sessionsThisWeek || 0}
            sub={formatDelta(weeklySessionDelta, 'vs last week')}
          />
          <MetricCard
            icon={<Wind size={16} color="var(--accent-cyan)" />}
            label="Hits This Week"
            value={usageInsights?.hitsThisWeek || 0}
            sub={formatDelta(weeklyHitsDelta, 'vs last week')}
          />
          <MetricCard
            icon={<Activity size={16} color="var(--accent-green)" />}
            label="Avg Heat-up"
            value={historyStats?.avgHeatUpSec ? `${historyStats.avgHeatUpSec}s` : '—'}
            sub="Time to ready"
          />
          <MetricCard
            icon={<TrendingUp size={16} color="var(--accent-orange)" />}
            label="Favorite Temp"
            value={favoriteTemp}
            sub={historyStats?.favoriteTemps?.[0]?.count ? `${historyStats.favoriteTemps[0].count} sessions` : 'No pattern yet'}
          />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
          <StatRow label="Avg hits per minute" value={formatOneDecimal(usageInsights?.avgHitsPerMinute, '—')} />
          <StatRow label="Avg sessions per active day" value={formatOneDecimal(usageInsights?.avgSessionsPerDay, '—')} />
          <StatRow label="Sessions per day (7d)" value={formatOneDecimal(historyStats?.sessionsPerDay7d, '—')} />
          <StatRow label="Sessions per day (30d)" value={formatOneDecimal(historyStats?.sessionsPerDay30d, '—')} />
        </div>
      </motion.div>

      <SectionLabel>Patterns</SectionLabel>
      <motion.div layout className="glass-card" style={{ padding: '24px', width: '100%' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
          <MetricCard
            icon={<Calendar size={16} color="var(--accent-cyan)" />}
            label="Peak Time"
            value={getPeakTimeLabel(usageInsights?.peakTimeOfDay)}
            sub="Most common time of day"
          />
          <MetricCard
            icon={<Calendar size={16} color="var(--accent-orange)" />}
            label="Busiest Day"
            value={getDayLabel(usageInsights?.busiestDayOfWeek)}
            sub="Most active day of week"
          />
          <MetricCard
            icon={<TrendingUp size={16} color="var(--accent-green)" />}
            label="Current Streak"
            value={`${usageInsights?.currentStreak || 0} days`}
            sub={`Best: ${usageInsights?.longestStreak || 0} days`}
          />
          <MetricCard
            icon={<Activity size={16} color="var(--accent-cyan)" />}
            label="Active Days"
            value={usageInsights?.totalDaysActive || 0}
            sub={historyStats?.peakSessionsInDay ? `${historyStats.peakSessionsInDay} sessions in your busiest day` : 'Building history'}
          />
        </div>
      </motion.div>

      <SectionLabel>Dose & Battery</SectionLabel>
      <div style={{ width: '100%', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <motion.div layout className="glass-card" style={{ padding: '20px' }}>
          <div style={{ fontSize: '10px', opacity: 0.45, textTransform: 'uppercase', fontWeight: '800', letterSpacing: '0.12em', marginBottom: '12px' }}>
            Intake
          </div>
          <StatRow label="Total used" value={`${(intake?.totalGramsAllTime || 0).toFixed(2)}g`} />
          <StatRow label="This week" value={`${(intake?.totalGramsThisWeek || 0).toFixed(2)}g`} />
          <StatRow label="This month" value={`${(intake?.totalGramsThisMonth || 0).toFixed(2)}g`} />
          <StatRow label="Avg per session" value={`${(intake?.avgGramsPerSession || 0).toFixed(2)}g`} />
        </motion.div>

        <motion.div layout className="glass-card" style={{ padding: '20px' }}>
          <div style={{ fontSize: '10px', opacity: 0.45, textTransform: 'uppercase', fontWeight: '800', letterSpacing: '0.12em', marginBottom: '12px' }}>
            Battery
          </div>
          <StatRow label="Recent drain" value={formatPercent(batteryInsights?.avgDrainRecent)} />
          <StatRow label="Trend" value={formatTrend(drainTrend)} />
          <StatRow label="Sessions per charge" value={formatOneDecimal(batteryInsights?.sessionsPerCycle, '—')} />
          <StatRow label="Longest run" value={batteryInsights?.longestRunSessions || '—'} />
        </motion.div>
      </div>
    </motion.div>
  )
}

const SectionLabel = ({ children }) => (
  <div style={{ width: '100%', padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>
    {children}
  </div>
)

const MetricCard = ({ icon, label, value, sub }) => (
  <div className="glass-card" style={{ padding: '16px', background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.05)' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
      {icon}
      <div style={{ fontSize: '10px', opacity: 0.45, textTransform: 'uppercase', fontWeight: '800', letterSpacing: '0.1em' }}>
        {label}
      </div>
    </div>
    <div style={{ fontSize: '22px', fontWeight: '900', letterSpacing: '-0.03em', marginBottom: '6px' }}>{value}</div>
    <div style={{ fontSize: '10px', opacity: 0.45 }}>{sub}</div>
  </div>
)

const StatRow = ({ label, value }) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
    <span style={{ fontSize: '12px', opacity: 0.6 }}>{label}</span>
    <span style={{ fontSize: '12px', fontWeight: '700' }}>{value}</span>
  </div>
)

const getPeakTimeLabel = (tod) => {
  switch (tod) {
    case 0: return 'Night'
    case 1: return 'Morning'
    case 2: return 'Afternoon'
    case 3: return 'Evening'
    default: return '—'
  }
}

const getDayLabel = (day) => {
  switch (day) {
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

const formatDelta = (delta, suffix) => {
  if (!delta) return `No change ${suffix}`
  return `${delta > 0 ? '+' : ''}${delta} ${suffix}`
}

const formatOneDecimal = (value, fallback) => (
  value && value > 0 ? value.toFixed(1) : fallback
)

const formatPercent = (value) => (
  value && value > 0 ? `${value.toFixed(1)}%` : '—'
)

const formatTrend = (value) => {
  if (value > 0.5) return `+${value.toFixed(1)}%`
  if (value < -0.5) return `${value.toFixed(1)}%`
  return 'Stable'
}

export default AnalysisView
