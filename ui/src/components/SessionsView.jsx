import { useState, useMemo } from 'react'
import useStore from '../store/useStore'
import { filterSessionsByRange, HISTORY_RANGES } from '../lib/historyUtils'

export default function SessionsView() {
  const { sessionHistory, chargeHistory, telemetry } = useStore()
  const [range, setRange] = useState('30d')

  const isCelsius = telemetry.status?.isCelsius ?? true

  const filteredSessions = useMemo(() => filterSessionsByRange(sessionHistory, range), [sessionHistory, range])
  const filteredCharges  = useMemo(() => filterSessionsByRange(chargeHistory, range), [chargeHistory, range])

  // Build unified timeline
  const timeline = useMemo(() => {
    const items = [
      ...filteredSessions.map(s => ({ ...s, _kind: 'session', _ts: s.startTimeMs })),
      ...filteredCharges.map(c => ({ ...c, _kind: 'charge', _ts: c.startTimeMs })),
    ]
    return items.sort((a, b) => b._ts - a._ts)
  }, [filteredSessions, filteredCharges])

  // Aggregate stats
  const totalSessions = filteredSessions.length
  const avgDrain      = totalSessions ? Math.round(filteredSessions.reduce((a, s) => a + (s.batteryConsumed || 0), 0) / totalSessions) : 0
  const avgHits       = totalSessions ? Math.round(filteredSessions.reduce((a, s) => a + (s.hitCount || 0), 0) / totalSessions * 10) / 10 : 0
  const totalCharges  = filteredCharges.length

  // Temp band analysis
  const tempBands = useMemo(() => {
    const bands = {}
    filteredSessions.forEach(s => {
      if (!s.avgTempC && !s.peakTempC) return
      const bucket = Math.round((s.avgTempC || s.peakTempC) / 5) * 5
      if (!bands[bucket]) bands[bucket] = { count: 0, hits: 0 }
      bands[bucket].count++
      bands[bucket].hits += s.hitCount || 0
    })
    return Object.entries(bands)
      .map(([temp, data]) => ({ temp: +temp, ...data, avgHits: Math.round(data.hits / data.count * 10) / 10 }))
      .sort((a, b) => b.avgHits - a.avgHits)
      .slice(0, 5)
  }, [filteredSessions])

  const displayTemp = (c) => isCelsius ? `${c}°C` : `${Math.round(c * 1.8 + 32)}°F`

  return (
    <div className="view">
      <div className="page-header">
        <div className="page-title">Sessions</div>
        <RangePicker range={range} onChange={setRange} />
      </div>

      {/* Aggregate header */}
      {totalSessions > 0 ? (
        <>
          <div className="metric-row" style={{ gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginBottom: 12 }}>
            <div className="metric-cell" style={{ textAlign: 'center' }}>
              <span className="label">Sessions</span>
              <div className="value-md">{totalSessions}</div>
            </div>
            <div className="metric-cell" style={{ textAlign: 'center' }}>
              <span className="label">Avg hits</span>
              <div className="value-md" style={{ color: 'var(--hit)' }}>{avgHits}</div>
            </div>
            <div className="metric-cell" style={{ textAlign: 'center' }}>
              <span className="label">Avg drain</span>
              <div className="value-md" style={{ color: 'var(--teal)' }}>{avgDrain > 0 ? `${avgDrain}%` : '—'}</div>
            </div>
          </div>

          {/* Temp band analysis */}
          {tempBands.length > 0 && (
            <div className="card" style={{ marginBottom: 12 }}>
              <div className="section-title">Best temp bands · avg hits</div>
              {tempBands.map((band, i) => (
                <div key={band.temp} className="info-row">
                  <span className="info-row-label">{displayTemp(band.temp)}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <TempBar value={band.avgHits} max={tempBands[0].avgHits} />
                    <span className="info-row-value" style={{ color: i === 0 ? 'var(--ember)' : undefined }}>
                      {band.avgHits} hits
                    </span>
                    <span className="body-sm">({band.count}×)</span>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Charge context */}
          {totalCharges > 0 && (
            <div className="card-sm" style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
                <span className="info-row-label">{totalCharges} charge{totalCharges !== 1 ? 's' : ''} in period</span>
                <span className="info-row-value" style={{ color: 'var(--teal)' }}>
                  ~{totalCharges ? Math.round(totalSessions / totalCharges * 10) / 10 : '—'} sessions/charge
                </span>
              </div>
            </div>
          )}

          {/* Unified timeline */}
          <div className="card" style={{ padding: '16px 20px' }}>
            <div className="section-title">Timeline</div>
            {timeline.map((item, i) => item._kind === 'session'
              ? <SessionRow key={`s-${item.id ?? i}`} session={item} isCelsius={isCelsius} />
              : <ChargeRow   key={`c-${item.id ?? i}`} charge={item} />
            )}
          </div>
        </>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">📋</div>
          <div className="empty-state-title">No sessions yet</div>
          <div className="empty-state-body">Complete a session on the Control tab and it'll appear here.</div>
        </div>
      )}
    </div>
  )
}

function SessionRow({ session, isCelsius }) {
  const dm = session.durationMs ? (session.durationMs / 60000).toFixed(1) : '—'
  const temp = session.avgTempC
    ? (isCelsius ? `${session.avgTempC}°C` : `${Math.round(session.avgTempC * 1.8 + 32)}°F`)
    : null
  const date = formatRelative(session.startTimeMs)
  const drain = session.batteryConsumed

  return (
    <div className="session-card">
      <div className="session-card-icon" style={{ background: 'var(--ember-dim)' }}>
        <span style={{ fontSize: 16 }}>🌿</span>
      </div>
      <div className="session-card-content">
        <div className="session-card-title">{dm}m · {session.hitCount ?? 0} hits{temp ? ` · ${temp}` : ''}</div>
        <div className="session-card-meta">{date}</div>
      </div>
      {drain > 0 && (
        <div className="session-card-aside">
          <div style={{ fontSize: 13, color: 'var(--teal)', fontWeight: 600 }}>−{drain}%</div>
        </div>
      )}
    </div>
  )
}

function ChargeRow({ charge }) {
  const dm = charge.durationMs ? Math.round(charge.durationMs / 60000) : null
  const gained = charge.batteryGained
  const date = formatRelative(charge.startTimeMs)

  return (
    <div className="charge-card">
      <div className="charge-arc">
        <span style={{ fontSize: 14 }}>⚡</span>
      </div>
      <div className="session-card-content">
        <div className="session-card-title" style={{ fontSize: 13, color: 'var(--teal)' }}>
          Charged {gained ? `+${gained}%` : ''}{dm ? ` · ${dm}m` : ''}
        </div>
        <div className="session-card-meta">{date}</div>
      </div>
    </div>
  )
}

function RangePicker({ range, onChange }) {
  return (
    <div style={{ display: 'flex', gap: 6 }}>
      {HISTORY_RANGES.map(r => (
        <button
          key={r}
          onClick={() => onChange(r)}
          style={{
            background: range === r ? 'var(--ember-dim)' : 'var(--surface-raised)',
            color: range === r ? 'var(--ember)' : 'var(--text-muted)',
            border: `1px solid ${range === r ? 'var(--ember)' : 'var(--border)'}`,
            borderRadius: 8,
            padding: '4px 10px',
            fontSize: 12,
            fontWeight: 600,
            cursor: 'pointer',
            fontFamily: 'inherit',
          }}
        >
          {r}
        </button>
      ))}
    </div>
  )
}

function TempBar({ value, max }) {
  const pct = max > 0 ? (value / max) * 100 : 0
  return (
    <div style={{ width: 60, height: 4, background: 'var(--surface-raised)', borderRadius: 2, overflow: 'hidden' }}>
      <div style={{ width: `${pct}%`, height: '100%', background: 'var(--ember)', borderRadius: 2 }} />
    </div>
  )
}

function formatRelative(tsMs) {
  if (!tsMs) return ''
  const diff = Date.now() - tsMs
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(diff / 3600000)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(diff / 86400000)
  if (days < 7) return `${days}d ago`
  return new Date(tsMs).toLocaleDateString([], { month: 'short', day: 'numeric' })
}
