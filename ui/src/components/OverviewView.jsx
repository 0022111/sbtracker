import React from 'react'
import { motion } from 'framer-motion'
import { Battery, Gauge, Play, Radar, Sparkles, TimerReset, Wind, Zap } from 'lucide-react'
import { useSessionIntelligence } from '../hooks/useSessionIntelligence'
import useStore from '../store/useStore'

const OverviewView = () => {
  const {
    telemetry,
    sendCommand,
    hasSeenOnboarding,
    setHasSeenOnboarding,
    setView,
  } = useStore()
  const intelligence = useSessionIntelligence('30d')

  const { status, stats, connectionState, prefs } = telemetry
  const ringRef = React.useRef(null)
  const [isDragging, setIsDragging] = React.useState(false)

  const isConnected = connectionState === 'Connected'
  const isHeating = (status?.heaterMode || 0) > 0
  const isCharging = status?.isCharging
  const isCelsius = status?.isCelsius ?? true
  const unit = isCelsius ? '°C' : '°F'
  const limits = telemetry.deviceLimits || { min: 40, max: 210 }
  const tempRatio = status ? clamp((status.targetTempC - limits.min) / (limits.max - limits.min), 0, 1) : 0
  const currentRatio = status ? clamp((status.currentTempC - limits.min) / (limits.max - limits.min), 0, 1) : 0
  const leadRecommendation = intelligence.recommendations[0]
  const primaryAction = !isConnected
    ? { label: 'Find device', onClick: () => sendCommand('startScan'), className: 'btn-secondary' }
    : isHeating
      ? { label: 'Stop heater', onClick: () => sendCommand('setHeater', false), className: 'btn-stop' }
      : { label: 'Start heater', onClick: () => sendCommand('setHeater', true), className: 'btn-ignite' }

  const angleFromEvent = (event) => {
    if (!ringRef.current) return null
    const rect = ringRef.current.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    const dx = event.clientX - centerX
    const dy = event.clientY - centerY
    const distance = Math.hypot(dx, dy)

    if (distance < rect.width * 0.24 || distance > rect.width * 0.52) return null
    return normalizeAngle((Math.atan2(dy, dx) * 180) / Math.PI + 90)
  }

  const setTempFromAngle = React.useCallback((angle) => {
    const nextTemp = Math.round(limits.min + clamp(angle / 360, 0, 1) * (limits.max - limits.min))
    if (nextTemp !== (status?.targetTempC ?? 190)) {
      sendCommand('setTemp', nextTemp)
    }
  }, [limits.max, limits.min, sendCommand, status?.targetTempC])

  const handlePointerDown = (event) => {
    if (!isConnected) return
    const angle = angleFromEvent(event)
    if (angle === null) return
    event.preventDefault()
    event.currentTarget.setPointerCapture?.(event.pointerId)
    setIsDragging(true)
    setTempFromAngle(angle)
  }

  const handlePointerMove = React.useCallback((event) => {
    if (!isDragging) return
    const angle = angleFromEvent(event)
    if (angle === null) return
    event.preventDefault()
    setTempFromAngle(angle)
  }, [isDragging, setTempFromAngle])

  React.useEffect(() => {
    if (!isDragging) return undefined
    const stop = () => setIsDragging(false)
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', stop)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', stop)
    }
  }, [handlePointerMove, isDragging])

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">{intelligence.headline.eyebrow}</p>
        <h1 className="page-title">{intelligence.headline.title}</h1>
        <p className="page-subtitle">{intelligence.headline.detail}</p>
      </header>

      {!hasSeenOnboarding && (
        <div className="glass-card info-banner">
          <div>
            <div className="eyebrow">From The Ashes</div>
            <div className="banner-title">This rebuild treats the log like raw truth.</div>
            <div className="banner-copy">
              Bluetooth, status logging, hit detection, and battery runway stay. The new layer is about reading sessions in a way that actually changes what you do next.
            </div>
          </div>
          <button className="adj-btn" onClick={() => setHasSeenOnboarding(true)}>×</button>
        </div>
      )}

      <div className="hero-layout">
        <div className="glass-card future-glass command-card">
          <div className="command-card-head">
            <div className={`status-badge badge-${intelligence.headline.tone}`}>
              <span className="status-dot" style={{ background: getToneColor(intelligence.headline.tone) }} />
              {isHeating ? 'Session live' : isConnected ? 'Device ready' : 'Awaiting bridge'}
            </div>
            <button className="btn-secondary compact-button" onClick={() => setView('analysis')}>
              <Radar size={14} />
              Open signals
            </button>
          </div>

          <div
            ref={ringRef}
            className="temp-ring-container"
            onPointerDown={handlePointerDown}
            style={{ cursor: isConnected ? 'pointer' : 'default', touchAction: 'none' }}
          >
            <Ring currentRatio={currentRatio} targetRatio={tempRatio} active={isHeating} isDragging={isDragging} />
            <div className="ring-copy">
              <div className="temp-text">
                {isConnected ? displayTemp(isDragging ? status?.targetTempC : status?.currentTempC, isCelsius) : '—'}
                <span className="temp-unit">{unit}</span>
              </div>
              <div className="ring-subtitle">
                {isDragging
                  ? `Target ${displayTemp(status?.targetTempC, isCelsius)}${unit}`
                  : `Set to ${displayTemp(status?.targetTempC, isCelsius)}${unit}`}
              </div>
            </div>
          </div>

          <div className="summary-grid">
            <SignalTile icon={<Battery size={16} />} label="Battery" value={`${status?.batteryLevel ?? 0}%`} note={isCharging ? 'Charging now' : 'Current level'} />
            <SignalTile icon={<Wind size={16} />} label="Hits live" value={isHeating ? stats?.hitCount || 0 : intelligence.today.hitCount} note={isHeating ? 'Detected this run' : 'Today'} />
            <SignalTile icon={<Gauge size={16} />} label="Heat-up ETA" value={formatHeat(stats?.estHeatUpMs)} note={isHeating ? 'Live estimate' : 'Based on history'} />
            <SignalTile icon={<Zap size={16} />} label="Runway" value={intelligence.battery.sessionsRemaining ?? '—'} note={intelligence.battery.confidenceLabel} />
          </div>
        </div>

        <div className="panel-stack">
          <div className="glass-card panel-card">
            <div className="section-heading">Control Deck</div>
            <div className="adjustment-row">
              <span className="adj-label">Target</span>
              <div className="adj-controls">
                <button className="adj-btn" onClick={() => sendCommand('setTemp', Math.max(limits.min, (status?.targetTempC || 190) - 1))}>−</button>
                <span className="adj-value">{displayTemp(status?.targetTempC || 190, isCelsius)}°</span>
                <button className="adj-btn" onClick={() => sendCommand('setTemp', Math.min(limits.max, (status?.targetTempC || 190) + 1))}>+</button>
              </div>
            </div>
            <div className="adjustment-row">
              <span className="adj-label">Boost</span>
              <div className="adj-controls">
                <button className="adj-btn" onClick={() => sendCommand('setBoostDelta', Math.max(0, (status?.boostOffsetC || 15) - 1))}>−</button>
                <span className="adj-value">+{displayDelta(status?.boostOffsetC || 15, isCelsius)}°</span>
                <button className="adj-btn" onClick={() => sendCommand('setBoostDelta', Math.min(30, (status?.boostOffsetC || 15) + 1))}>+</button>
              </div>
            </div>
            <div className="button-row" style={{ marginTop: '16px' }}>
              <button className={primaryAction.className} style={{ flex: 1 }} onClick={primaryAction.onClick}>
                {primaryAction.label}
              </button>
            </div>
            {isHeating && (
              <div className="button-row" style={{ marginTop: '10px' }}>
                <button className="btn-secondary" style={{ flex: 1 }} onClick={() => sendCommand('setBoostPulse', status?.heaterMode !== 2)}>
                  Toggle boost
                </button>
                <button className="btn-secondary" style={{ flex: 1 }} onClick={() => sendCommand('setSuperBoostPulse', status?.heaterMode !== 3)}>
                  Toggle superboost
                </button>
              </div>
            )}
          </div>

          <div className="glass-card panel-card">
            <div className="section-heading">Operational Read</div>
            <InfoRow label="Today" value={`${intelligence.today.sessionCount} sessions`} />
            <InfoRow label="Average session" value={`${intelligence.summary.avgDurationMin.toFixed(1)} min`} />
            <InfoRow label="Productive share" value={`${intelligence.summary.productiveShare}%`} />
            <InfoRow label="Default mode" value={prefs?.defaultIsCapsule ? 'Capsule' : 'Loose fill'} />
          </div>
        </div>
      </div>

      <div className="split-layout">
        <FeaturePanel
          title="What the log says lately"
          icon={<Sparkles size={16} />}
          body={leadRecommendation?.detail || 'The session model is still learning from your recent runs.'}
          footer={leadRecommendation?.title || 'Need more history'}
          tone={leadRecommendation?.tone || 'neutral'}
        />
        <FeaturePanel
          title="Battery runway"
          icon={<TimerReset size={16} />}
          body={intelligence.battery.sessionsRemaining !== null
            ? `Expected remaining sessions: ${intelligence.battery.sessionsRemaining}. Range ${intelligence.battery.sessionsLow ?? '—'} to ${intelligence.battery.sessionsHigh ?? '—'}.`
            : 'Runway will appear after a few complete sessions establish your drain pattern.'}
          footer={intelligence.battery.confidenceLabel}
          tone={intelligence.battery.reliable ? 'accent' : 'neutral'}
        />
      </div>

      <div className="glass-card panel-card">
        <div className="section-heading">Recent session reads</div>
        <div className="story-list">
          {intelligence.recent.slice(0, 4).map((session) => (
            <div key={session.id} className="story-card">
              <div className="story-card-head">
                <div>
                  <div className="story-title">{session.storyLabel}</div>
                  <div className="story-meta">{formatDate(session.startTimeMs)}</div>
                </div>
                <span className="pill">{session.sessionScore}/100</span>
              </div>
              <div className="story-copy">{session.storyDetail}</div>
              <div className="story-traits">
                <span className="pill">{session.hitCount} hits</span>
                <span className="pill">{session.durationMin.toFixed(1)} min</span>
                <span className="pill">{session.battery}% drain</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </motion.section>
  )
}

const SignalTile = ({ icon, label, value, note }) => (
  <div className="metric-card glass-panel">
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent-cyan)' }}>
      {icon}
      <span className="metric-label">{label}</span>
    </div>
    <div className="metric-value" style={{ fontSize: '24px' }}>{value}</div>
    <div className="metric-note">{note}</div>
  </div>
)

const FeaturePanel = ({ title, icon, body, footer, tone }) => (
  <div className={`glass-card panel-card feature-panel tone-${tone}`}>
    <div className="feature-panel-head">
      <div className="feature-icon">{icon}</div>
      <div className="section-heading">{title}</div>
    </div>
    <div className="feature-body">{body}</div>
    <div className="feature-footer">{footer}</div>
  </div>
)

const InfoRow = ({ label, value }) => (
  <div className="info-row">
    <span className="info-row-label">{label}</span>
    <span className="info-row-value">{value}</span>
  </div>
)

const Ring = ({ currentRatio, targetRatio, active, isDragging }) => {
  const size = 286
  const radius = 118
  const circumference = 2 * Math.PI * radius
  const currentOffset = circumference * (1 - currentRatio)
  const targetOffset = circumference * (1 - targetRatio)

  return (
    <svg viewBox="0 0 286 286" className={`ring-svg ${active ? 'pulse-slow' : ''}`}>
      <defs>
        <linearGradient id="ring-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#71d7c0" />
          <stop offset="100%" stopColor="#dfa15a" />
        </linearGradient>
      </defs>
      <circle cx="143" cy="143" r={radius} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="14" />
      <circle
        cx="143"
        cy="143"
        r={radius}
        fill="none"
        stroke="rgba(255,255,255,0.18)"
        strokeWidth="10"
        strokeDasharray={circumference}
        strokeDashoffset={targetOffset}
        strokeLinecap="round"
        transform="rotate(-90 143 143)"
      />
      <circle
        cx="143"
        cy="143"
        r={radius}
        fill="none"
        stroke="url(#ring-gradient)"
        strokeWidth={isDragging ? 16 : 14}
        strokeDasharray={circumference}
        strokeDashoffset={currentOffset}
        strokeLinecap="round"
        transform="rotate(-90 143 143)"
      />
    </svg>
  )
}

const formatDate = (value) => (
  new Date(value).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
)

const normalizeAngle = (angle) => ((angle % 360) + 360) % 360

const displayTemp = (celsiusValue, isCelsius) => {
  if (celsiusValue === undefined || celsiusValue === null) return '—'
  return isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8 + 32)
}

const displayDelta = (celsiusValue, isCelsius) => (
  isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8)
)

const formatHeat = (value) => {
  if (!value) return '—'
  return `${Math.round(value / 1000)}s`
}

const getToneColor = (tone) => {
  switch (tone) {
    case 'good': return 'var(--accent-green)'
    case 'warn': return 'var(--accent-orange)'
    case 'accent': return 'var(--accent-cyan)'
    default: return 'var(--accent-neutral)'
  }
}

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

export default OverviewView
