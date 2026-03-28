import { useEffect, useRef } from 'react'
import useStore from '../store/useStore'

/**
 * TraceSheet — slides up from the bottom when a session ends.
 * Shows: stats row, temps used, hit timeline.
 * No notes, no rating. Just data.
 */
export function TraceSheet({ session, hits, isCelsius, onDismiss }) {
  const sheetRef = useRef(null)

  // Close on backdrop tap
  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) onDismiss()
  }

  const durationMin  = session ? Math.round(session.durationMs / 60000 * 10) / 10 : 0
  const hitCount     = session?.hitCount ?? hits?.length ?? 0
  const batteryUsed  = session?.batteryConsumed ?? 0
  const baseTemp     = session?.avgTempC ?? session?.targetTempC ?? null
  const peakTemp     = session?.peakTempC ?? null
  const displayTemp  = (c) => isCelsius ? `${c}°C` : `${Math.round(c * 1.8 + 32)}°F`

  // Build hit timeline bars (normalize to max duration)
  const maxDur = hits?.length ? Math.max(...hits.map(h => h.durationSec || 0), 0.1) : 1
  const hitBars = hits?.slice(-20) ?? []

  return (
    <div className="trace-sheet-backdrop animate-fade-in" onClick={handleBackdropClick}>
      <div className="trace-sheet animate-slide-up" ref={sheetRef}>
        <div className="trace-sheet-handle" />

        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
            <div style={{ fontSize: 18, fontWeight: 700 }}>Session complete</div>
            <button
              onClick={onDismiss}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 22, cursor: 'pointer', lineHeight: 1 }}
            >×</button>
          </div>
          <div className="body-sm">Your last extraction</div>
        </div>

        {/* Stats row */}
        <div className="metric-row" style={{ gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginBottom: 16 }}>
          <StatCell label="Duration" value={`${durationMin}m`} />
          <StatCell label="Hits" value={hitCount} color="var(--hit)" />
          <StatCell label="Battery" value={batteryUsed > 0 ? `-${batteryUsed}%` : '—'} color="var(--teal)" />
        </div>

        {/* Temps used */}
        {baseTemp && (
          <div className="card-sm" style={{ marginBottom: 12 }}>
            <div className="section-title">Temps used</div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              {baseTemp && (
                <span className="pill ember">Base {displayTemp(baseTemp)}</span>
              )}
              {peakTemp && peakTemp > baseTemp + 3 && (
                <span className="pill" style={{ color: 'var(--ember-strong)', borderColor: 'var(--ember)' }}>
                  Peak {displayTemp(peakTemp)}
                </span>
              )}
            </div>
          </div>
        )}

        {/* Hit timeline */}
        {hitBars.length > 0 ? (
          <div className="card-sm" style={{ marginBottom: 12 }}>
            <div className="section-title" style={{ marginBottom: 8 }}>
              Hit timeline · {hitCount} {hitCount === 1 ? 'pull' : 'pulls'}
            </div>
            <div className="hit-timeline">
              {hitBars.map((hit, i) => (
                <div
                  key={i}
                  className="hit-bar"
                  style={{ height: `${Math.max(10, (hit.durationSec / maxDur) * 100)}%` }}
                  title={`Hit ${i + 1}: ${hit.durationSec?.toFixed(1)}s`}
                />
              ))}
            </div>
            <div className="body-sm" style={{ marginTop: 8 }}>
              Each bar is one pull — height = duration
            </div>
          </div>
        ) : (
          <div className="empty-state" style={{ padding: '24px 0' }}>
            <div className="empty-state-body">No hit markers recorded in this session.</div>
          </div>
        )}

        <button className="btn btn-ghost" style={{ width: '100%', marginTop: 8 }} onClick={onDismiss}>
          Done
        </button>
      </div>
    </div>
  )
}

function StatCell({ label, value, color }) {
  return (
    <div className="card-sm" style={{ textAlign: 'center', padding: '12px 8px' }}>
      <div className="label" style={{ marginBottom: 6 }}>{label}</div>
      <div className="value-md" style={{ color: color || 'var(--text)' }}>{value}</div>
    </div>
  )
}
