import React from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Calendar, Clock3, Flame, Search, Star, Thermometer, Wind, X } from 'lucide-react'
import useStore from '../store/useStore'

const ranges = ['7d', '30d', '90d', 'All']

const HistoryView = () => {
  const { sessionHistory, telemetry, sendCommand, setView } = useStore()
  const isCelsius = telemetry.status?.isCelsius ?? true
  const unit = isCelsius ? '°C' : '°F'

  const [selectedId, setSelectedId] = React.useState(null)
  const [searchQuery, setSearchQuery] = React.useState('')
  const [range, setRange] = React.useState('30d')
  const [tempNote, setTempNote] = React.useState('')
  const [tempRating, setTempRating] = React.useState(0)

  const selectedSession = sessionHistory.find((session) => session.id === selectedId) || null

  React.useEffect(() => {
    if (!selectedSession) return
    setTempNote(selectedSession.notes || '')
    setTempRating(selectedSession.rating || 0)
  }, [selectedSession])

  const filteredHistory = sessionHistory.filter((session) => {
    if (range !== 'All') {
      const diffDays = (Date.now() - session.startTimeMs) / (1000 * 60 * 60 * 24)
      if (range === '7d' && diffDays > 7) return false
      if (range === '30d' && diffDays > 30) return false
      if (range === '90d' && diffDays > 90) return false
    }

    if (!searchQuery.trim()) return true
    const query = searchQuery.trim().toLowerCase()
    return formatDate(session.startTimeMs).toLowerCase().includes(query)
      || (session.notes || '').toLowerCase().includes(query)
  })

  const summary = React.useMemo(() => {
    if (!filteredHistory.length) {
      return { sessions: 0, hits: 0, avgDurationMin: 0, avgDrain: 0 }
    }

    const totals = filteredHistory.reduce((acc, session) => {
      acc.hits += session.hitCount || 0
      acc.durationMs += session.durationMs || 0
      acc.drain += session.batteryConsumed || 0
      return acc
    }, { hits: 0, durationMs: 0, drain: 0 })

    return {
      sessions: filteredHistory.length,
      hits: totals.hits,
      avgDurationMin: totals.durationMs / filteredHistory.length / 60000,
      avgDrain: totals.drain / filteredHistory.length,
    }
  }, [filteredHistory])

  const saveSessionMeta = () => {
    if (!selectedId) return
    sendCommand('setSessionNote', { id: selectedId, notes: tempNote })
    sendCommand('setSessionRating', { id: selectedId, rating: tempRating })
    setSelectedId(null)
  }

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">History</p>
        <h1 className="page-title">What your sessions looked like</h1>
        <p className="page-subtitle">
          Review each session as a real event, not just a log row. Search by note or date and keep the details that help you remember what worked.
        </p>
      </header>

      <div className="toolbar">
        <div style={{ position: 'relative', flex: '1 1 220px' }}>
          <Search size={16} style={{ position: 'absolute', top: '16px', left: '14px', opacity: 0.45 }} />
          <input
            className="toolbar-input"
            style={{ paddingLeft: '40px' }}
            placeholder="Search dates or notes"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>
        {ranges.map((item) => (
          <button
            key={item}
            className={`btn-secondary range-chip ${range === item ? 'active' : ''}`}
            onClick={() => setRange(item)}
          >
            {item}
          </button>
        ))}
      </div>

      <div className="summary-grid">
        <MetricTile icon={<Calendar size={16} />} label="Sessions" value={summary.sessions} note={`${range} view`} />
        <MetricTile icon={<Wind size={16} />} label="Hits" value={summary.hits} note="Detected total" />
        <MetricTile icon={<Clock3 size={16} />} label="Avg duration" value={summary.sessions ? `${summary.avgDurationMin.toFixed(1)}m` : '—'} note="Per session" />
        <MetricTile icon={<Flame size={16} />} label="Avg drain" value={summary.sessions ? `${summary.avgDrain.toFixed(1)}%` : '—'} note="Battery used" />
      </div>

      {!filteredHistory.length ? (
        <div className="glass-card empty-state">
          <Calendar size={28} style={{ opacity: 0.5 }} />
          <div style={{ fontSize: '18px', fontWeight: 800 }}>No sessions in this range</div>
          <div style={{ maxWidth: '280px', fontSize: '13px', lineHeight: 1.55, color: 'var(--text-muted)' }}>
            Finish a session to start building history, or widen the range if you know the data is already there.
          </div>
          <button className="btn-secondary" onClick={() => setView('overview')}>Go to Home</button>
        </div>
      ) : (
        <div className="session-list">
          {filteredHistory.map((session) => (
            <motion.button
              key={session.id}
              type="button"
              className="glass-card history-card"
              onClick={() => setSelectedId(session.id)}
              whileTap={{ scale: 0.985 }}
              style={{ border: 'none', cursor: 'pointer', textAlign: 'left' }}
            >
              <div className="history-card-head">
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                    <div style={{ fontSize: '17px', fontWeight: 800 }}>{formatDate(session.startTimeMs)}</div>
                    {session.sessionKindLabel && (
                      <span className="pill" style={pillTone(session.sessionKind)}>
                        {session.sessionKindLabel}
                      </span>
                    )}
                  </div>
                  <div className="history-meta">
                    <span className="pill"><Clock3 size={12} /> {formatDuration(session.durationMs)}</span>
                    <span className="pill"><Wind size={12} /> {session.hitCount || 0} hits</span>
                    <span className="pill"><Thermometer size={12} /> {displayTemp(session.peakTempC, isCelsius)}{unit}</span>
                    <span className="pill"><Flame size={12} /> {session.batteryConsumed || 0}%</span>
                  </div>
                </div>
                {!!session.rating && (
                  <span className="pill" style={{ color: '#f3cd66' }}>
                    <Star size={12} /> {session.rating}/5
                  </span>
                )}
              </div>

              {session.sessionKindDetail && (
                <div style={{ marginTop: '10px', fontSize: '12px', color: 'var(--text-muted)' }}>
                  {session.sessionKindDetail}
                </div>
              )}

              {session.notes && (
                <div className="history-note">{session.notes}</div>
              )}
            </motion.button>
          ))}
        </div>
      )}

      <AnimatePresence>
        {selectedSession && (
          <div className="sheet-overlay">
            <motion.div
              className="sheet-backdrop"
              onClick={() => setSelectedId(null)}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            />

            <motion.div
              className="glass-card future-glass sheet-panel"
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 24 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', alignItems: 'flex-start', marginBottom: '16px' }}>
                <div>
                  <div className="view-title" style={{ marginBottom: '8px' }}>Session Review</div>
                  <h2 style={{ margin: 0, fontSize: '24px', letterSpacing: '-0.03em' }}>{formatDate(selectedSession.startTimeMs)}</h2>
                </div>
                <button className="adj-btn" onClick={() => setSelectedId(null)}>
                  <X size={16} />
                </button>
              </div>

              <div className="summary-grid" style={{ marginBottom: '18px' }}>
                <MetricTile icon={<Wind size={16} />} label="Hits" value={selectedSession.hitCount || 0} note="Detected" />
                <MetricTile icon={<Clock3 size={16} />} label="Duration" value={formatDuration(selectedSession.durationMs)} note="Heater active" />
                <MetricTile icon={<Thermometer size={16} />} label="Peak temp" value={`${displayTemp(selectedSession.peakTempC, isCelsius)}${unit}`} note="During session" />
                <MetricTile icon={<Flame size={16} />} label="Battery" value={`${selectedSession.batteryConsumed || 0}%`} note="Consumed" />
              </div>

              {selectedSession.sessionKindLabel && (
                <div className="glass-panel" style={{ padding: '14px 16px', marginBottom: '18px' }}>
                  <div className="section-heading" style={{ marginBottom: '8px' }}>Session Read</div>
                  <div style={{ fontSize: '16px', fontWeight: 800, marginBottom: '4px' }}>{selectedSession.sessionKindLabel}</div>
                  <div style={{ fontSize: '13px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
                    {selectedSession.sessionKindDetail}
                  </div>
                </div>
              )}

              <div style={{ marginBottom: '16px' }}>
                <div className="section-heading" style={{ marginBottom: '10px' }}>Rating</div>
                <div className="star-row">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      key={star}
                      className="star-button"
                      onClick={() => setTempRating(star)}
                      style={{
                        color: star <= tempRating ? '#f3cd66' : 'var(--text-soft)',
                        borderColor: star <= tempRating ? 'rgba(243, 205, 102, 0.35)' : 'rgba(255,255,255,0.08)',
                      }}
                    >
                      <Star size={18} fill={star <= tempRating ? 'currentColor' : 'none'} />
                    </button>
                  ))}
                </div>
              </div>

              <div style={{ marginBottom: '18px' }}>
                <div className="section-heading" style={{ marginBottom: '10px' }}>Notes</div>
                <textarea
                  className="sheet-textarea"
                  placeholder="What stood out about this session?"
                  value={tempNote}
                  onChange={(event) => setTempNote(event.target.value)}
                />
              </div>

              <div className="button-row">
                <button className="btn-secondary" style={{ flex: 1 }} onClick={() => setSelectedId(null)}>Cancel</button>
                <button className="btn-ignite" style={{ flex: 1 }} onClick={saveSessionMeta}>Save</button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </motion.section>
  )
}

const MetricTile = ({ icon, label, value, note }) => (
  <div className="metric-card glass-card">
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent-cyan)' }}>
      {icon}
      <span className="metric-label">{label}</span>
    </div>
    <div className="metric-value" style={{ fontSize: '24px' }}>{value}</div>
    <div className="metric-note">{note}</div>
  </div>
)

const displayTemp = (celsiusValue, isCelsius) => (
  isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8 + 32)
)

const formatDate = (timestampMs) => new Date(timestampMs).toLocaleString(undefined, {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

const formatDuration = (durationMs) => {
  const totalMinutes = Math.floor((durationMs || 0) / 60000)
  const seconds = Math.floor(((durationMs || 0) % 60000) / 1000)
  return `${totalMinutes}m ${seconds}s`
}

const pillTone = (kind) => {
  switch (kind) {
    case 'warmup_only':
      return { color: 'var(--accent-orange)', borderColor: 'rgba(231, 164, 91, 0.25)' }
    case 'heavy':
      return { color: '#f1b37a', borderColor: 'rgba(235, 107, 99, 0.2)' }
    case 'check':
      return { color: 'var(--text-muted)' }
    default:
      return { color: 'var(--accent-cyan)' }
  }
}

export default HistoryView
