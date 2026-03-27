import React from 'react'
import { motion } from 'framer-motion'
import { Clock3, Flame, Play, Route, Sparkles, ThermometerSun } from 'lucide-react'
import useStore from '../store/useStore'

const SessionView = () => {
  const { programs, telemetry, sendCommand, setView } = useStore()
  const isConnected = telemetry.connectionState === 'Connected'
  const isHeating = (telemetry.status?.heaterMode || 0) > 0
  const isCelsius = telemetry.status?.isCelsius ?? true
  const unit = isCelsius ? '°C' : '°F'

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">Programs</p>
        <h1 className="page-title">Reusable session starts</h1>
        <p className="page-subtitle">
          Save the sessions you actually repeat. Programs start the heater, set the base temperature, then apply boost steps over time.
        </p>
      </header>

      <div className="split-layout">
        <div className="glass-card future-glass" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>How to use this</div>
          <div className="info-row">
            <span className="info-row-label">Start here for</span>
            <span className="info-row-value">Repeatable routines</span>
          </div>
          <div className="info-row">
            <span className="info-row-label">Use Home for</span>
            <span className="info-row-value">Manual temp control</span>
          </div>
          <div className="info-row">
            <span className="info-row-label">Current device state</span>
            <span className="info-row-value">{isHeating ? 'Session already running' : isConnected ? 'Ready to start' : 'Not connected'}</span>
          </div>
          <div className="button-row" style={{ marginTop: '14px' }}>
            <button className="btn-secondary" style={{ flex: 1 }} onClick={() => setView('overview')}>
              Go to Home
            </button>
          </div>
        </div>

        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Current library</div>
          <div className="metric-value">{programs.length}</div>
          <div className="metric-note" style={{ marginBottom: '12px' }}>
            {programs.length === 1 ? 'Saved program' : 'Saved programs'}
          </div>
          <div style={{ fontSize: '13px', lineHeight: 1.5, color: 'var(--text-muted)' }}>
            A good program is one you reach for without thinking. It should save taps, not add complexity.
          </div>
        </div>
      </div>

      {!programs.length ? (
        <div className="glass-card empty-state">
          <Sparkles size={28} style={{ opacity: 0.5 }} />
          <div style={{ fontSize: '18px', fontWeight: 800 }}>No programs loaded</div>
          <div style={{ maxWidth: '260px', fontSize: '13px', lineHeight: 1.55, color: 'var(--text-muted)' }}>
            Connect to your device to load saved programs, then start one here when you want a repeatable session.
          </div>
        </div>
      ) : (
        <div className="program-list">
          {programs.map((program) => {
            const details = describeProgram(program.boostStepsJson, program.targetTempC, isCelsius)
            const actionDisabled = !isConnected || isHeating

            return (
              <div key={program.id} className="glass-card program-card">
                <div className="program-card-head">
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <h2 className="program-name">{program.name}</h2>
                      {program.isDefault && <span className="pill">Default</span>}
                    </div>
                    <div className="program-meta">
                      <span className="pill"><ThermometerSun size={12} /> {displayTemp(program.targetTempC, isCelsius)}{unit}</span>
                      <span className="pill"><Route size={12} /> {details.stageCount} stages</span>
                      <span className="pill"><Clock3 size={12} /> Last change {details.lastOffsetLabel}</span>
                    </div>
                  </div>

                  <button
                    className="btn-ignite mini"
                    onClick={() => sendCommand('setProgram', program.id)}
                    disabled={actionDisabled}
                    style={{
                      opacity: actionDisabled ? 0.45 : 1,
                      cursor: actionDisabled ? 'not-allowed' : 'pointer',
                    }}
                  >
                    <Play size={18} fill="currentColor" />
                  </button>
                </div>

                <div className="split-layout" style={{ marginTop: '14px' }}>
                  <ProgramStat label="Start temp" value={`${displayTemp(program.targetTempC, isCelsius)}${unit}`} icon={<Flame size={15} />} />
                  <ProgramStat label="Boost path" value={details.summary} icon={<Route size={15} />} />
                </div>

                {details.steps.length > 0 && (
                  <div style={{ marginTop: '14px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {details.steps.map((step, index) => (
                      <div key={`${program.id}-${index}`} className="info-row" style={{ paddingTop: 0 }}>
                        <span className="info-row-label">Step {index + 1}</span>
                        <span className="info-row-value">
                          +{displayTemp(step.boostC, isCelsius)}° at {formatOffset(step.offsetSec)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </motion.section>
  )
}

const ProgramStat = ({ icon, label, value }) => (
  <div className="glass-panel" style={{ padding: '14px 16px' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px', color: 'var(--accent-cyan)' }}>
      {icon}
      <span className="metric-label">{label}</span>
    </div>
    <div style={{ fontSize: '15px', fontWeight: 700 }}>{value}</div>
  </div>
)

const describeProgram = (boostStepsJson, targetTempC, isCelsius) => {
  let steps = []

  try {
    const parsed = JSON.parse(boostStepsJson || '[]')
    if (Array.isArray(parsed)) {
      steps = parsed
        .map((step) => ({
          offsetSec: Number(step.offsetSec) || 0,
          boostC: Number(step.boostC) || 0,
        }))
        .sort((a, b) => a.offsetSec - b.offsetSec)
    }
  } catch (error) {
    steps = []
  }

  const lastOffsetSec = steps.length ? steps[steps.length - 1].offsetSec : 0
  const maxBoost = steps.length ? Math.max(...steps.map((step) => step.boostC)) : 0
  const topTemp = targetTempC + maxBoost
  const displayTopTemp = displayTemp(topTemp, isCelsius)

  return {
    steps,
    stageCount: Math.max(1, steps.length + 1),
    lastOffsetLabel: steps.length ? formatOffset(lastOffsetSec) : 'immediate',
    summary: steps.length ? `Peaks near ${displayTopTemp}${isCelsius ? '°C' : '°F'}` : 'Steady start only',
  }
}

const displayTemp = (celsiusValue, isCelsius) => (
  isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8 + 32)
)

const formatOffset = (offsetSec) => {
  const minutes = Math.floor(offsetSec / 60)
  const seconds = offsetSec % 60
  return `${minutes}:${`${seconds}`.padStart(2, '0')}`
}

export default SessionView
