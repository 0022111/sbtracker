import React from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Battery, Flame, Gauge, Plug, Settings, Sparkles, Wind, Zap } from 'lucide-react'
import useStore from '../store/useStore'

const OverviewView = () => {
  const {
    telemetry,
    sendCommand,
    currentSessionSeries,
    sessionHits,
    heatUpTimeSeconds,
    sessionHistory,
    hasSeenOnboarding,
    setHasSeenOnboarding,
    setView,
  } = useStore()

  const { status, stats, connectionState, extended, prefs } = telemetry
  const ringRef = React.useRef(null)
  const [isDragging, setIsDragging] = React.useState(false)
  const [hitPulse, setHitPulse] = React.useState(false)
  const prevHitCount = React.useRef(stats?.hitCount || 0)

  const isConnected = connectionState === 'Connected'
  const isHeating = (status?.heaterMode || 0) > 0
  const isCharging = status?.isCharging
  const isHitActive = stats?.isHitActive || false
  const isCelsius = status?.isCelsius ?? true
  const unit = isCelsius ? '°C' : '°F'
  const limits = telemetry.deviceLimits || { min: 40, max: 210 }
  const tempRatio = status ? clamp((status.targetTempC - limits.min) / (limits.max - limits.min), 0, 1) : 0
  const currentRatio = status ? clamp((status.currentTempC - limits.min) / (limits.max - limits.min), 0, 1) : 0

  React.useEffect(() => {
    if ((stats?.hitCount || 0) > prevHitCount.current) {
      setHitPulse(true)
      const timer = window.setTimeout(() => setHitPulse(false), 900)
      prevHitCount.current = stats?.hitCount || 0
      return () => window.clearTimeout(timer)
    }
    prevHitCount.current = stats?.hitCount || 0
  }, [stats?.hitCount])

  const displayTemp = React.useCallback((celsiusValue) => {
    if (celsiusValue === undefined || celsiusValue === null) return '—'
    return isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8 + 32)
  }, [isCelsius])

  const displayDelta = React.useCallback((celsiusValue) => {
    if (celsiusValue === undefined || celsiusValue === null) return '—'
    return isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8)
  }, [isCelsius])

  const normalizeAngle = (angle) => ((angle % 360) + 360) % 360

  const angleFromEvent = (event) => {
    if (!ringRef.current) return null
    const rect = ringRef.current.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    const dx = event.clientX - centerX
    const dy = event.clientY - centerY
    const distance = Math.hypot(dx, dy)

    if (distance < rect.width * 0.24 || distance > rect.width * 0.52) {
      return null
    }

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

  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)
  const todaySessions = sessionHistory.filter((session) => session.startTimeMs >= todayStart.getTime())
  const todayHits = todaySessions.reduce((sum, session) => sum + (session.hitCount || 0), 0)
  const todayMinutes = todaySessions.reduce((sum, session) => sum + ((session.durationMs || 0) / 60000), 0)
  const lastSession = sessionHistory[0] || null
  const batteryDelta = lastSession ? Math.max(0, (lastSession.startBattery || 0) - (lastSession.endBattery || 0)) : null

  const primaryAction = !isConnected
    ? { label: 'Find device', onClick: () => sendCommand('startScan'), className: 'btn-secondary' }
    : isHeating
      ? { label: 'Stop heater', onClick: () => sendCommand('setHeater', false), className: 'btn-stop' }
      : { label: 'Start heater', onClick: () => sendCommand('setHeater', true), className: 'btn-ignite' }

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">Home</p>
        <h1 className="page-title">{getHeadline(connectionState, isHeating, isCharging, status?.setpointReached)}</h1>
        <p className="page-subtitle">
          {getSubheadline({ isConnected, isHeating, isCharging, stats, status })}
        </p>
      </header>

      {!hasSeenOnboarding && (
        <div className="glass-card" style={{ padding: '18px 20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', alignItems: 'flex-start' }}>
            <div>
              <div className="eyebrow" style={{ marginBottom: '8px' }}>Start Here</div>
              <div style={{ fontSize: '16px', fontWeight: 800, marginBottom: '6px' }}>SBTracker is built around one flow.</div>
              <div style={{ fontSize: '13px', lineHeight: 1.5, color: 'var(--text-muted)' }}>
                Connect, set your target, run your session, then use history and insights to review what actually changed.
              </div>
            </div>
            <button className="adj-btn" onClick={() => setHasSeenOnboarding(true)}>×</button>
          </div>
        </div>
      )}

      <div className="hero-layout">
        <div className="glass-card future-glass" style={{ padding: '22px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
            <div className="status-badge">
              <span className="status-dot" style={{ background: isHeating ? 'var(--accent-orange)' : isConnected ? 'var(--accent-green)' : 'var(--accent-red)' }} />
              {getStateLabel(connectionState, isHeating, isCharging, status?.setpointReached)}
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{status?.deviceType || 'No device'}</div>
          </div>

          <div
            ref={ringRef}
            className="temp-ring-container"
            onPointerDown={handlePointerDown}
            style={{ cursor: isConnected ? 'pointer' : 'default', touchAction: 'none' }}
          >
            <Ring
              currentRatio={currentRatio}
              targetRatio={tempRatio}
              active={isHeating}
              hitPulse={hitPulse || isHitActive}
              isDragging={isDragging}
            />
            <div style={{ position: 'relative', zIndex: 1, textAlign: 'center', pointerEvents: 'none' }}>
              <div className="temp-text">
                {isConnected ? displayTemp(isDragging ? status?.targetTempC : status?.currentTempC) : '—'}
                <span className="temp-unit">{unit}</span>
              </div>
              <div style={{ marginTop: '10px', fontSize: '13px', color: 'var(--text-muted)' }}>
                {isDragging
                  ? `Target ${displayTemp(status?.targetTempC)}${unit}`
                  : `Set to ${displayTemp(status?.targetTempC)}${unit}`}
              </div>
            </div>
          </div>

          <div className="summary-grid" style={{ marginTop: '18px' }}>
            <SummaryTile label="Battery" value={`${status?.batteryLevel ?? 0}%`} note={isCharging ? 'Charging now' : 'Current level'} icon={<Battery size={16} />} />
            <SummaryTile label="Session" value={isHeating ? formatClock(stats?.durationSeconds || 0) : 'Ready'} note={isHeating ? `${stats?.hitCount || 0} hits` : 'Heater idle'} icon={<Flame size={16} />} />
            <SummaryTile label="Heat-up" value={isHeating ? (heatUpTimeSeconds ? `${heatUpTimeSeconds}s` : 'Warming') : formatHeatEstimate(stats?.estHeatUpMs)} note={isHeating ? 'First ready time' : 'Estimated to ready'} icon={<Gauge size={16} />} />
            <SummaryTile label="Range" value={stats?.sessionsRemaining ? `${stats.sessionsRemaining}` : '—'} note={stats?.drainEstimateReliable ? 'Sessions left' : 'Estimate building'} icon={<Zap size={16} />} />
          </div>
        </div>

        <div className="panel-stack">
          <div className="glass-card" style={{ padding: '18px' }}>
            <div className="section-heading" style={{ marginBottom: '12px' }}>Controls</div>
            <div className="adjustment-row">
              <span className="adj-label">Target</span>
              <div className="adj-controls">
                <button className="adj-btn" onClick={() => sendCommand('setTemp', Math.max(limits.min, (status?.targetTempC || 190) - 1))}>−</button>
                <span className="adj-value">{displayTemp(status?.targetTempC || 190)}°</span>
                <button className="adj-btn" onClick={() => sendCommand('setTemp', Math.min(limits.max, (status?.targetTempC || 190) + 1))}>+</button>
              </div>
            </div>
            <div className="adjustment-row">
              <span className="adj-label">Boost</span>
              <div className="adj-controls">
                <button className="adj-btn" onClick={() => sendCommand('setBoostDelta', Math.max(0, (status?.boostOffsetC || 15) - 1))}>−</button>
                <span className="adj-value">+{displayDelta(status?.boostOffsetC || 15)}°</span>
                <button className="adj-btn" onClick={() => sendCommand('setBoostDelta', Math.min(30, (status?.boostOffsetC || 15) + 1))}>+</button>
              </div>
            </div>
            <div className="adjustment-row">
              <span className="adj-label">Superboost</span>
              <div className="adj-controls">
                <button className="adj-btn" onClick={() => sendCommand('setSuperBoostDelta', Math.max(0, (status?.superBoostOffsetC || 30) - 2))}>−</button>
                <span className="adj-value">+{displayDelta(status?.superBoostOffsetC || 30)}°</span>
                <button className="adj-btn" onClick={() => sendCommand('setSuperBoostDelta', Math.min(30, (status?.superBoostOffsetC || 30) + 2))}>+</button>
              </div>
            </div>
            <div className="button-row" style={{ marginTop: '16px' }}>
              <button className={primaryAction.className} style={{ flex: 1 }} onClick={primaryAction.onClick}>
                {primaryAction.label}
              </button>
            </div>
            {isHeating && (
              <div className="button-row" style={{ marginTop: '10px' }}>
                <button
                  className={`btn-secondary ${status?.heaterMode === 2 ? 'active' : ''}`}
                  style={{ flex: 1 }}
                  onClick={() => sendCommand('setBoostPulse', status?.heaterMode !== 2)}
                >
                  Toggle boost
                </button>
                <button
                  className={`btn-secondary ${status?.heaterMode === 3 ? 'active' : ''}`}
                  style={{ flex: 1 }}
                  onClick={() => sendCommand('setSuperBoostPulse', status?.heaterMode !== 3)}
                >
                  Toggle superboost
                </button>
              </div>
            )}
          </div>

          <div className="glass-card" style={{ padding: '18px' }}>
            <div className="section-heading" style={{ marginBottom: '12px' }}>Right Now</div>
            <div className="info-row">
              <span className="info-row-label">Battery mode</span>
              <span className="info-row-value">{isCharging ? 'Charging' : 'Unplugged'}</span>
            </div>
            <div className="info-row">
              <span className="info-row-label">Dose default</span>
              <span className="info-row-value">{prefs?.defaultIsCapsule ? 'Capsule' : 'Loose fill'}</span>
            </div>
            <div className="info-row">
              <span className="info-row-label">Charge protection</span>
              <span className="info-row-value">
                {status?.chargeVoltageLimit ? '80% limit on' : 'Off'}
                {status?.chargeCurrentOptimization ? ' · optimize on' : ''}
              </span>
            </div>
            <div className="info-row">
              <span className="info-row-label">Maintenance</span>
              <span className="info-row-value">
                {(extended?.heaterRuntimeMinutes || 0) >= 600 ? 'Clean soon' : 'No cleaning flag'}
              </span>
            </div>
            <div className="button-row" style={{ marginTop: '14px' }}>
              <button className="btn-secondary" style={{ flex: 1 }} onClick={() => setView('session')}>
                Programs
              </button>
              <button className="btn-secondary" style={{ flex: 1 }} onClick={() => setView('settings')}>
                Settings
              </button>
            </div>
          </div>
        </div>
      </div>

      {isHeating && currentSessionSeries.length > 1 && (
        <div className="glass-card" style={{ padding: '18px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
            <div className="section-heading">Live Session</div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{stats?.hitCount || 0} hits</div>
          </div>
          <TempGraph data={currentSessionSeries} hits={sessionHits} />
        </div>
      )}

      {(isCharging || (extended?.heaterRuntimeMinutes || 0) >= 600) && (
        <div className="split-layout">
          {isCharging && (
            <div className="glass-card" style={{ padding: '18px' }}>
              <div className="section-heading" style={{ marginBottom: '12px' }}>Charging</div>
              <div className="metric-value">{status?.batteryLevel ?? 0}%</div>
              <div className="metric-note" style={{ marginBottom: '10px' }}>
                {stats?.chargeEtaMinutes ? `${stats.chargeEtaMinutes} min to full` : 'Estimating time to full'}
              </div>
              <div className="info-row">
                <span className="info-row-label">To 80%</span>
                <span className="info-row-value">{stats?.chargeEta80Minutes ? `${stats.chargeEta80Minutes} min` : '—'}</span>
              </div>
              <div className="info-row">
                <span className="info-row-label">Charge rate</span>
                <span className="info-row-value">{stats?.chargeRatePctPerMin ? `${stats.chargeRatePctPerMin.toFixed(1)}%/min` : '—'}</span>
              </div>
            </div>
          )}

          {(extended?.heaterRuntimeMinutes || 0) >= 600 && (
            <div className="glass-card" style={{ padding: '18px' }}>
              <div className="section-heading" style={{ marginBottom: '12px' }}>Device Care</div>
              <div style={{ fontSize: '16px', fontWeight: 800, marginBottom: '8px' }}>Cleaning recommended</div>
              <div style={{ fontSize: '13px', lineHeight: 1.5, color: 'var(--text-muted)' }}>
                The heater has logged about {Math.round((extended?.heaterRuntimeMinutes || 0) / 60)} hours since the last clean.
              </div>
            </div>
          )}
        </div>
      )}

      <div className="split-layout">
        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Today</div>
          <div className="summary-grid">
            <SummaryTile label="Sessions" value={todaySessions.length} note="Detected today" icon={<Sparkles size={16} />} />
            <SummaryTile label="Hits" value={todayHits} note="Across all sessions" icon={<Wind size={16} />} />
            <SummaryTile label="Minutes" value={Math.round(todayMinutes)} note="Heater on time" icon={<Flame size={16} />} />
            <SummaryTile label="Battery" value={batteryDelta !== null ? `${batteryDelta}%` : '—'} note="Last session used" icon={<Battery size={16} />} />
          </div>
        </div>

        <div className="glass-card" style={{ padding: '18px' }}>
          <div className="section-heading" style={{ marginBottom: '12px' }}>Last Session</div>
          {lastSession ? (
            <>
              <div style={{ fontSize: '18px', fontWeight: 800, marginBottom: '6px' }}>{formatSessionDate(lastSession.startTimeMs)}</div>
              <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '14px' }}>
                {formatMinutes(lastSession.durationMs)} • {lastSession.hitCount || 0} hits • {displayTemp(lastSession.peakTempC)}{unit} peak
              </div>
              {lastSession.notes && (
                <div style={{ fontSize: '13px', lineHeight: 1.5, color: 'var(--text-muted)', marginBottom: '14px' }}>
                  {lastSession.notes}
                </div>
              )}
              <button className="btn-secondary" style={{ width: '100%' }} onClick={() => setView('history')}>
                Open history
              </button>
            </>
          ) : (
            <div style={{ fontSize: '13px', lineHeight: 1.5, color: 'var(--text-muted)' }}>
              Start your first session and the review card will live here.
            </div>
          )}
        </div>
      </div>
    </motion.section>
  )
}

const SummaryTile = ({ icon, label, value, note }) => (
  <div className="metric-card glass-panel">
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent-cyan)' }}>
      {icon}
      <span className="metric-label">{label}</span>
    </div>
    <div className="metric-value" style={{ fontSize: '24px' }}>{value}</div>
    <div className="metric-note">{note}</div>
  </div>
)

const Ring = ({ currentRatio, targetRatio, active, hitPulse, isDragging }) => {
  const safeCurrent = clamp(currentRatio || 0, 0, 1)
  const safeTarget = clamp(targetRatio || 0, 0, 1)
  const angle = safeTarget * 360 - 90
  const radians = (angle * Math.PI) / 180
  const radius = 118
  const handleX = 144 + radius * Math.cos(radians)
  const handleY = 144 + radius * Math.sin(radians)
  const accent = active ? 'var(--accent-orange)' : 'var(--accent-cyan)'

  return (
    <svg viewBox="0 0 288 288" style={{ position: 'absolute', inset: 0, overflow: 'visible' }}>
      <defs>
        <linearGradient id="ring-track" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor={accent} />
          <stop offset="100%" stopColor={active ? '#d77a4e' : '#92e3cf'} />
        </linearGradient>
      </defs>

      <circle cx="144" cy="144" r="128" fill="rgba(255,255,255,0.015)" />
      <circle cx="144" cy="144" r="118" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="10" />

      <motion.circle
        cx="144"
        cy="144"
        r="118"
        fill="none"
        stroke="rgba(255,255,255,0.1)"
        strokeWidth="16"
        strokeDasharray="741"
        animate={{ strokeDashoffset: 741 - 741 * safeCurrent, opacity: active ? 0.28 : 0.16 }}
        transition={{ type: 'spring', damping: 24, stiffness: 70 }}
        strokeLinecap="round"
        style={{ transform: 'rotate(-90deg)', transformOrigin: 'center', filter: 'blur(6px)' }}
      />

      <motion.circle
        cx="144"
        cy="144"
        r="118"
        fill="none"
        stroke="url(#ring-track)"
        strokeWidth={isDragging ? 10 : 8}
        strokeDasharray="741"
        animate={{ strokeDashoffset: 741 - 741 * safeTarget }}
        transition={{ type: 'spring', damping: 18, stiffness: 120 }}
        strokeLinecap="round"
        style={{ transform: 'rotate(-90deg)', transformOrigin: 'center' }}
      />

      <AnimatePresence>
        {hitPulse && (
          <motion.circle
            cx="144"
            cy="144"
            r="118"
            fill="none"
            stroke="rgba(113, 215, 192, 0.65)"
            strokeWidth="18"
            initial={{ scale: 1, opacity: 0.7 }}
            animate={{ scale: 1.42, opacity: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.8, ease: 'easeOut' }}
          />
        )}
      </AnimatePresence>

      <motion.g
        animate={{ x: handleX - 144, y: handleY - 144 }}
        transition={{ type: 'spring', damping: 20, stiffness: 240 }}
      >
        <circle cx="144" cy="144" r="14" fill="rgba(7, 16, 13, 0.95)" stroke="rgba(255,255,255,0.16)" />
        <circle cx="144" cy="144" r="6" fill={accent} />
      </motion.g>
    </svg>
  )
}

const TempGraph = ({ data, hits }) => {
  if (!data || data.length < 2) return null

  const start = data[0].t
  const end = data[data.length - 1].t
  const duration = Math.max(1, end - start)
  const temps = data.map((point) => point.temp)
  const min = Math.min(...temps)
  const max = Math.max(...temps)
  const range = Math.max(20, max - min)

  const points = data.map((point) => {
    const x = ((point.t - start) / duration) * 360
    const y = 92 - (((point.temp - min) / range) * 72)
    return `${x},${y}`
  }).join(' ')

  return (
    <svg width="100%" height="120" viewBox="0 0 360 120" preserveAspectRatio="none">
      <defs>
        <linearGradient id="session-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="rgba(113, 215, 192, 0.35)" />
          <stop offset="100%" stopColor="rgba(113, 215, 192, 0.02)" />
        </linearGradient>
      </defs>
      {hits.map((hit, index) => {
        const xStart = ((hit.endTime - hit.durationSec * 1000 - start) / duration) * 360
        const xEnd = ((hit.endTime - start) / duration) * 360
        return (
          <rect
            key={index}
            x={Math.max(0, xStart)}
            y="10"
            width={Math.max(3, xEnd - xStart)}
            height="90"
            rx="6"
            fill="rgba(231, 164, 91, 0.12)"
          />
        )
      })}
      <path d={`M 0 106 L ${points} L 360 106 Z`} fill="url(#session-fill)" />
      <polyline fill="none" stroke="rgba(113, 215, 192, 0.9)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" points={points} />
    </svg>
  )
}

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

const getHeadline = (connectionState, isHeating, isCharging, setpointReached) => {
  if (connectionState !== 'Connected') return 'Get the device connected'
  if (isCharging) return 'Charging and ready when you are'
  if (!isHeating) return 'Set your temp and start'
  if (setpointReached) return 'Session is ready'
  return 'Heating up now'
}

const getSubheadline = ({ isConnected, isHeating, isCharging, stats, status }) => {
  if (!isConnected) return 'Search for your device, then use this screen as your single place for live control.'
  if (isCharging) return stats?.chargeEtaMinutes ? `Currently at ${status?.batteryLevel || 0}%. Full charge in about ${stats.chargeEtaMinutes} minutes.` : 'Charging progress and battery timing will update here.'
  if (!isHeating) return stats?.estHeatUpMs ? `Current target should be ready in about ${Math.round(stats.estHeatUpMs / 1000)} seconds.` : 'Adjust the target on the ring or use the controls below.'
  return `Target ${status?.targetTempC || '—'}°C with ${stats?.hitCount || 0} hits detected so far.`
}

const getStateLabel = (connectionState, isHeating, isCharging, setpointReached) => {
  if (connectionState !== 'Connected') return connectionState
  if (isCharging) return 'Charging'
  if (!isHeating) return 'Idle'
  return setpointReached ? 'Ready' : 'Heating'
}

const formatHeatEstimate = (ms) => {
  if (!ms) return '—'
  return `${Math.round(ms / 1000)}s`
}

const formatClock = (seconds) => `${Math.floor(seconds / 60)}:${`${seconds % 60}`.padStart(2, '0')}`

const formatMinutes = (ms) => {
  const totalSeconds = Math.floor((ms || 0) / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}m ${seconds}s`
}

const formatSessionDate = (timestampMs) => new Date(timestampMs).toLocaleString(undefined, {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

export default OverviewView
