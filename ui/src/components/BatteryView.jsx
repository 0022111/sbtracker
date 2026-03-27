import React from 'react'
import { motion } from 'framer-motion'
import { Battery, Clock3, Plug, ShieldCheck, Zap } from 'lucide-react'
import useStore from '../store/useStore'

const BatteryView = () => {
  const { telemetry, sendCommand } = useStore()
  const { status, stats, batteryInsights, extended } = telemetry

  const isCharging = status?.isCharging
  const batteryLevel = status?.batteryLevel || 0

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">Battery</p>
        <h1 className="page-title">Battery health and charge behavior</h1>
        <p className="page-subtitle">
          This screen should answer two things quickly: how much battery you have right now, and whether the device is aging normally.
        </p>
      </header>

      <div className="hero-layout">
        <div className="glass-card future-glass" style={{ padding: '22px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', alignItems: 'flex-start' }}>
            <div>
              <div className="section-heading" style={{ marginBottom: '10px' }}>
                {isCharging ? 'Charging now' : 'Current battery'}
              </div>
              <div className="metric-value" style={{ fontSize: '64px' }}>
                {batteryLevel}
                <span style={{ fontSize: '26px', color: 'var(--text-soft)' }}>%</span>
              </div>
              <div className="metric-note">
                {isCharging
                  ? (stats?.chargeEtaMinutes ? `${stats.chargeEtaMinutes} minutes to full` : 'Estimating time to full')
                  : (stats?.sessionsToCritical ? `About ${stats.sessionsToCritical} sessions before 15%` : 'Need more session history for range estimates')}
              </div>
            </div>

            <div
              className="glass-panel"
              style={{
                width: '86px',
                height: '150px',
                padding: '8px',
                position: 'relative',
                display: 'flex',
                alignItems: 'flex-end',
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  top: '-8px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: '28px',
                  height: '8px',
                  borderRadius: '4px 4px 0 0',
                  background: 'rgba(255,255,255,0.16)',
                }}
              />
              <div
                style={{
                  width: '100%',
                  height: `${Math.max(8, batteryLevel)}%`,
                  borderRadius: '14px',
                  background: isCharging
                    ? 'linear-gradient(180deg, rgba(113, 215, 192, 1), rgba(113, 215, 192, 0.44))'
                    : 'linear-gradient(180deg, rgba(125, 217, 146, 1), rgba(125, 217, 146, 0.32))',
                }}
              />
            </div>
          </div>

          <div className="summary-grid" style={{ marginTop: '18px' }}>
            <MetricTile icon={<Plug size={16} />} label="Sessions left" value={stats?.sessionsRemaining || '—'} note={stats?.drainEstimateReliable ? 'Estimated' : 'Still learning'} />
            <MetricTile icon={<Clock3 size={16} />} label="To 80%" value={stats?.chargeEta80Minutes ? `${stats.chargeEta80Minutes}m` : '—'} note="If charging" />
            <MetricTile icon={<Zap size={16} />} label="Charge rate" value={stats?.chargeRatePctPerMin ? `${stats.chargeRatePctPerMin.toFixed(1)}%/m` : '—'} note="Current pace" />
            <MetricTile icon={<Battery size={16} />} label="Median drain" value={batteryInsights?.medianDrain ? `${batteryInsights.medianDrain.toFixed(1)}%` : '—'} note="Typical session" />
          </div>
        </div>

        <div className="panel-stack">
          {!isCharging && stats?.drainEstimateReliable && stats.sessionsRemainingLow > 0 && stats.sessionsRemainingHigh > 0 && (
            <div className="glass-card" style={{ padding: '18px' }}>
              <div className="section-heading" style={{ marginBottom: '10px' }}>Estimate range</div>
              <div style={{ fontSize: '16px', fontWeight: 800 }}>
                About {stats.sessionsRemainingLow} to {stats.sessionsRemainingHigh} sessions before 15%
              </div>
              <div style={{ marginTop: '8px', fontSize: '13px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
                The range widens when your recent sessions vary a lot in duration, hits, or starting battery.
              </div>
            </div>
          )}

          <div className="glass-card" style={{ padding: '18px' }}>
            <div className="section-heading" style={{ marginBottom: '12px' }}>Care signals</div>
            <InfoRow label="Recent drain" value={batteryInsights?.avgDrainRecent ? `${batteryInsights.avgDrainRecent.toFixed(1)}%` : '—'} />
            <InfoRow label="All-time average" value={batteryInsights?.avgDrainAll ? `${batteryInsights.avgDrainAll.toFixed(1)}%` : '—'} />
            <InfoRow label="Trend" value={formatTrend(batteryInsights?.drainTrend)} />
            <InfoRow label="Consistency" value={batteryInsights?.drainStdDev ? `±${batteryInsights.drainStdDev.toFixed(1)}%` : '—'} />
            <InfoRow label="Sessions per charge" value={batteryInsights?.sessionsPerCycle ? batteryInsights.sessionsPerCycle.toFixed(1) : '—'} />
          </div>
        </div>
      </div>

      <div className="split-layout">
        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Charge history</div>
          <InfoRow label="Average charge time" value={formatChargeMinutes(batteryInsights?.avgChargeTime)} />
          <InfoRow label="Average gained" value={batteryInsights?.avgBatteryGainedPct ? `${batteryInsights.avgBatteryGainedPct.toFixed(0)}%` : '—'} />
          <InfoRow label="Depth of discharge" value={batteryInsights?.avgDepthOfDischarge ? `${batteryInsights.avgDepthOfDischarge.toFixed(0)}%` : '—'} />
          <InfoRow label="Days per charge cycle" value={batteryInsights?.avgDaysPerChargeCycle ? `${batteryInsights.avgDaysPerChargeCycle.toFixed(1)} d` : '—'} />
          <InfoRow label="Longest run" value={batteryInsights?.longestRunSessions || '—'} />
        </div>

        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Protection settings</div>
          <SettingRow
            label="Charge optimization"
            detail="Reduce battery wear while charging"
            active={status?.chargeCurrentOptimization}
            onClick={() => sendCommand('setChargeCurrentOptimization', !status?.chargeCurrentOptimization)}
          />
          <SettingRow
            label="80% charge limit"
            detail="Stop charging early to preserve health"
            active={status?.chargeVoltageLimit}
            onClick={() => sendCommand('setChargeVoltageLimit', !status?.chargeVoltageLimit)}
          />
        </div>
      </div>

      {(extended?.heaterRuntimeMinutes || 0) >= 600 && (
        <div className="glass-card" style={{ padding: '18px', borderColor: 'rgba(231, 164, 91, 0.22)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
            <ShieldCheck size={18} color="var(--accent-orange)" />
            <div className="section-heading" style={{ color: 'var(--accent-orange)' }}>Maintenance</div>
          </div>
          <div style={{ fontSize: '16px', fontWeight: 800, marginBottom: '6px' }}>Cleaning is worth doing soon</div>
          <div style={{ fontSize: '13px', lineHeight: 1.5, color: 'var(--text-muted)' }}>
            The heater has logged roughly {Math.round((extended?.heaterRuntimeMinutes || 0) / 60)} hours of use. If performance feels slower or less consistent, a clean is a good next step.
          </div>
        </div>
      )}
    </motion.section>
  )
}

const MetricTile = ({ icon, label, value, note }) => (
  <div className="metric-card glass-panel">
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

const SettingRow = ({ label, detail, active, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    style={{
      width: '100%',
      textAlign: 'left',
      background: 'transparent',
      border: 'none',
      padding: '12px 0',
      borderBottom: '1px solid rgba(255,255,255,0.05)',
      cursor: 'pointer',
      color: 'inherit',
    }}
  >
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', alignItems: 'center' }}>
      <div>
        <div style={{ fontSize: '15px', fontWeight: 700 }}>{label}</div>
        <div style={{ marginTop: '4px', fontSize: '12px', color: 'var(--text-muted)' }}>{detail}</div>
      </div>
      <div className={`tg-switch ${active ? 'active' : ''}`}>
        <div className="tg-handle" style={{ transform: `translateX(${active ? 20 : 0}px)` }} />
      </div>
    </div>
  </button>
)

const formatChargeMinutes = (minutes) => {
  if (!minutes || minutes <= 0) return '—'
  if (minutes >= 60) {
    return `${Math.floor(minutes / 60)}h ${minutes % 60}m`
  }
  return `${minutes}m`
}

const formatTrend = (value) => {
  if (value === undefined || value === null) return '—'
  if (value > 0.5) return `Up ${value.toFixed(1)}%`
  if (value < -0.5) return `Down ${Math.abs(value).toFixed(1)}%`
  return 'Stable'
}

export default BatteryView
