import { useState, useEffect, useRef } from 'react'
import useStore from '../store/useStore'
import { TraceSheet } from './TraceSheet'

const clamp = (v, min, max) => Math.min(max, Math.max(min, v))

export default function ControlView() {
  const {
    telemetry,
    sendCommand,
    currentSessionSeries,
    sessionHits,
    heatUpTimeSeconds,
  } = useStore()

  const { status, stats, connectionState } = telemetry
  const isConnected = connectionState === 'Connected'
  const isHeating = (status?.heaterMode ?? 0) > 0
  const isCharging = status?.isCharging ?? false
  const isCelsius = status?.isCelsius ?? true
  const limits = telemetry.deviceLimits || { min: 40, max: 210 }
  const isHitActive = stats?.isHitActive ?? false

  // Trace sheet
  const [showTrace, setShowTrace] = useState(false)
  const [lastSession, setLastSession] = useState(null)
  const [lastHits, setLastHits] = useState([])
  const prevHeating = useRef(false)

  useEffect(() => {
    if (prevHeating.current && !isHeating && currentSessionSeries.length > 2) {
      setLastSession({
        durationMs: (currentSessionSeries.at(-1)?.t ?? 0) - (currentSessionSeries[0]?.t ?? 0),
        hitCount: stats?.hitCount ?? 0,
        avgTempC: status?.targetTempC,
        peakTempC: status?.targetTempC,
      })
      setLastHits([...sessionHits])
      setShowTrace(true)
    }
    prevHeating.current = isHeating
  }, [isHeating]) // eslint-disable-line

  const onSetTemp = (t) => sendCommand('setTemp', clamp(t, limits.min, limits.max))

  return (
    <div className="view">
      {/* Status bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <StatusIndicator state={connectionState} deviceType={status?.deviceType} />
        {isConnected && (
          <BatteryChip level={status?.batteryLevel} charging={isCharging} eta={stats?.chargeEtaMinutes} />
        )}
      </div>

      {/* Core temp display — always centered */}
      <TempDisplay
        isConnected={isConnected}
        isHeating={isHeating}
        isHitActive={isHitActive}
        isCharging={isCharging}
        status={status}
        stats={stats}
        isCelsius={isCelsius}
        limits={limits}
        onSetTemp={onSetTemp}
        sendCommand={sendCommand}
      />

      {/* State-adaptive controls below */}
      {!isConnected ? (
        <DisconnectedState onScan={() => sendCommand('startScan')} />
      ) : isCharging ? (
        <ChargingState status={status} stats={stats} />
      ) : isHeating ? (
        <HeatingState
          status={status}
          stats={stats}
          isCelsius={isCelsius}
          sessionHits={sessionHits}
          sendCommand={sendCommand}
          onStop={() => sendCommand('setHeater', false)}
        />
      ) : (
        <IdleState
          status={status}
          limits={limits}
          isCelsius={isCelsius}
          sendCommand={sendCommand}
          onStart={() => { sendCommand('setHeater', true); sendCommand('vibrate') }}
        />
      )}

      {showTrace && (
        <TraceSheet
          session={lastSession}
          hits={lastHits}
          isCelsius={isCelsius}
          onDismiss={() => setShowTrace(false)}
        />
      )}
    </div>
  )
}

/* ────────────────────────────────────────────
   TempDisplay — the hero. Ring is display-only.
   All interaction is via +/- buttons.
──────────────────────────────────────────── */
function TempDisplay({ isConnected, isHeating, isHitActive, isCharging, status, stats, isCelsius, limits, onSetTemp, sendCommand }) {
  const currentC = status?.currentTempC ?? null
  const targetC = status?.targetTempC ?? 185

  const toDisplay = (c) => isCelsius ? Math.round(c) : Math.round(c * 1.8 + 32)
  const unit = isCelsius ? '°C' : '°F'

  const size = 220
  const r = 88
  const cx = size / 2
  const cy = size / 2
  const circ = 2 * Math.PI * r
  const pct = (v) => Math.min(1, Math.max(0, (v - limits.min) / (limits.max - limits.min)))

  const currentPct = currentC !== null ? pct(currentC) : 0
  const targetPct = pct(targetC)

  // Arc dash calculations (start from top = -90deg offset)
  const currentDash = circ * currentPct
  const targetDash = circ * targetPct

  const ringColor = isHitActive
    ? 'var(--hit)'
    : isHeating
      ? 'var(--ember)'
      : 'rgba(255,255,255,0.2)'

  // What to show big in the center
  const bigNumber = !isConnected
    ? '—'
    : currentC !== null
      ? toDisplay(currentC)
      : toDisplay(targetC)

  const sublabel = !isConnected
    ? 'Not connected'
    : isHeating
      ? status?.setpointReached
        ? `At target ${toDisplay(targetC)}${unit}`
        : `→ ${toDisplay(targetC)}${unit}`
      : isCharging
        ? 'Charging'
        : `Target ${toDisplay(targetC)}${unit}`

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: 20 }}>
      {/* SVG ring — display only, no touch handling */}
      <div style={{ position: 'relative', width: size, height: size }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ display: 'block' }}>
          {/* Track */}
          <circle cx={cx} cy={cy} r={r} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth={12} />
          {/* Target ghost arc */}
          <circle cx={cx} cy={cy} r={r}
            fill="none"
            stroke="rgba(255,255,255,0.12)"
            strokeWidth={8}
            strokeDasharray={`${targetDash} ${circ - targetDash}`}
            strokeLinecap="round"
            transform={`rotate(-90 ${cx} ${cy})`}
          />
          {/* Current temp arc */}
          <circle cx={cx} cy={cy} r={r}
            fill="none"
            stroke={ringColor}
            strokeWidth={12}
            strokeDasharray={`${currentDash} ${circ - currentDash}`}
            strokeLinecap="round"
            transform={`rotate(-90 ${cx} ${cy})`}
            style={{ transition: 'stroke-dasharray 0.5s ease, stroke 0.3s ease' }}
          />
        </svg>

        {/* Center content */}
        <div style={{
          position: 'absolute', inset: 0,
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center',
          pointerEvents: 'none',
        }}>
          <div style={{
            fontSize: 52, fontWeight: 700, letterSpacing: '-0.03em', lineHeight: 1,
            color: isHitActive ? 'var(--hit)' : 'var(--text)',
            transition: 'color 0.2s',
          }}>
            {bigNumber}
          </div>
          {isConnected && (
            <div style={{ fontSize: 18, color: 'var(--text-dim)', lineHeight: 1, marginTop: 2 }}>
              {unit}
            </div>
          )}
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 6 }}>
            {sublabel}
          </div>
        </div>
      </div>

      {/* Temp +/- controls — always visible when connected, not while charging */}
      {isConnected && !isCharging && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 0, marginTop: 4 }}>
          <StepButton label="−" onPress={() => onSetTemp(targetC - 1)} />
          <div style={{ padding: '0 20px', textAlign: 'center' }}>
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Target</div>
            <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.02em' }}>
              {toDisplay(targetC)}{unit}
            </div>
          </div>
          <StepButton label="+" onPress={() => onSetTemp(targetC + 1)} />
        </div>
      )}
    </div>
  )
}

/* StepButton — tap for single step, hold for repeat */
function StepButton({ label, onPress }) {
  const intervalRef = useRef(null)
  const timeoutRef = useRef(null)

  const start = () => {
    onPress()
    // After 400ms hold, repeat every 150ms — each repeat reads fresh device state
    timeoutRef.current = setTimeout(() => {
      intervalRef.current = setInterval(onPress, 150)
    }, 400)
  }
  const stop = () => {
    clearTimeout(timeoutRef.current)
    clearInterval(intervalRef.current)
  }

  return (
    <button
      onPointerDown={start}
      onPointerUp={stop}
      onPointerLeave={stop}
      style={{
        width: 52, height: 52, borderRadius: 16,
        border: '1px solid var(--border)',
        background: 'var(--surface-raised)',
        color: 'var(--text)',
        fontSize: 26, fontWeight: 300,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer',
        WebkitTapHighlightColor: 'transparent',
        userSelect: 'none',
      }}
    >
      {label}
    </button>
  )
}

/* ── State panels ── */

function HeatingState({ status, stats, isCelsius, sessionHits, sendCommand, onStop }) {
  const boostC = status?.boostOffsetC ?? 0
  const sbC = status?.superBoostOffsetC ?? 0
  const isBoost = status?.heaterMode === 2
  const isSuperB = status?.heaterMode === 3
  const isHit = stats?.isHitActive ?? false
  const elapsed = formatSec(stats?.durationSeconds ?? 0)
  const unit = isCelsius ? '°C' : '°F'
  const toDisp = (c) => isCelsius ? c : Math.round(c * 1.8 + 32)

  const drain = stats?.batteryDrain ?? 0
  const startBat = stats?.startingBattery ?? 0
  const drainRate = stats?.drainRatePctPerMin ?? 0

  return (
    <>
      {/* Session live stats */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginBottom: 8 }}>
        <StatCell label="Elapsed" value={elapsed} />
        <StatCell label="Hits" value={stats?.hitCount ?? 0} color="var(--hit)" />
        <StatCell label="Avg hit" value={stats?.avgHitDurationSec ? `${stats.avgHitDurationSec.toFixed(1)}s` : '—'} />
      </div>

      {/* Battery drain row — only shows once we have data */}
      {startBat > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginBottom: 12 }}>
          <StatCell
            label="Drain"
            value={drain > 0 ? `−${drain}%` : '…'}
            color={drain >= 10 ? 'var(--danger)' : drain >= 5 ? 'var(--ember)' : 'var(--teal)'}
          />
          <StatCell label="Start" value={`${startBat}%`} />
          <StatCell
            label="Rate"
            value={drainRate > 0.05 ? `${drainRate.toFixed(1)}%/m` : '…'}
            color="var(--text-dim)"
          />
        </div>
      )}

      {/* Hit indicator */}
      <div className="card" style={{
        marginBottom: 12,
        borderColor: isHit ? 'var(--hit)' : 'var(--border)',
        transition: 'border-color 0.2s',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{
              fontSize: 12, fontWeight: 600, letterSpacing: '0.06em',
              textTransform: 'uppercase',
              color: isHit ? 'var(--hit)' : 'var(--text-muted)',
              marginBottom: 3,
            }}>
              {isHit ? 'Pull in progress' : 'Waiting for pull'}
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-dim)' }}>
              {isHit
                ? `${(stats?.currentHitDurationSec ?? 0).toFixed(1)}s`
                : sessionHits.length > 0
                  ? `Last: ${sessionHits.at(-1).durationSec.toFixed(1)}s at ${toDisp(sessionHits.at(-1).peakTemp ?? 0)}${unit}`
                  : 'No pulls yet'}
            </div>
          </div>
          <div style={{
            width: 10, height: 10, borderRadius: '50%',
            background: isHit ? 'var(--hit)' : 'var(--surface-high)',
            boxShadow: isHit ? '0 0 10px var(--hit)' : 'none',
            transition: 'all 0.15s',
          }} />
        </div>
      </div>

      {/* Boost controls */}
      <div className="card" style={{ marginBottom: 12 }}>
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <ModeButton
            label="Boost"
            active={isBoost}
            onClick={() => sendCommand('setBoostPulse', !isBoost)}
          />
          <ModeButton
            label="Super boost"
            active={isSuperB}
            onClick={() => sendCommand('setSuperBoostPulse', !isSuperB)}
          />
        </div>

        <AdjRow label="Boost delta" value={`+${isCelsius ? boostC : Math.round(boostC * 1.8)}${unit}`}
          onDec={() => sendCommand('setBoostDelta', Math.max(0, boostC - 1))}
          onInc={() => sendCommand('setBoostDelta', Math.min(30, boostC + 1))}
        />
        <AdjRow label="Super delta" value={`+${isCelsius ? sbC : Math.round(sbC * 1.8)}${unit}`}
          onDec={() => sendCommand('setSuperBoostDelta', Math.max(0, sbC - 1))}
          onInc={() => sendCommand('setSuperBoostDelta', Math.min(30, sbC + 1))}
        />
      </div>

      <button className="btn btn-stop" onClick={onStop} style={{ width: '100%' }}>
        Stop heater
      </button>
    </>
  )
}

function IdleState({ status, limits, isCelsius, sendCommand, onStart }) {
  const boost = status?.boostOffsetC ?? 0
  const sbC = status?.superBoostOffsetC ?? 0
  const unit = isCelsius ? '°C' : '°F'

  return (
    <>
      <div className="card" style={{ marginBottom: 12 }}>
        <AdjRow label="Boost delta" value={`+${isCelsius ? boost : Math.round(boost * 1.8)}${unit}`}
          onDec={() => sendCommand('setBoostDelta', Math.max(0, boost - 1))}
          onInc={() => sendCommand('setBoostDelta', Math.min(30, boost + 1))}
        />
        <AdjRow label="Super delta" value={`+${isCelsius ? sbC : Math.round(sbC * 1.8)}${unit}`}
          onDec={() => sendCommand('setSuperBoostDelta', Math.max(0, sbC - 1))}
          onInc={() => sendCommand('setSuperBoostDelta', Math.min(30, sbC + 1))}
        />
      </div>
      <button className="btn btn-primary" onClick={onStart} style={{ width: '100%' }}>
        Start heater
      </button>
    </>
  )
}

function ChargingState({ status, stats }) {
  const pct = status?.batteryLevel ?? 0
  const eta = stats?.chargeEtaMinutes
  const eta80 = stats?.chargeEta80Minutes
  const rate = stats?.chargeRatePctPerMin

  return (
    <div className="card" style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
        <span style={{ fontSize: 13, color: 'var(--text-dim)' }}>Battery</span>
        <span style={{ fontSize: 22, fontWeight: 700, color: 'var(--teal)' }}>{pct}%</span>
      </div>
      <div style={{ height: 8, borderRadius: 4, background: 'var(--surface-raised)', overflow: 'hidden', marginBottom: 8 }}>
        <div style={{
          width: `${pct}%`, height: '100%', borderRadius: 4,
          background: pct >= 80 ? 'var(--success)' : 'var(--teal)',
          transition: 'width 0.6s ease',
        }} />
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--text-dim)' }}>
        {rate != null && <span>{rate.toFixed(1)}%/min</span>}
        {pct < 80 && eta80 ? <span>80% in ~{eta80}m</span> : eta ? <span>Full in ~{eta}m</span> : null}
      </div>
    </div>
  )
}

function DisconnectedState({ onScan }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 20 }}>
        Scan for your S&amp;B device over Bluetooth.
      </div>
      <button className="btn btn-primary" onClick={onScan} style={{ width: '100%' }}>
        Find device
      </button>
    </div>
  )
}

/* ── Shared sub-components ── */

function AdjRow({ label, value, onDec, onInc }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '8px 0', borderTop: '1px solid var(--border-subtle)',
    }}>
      <span style={{ fontSize: 13, color: 'var(--text-dim)' }}>{label}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <button className="adj-btn" onClick={onDec}>−</button>
        <span style={{ fontSize: 16, fontWeight: 600, minWidth: 52, textAlign: 'center' }}>{value}</span>
        <button className="adj-btn" onClick={onInc}>+</button>
      </div>
    </div>
  )
}

function ModeButton({ label, active, onClick }) {
  return (
    <button onClick={onClick} style={{
      flex: 1, padding: '10px 0', borderRadius: 10,
      border: `1px solid ${active ? 'var(--ember)' : 'var(--border)'}`,
      background: active ? 'var(--ember-dim)' : 'var(--surface-raised)',
      color: active ? 'var(--ember)' : 'var(--text-dim)',
      fontFamily: 'inherit', fontSize: 13, fontWeight: 600,
      cursor: 'pointer', transition: 'all 0.15s',
    }}>
      {label}
    </button>
  )
}

function StatCell({ label, value, color }) {
  return (
    <div style={{
      background: 'var(--surface-raised)', borderRadius: 12,
      padding: '10px 12px', textAlign: 'center',
    }}>
      <div style={{ fontSize: 10, fontWeight: 600, letterSpacing: '0.07em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 18, fontWeight: 600, color: color ?? 'var(--text)' }}>
        {value}
      </div>
    </div>
  )
}

function StatusIndicator({ state, deviceType }) {
  const isConnected = state === 'Connected'
  const isBusy = ['Connecting', 'Scanning', 'Reconnecting'].includes(state)
  const color = isConnected ? 'var(--success)' : isBusy ? 'var(--ember)' : 'var(--text-muted)'
  const label = isConnected ? (deviceType || 'Connected') : isBusy ? state : 'Offline'
  return (
    <div className="status-pill">
      <div className="status-dot" style={{ background: color, boxShadow: isConnected ? `0 0 8px ${color}` : 'none' }} />
      <span style={{ fontSize: 13, fontWeight: 500 }}>{label}</span>
    </div>
  )
}

function BatteryChip({ level, charging, eta }) {
  if (level == null) return null
  const low = level < 20
  const color = charging ? 'var(--teal)' : low ? 'var(--danger)' : 'var(--text-dim)'
  return (
    <div className="status-pill">
      <span style={{ color }}>{charging ? '⚡' : '🔋'}</span>
      <span style={{ fontSize: 13, color }}>{level}%</span>
    </div>
  )
}

function formatSec(sec) {
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}
