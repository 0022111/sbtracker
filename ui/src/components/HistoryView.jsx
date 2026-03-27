import React from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Calendar, Clock3, Flame, Search, Star, Wind, X } from 'lucide-react'
import useStore from '../store/useStore'
import { HISTORY_RANGES, buildSessionIntelligence } from '../lib/sessionIntelligence'

const filters = [
  { id: 'all', label: 'All reads' },
  { id: 'efficient', label: 'Dense' },
  { id: 'heavy', label: 'Heavy' },
  { id: 'warmup', label: 'Warm-up only' },
]

const HistoryView = () => {
  const { sessionHistory, telemetry, sendCommand, setView } = useStore()
  const isCelsius = telemetry.status?.isCelsius ?? true

  const [selectedId, setSelectedId] = React.useState(null)
  const [searchQuery, setSearchQuery] = React.useState('')
  const [range, setRange] = React.useState('30d')
  const [filter, setFilter] = React.useState('all')
  const [tempNote, setTempNote] = React.useState('')
  const [tempRating, setTempRating] = React.useState(0)

  const intelligence = React.useMemo(
    () => buildSessionIntelligence({ telemetry, sessionHistory, range }),
    [range, sessionHistory, telemetry]
  )

  const filteredHistory = intelligence.sessions.filter((session) => {
    if (filter !== 'all' && session.storyKey !== filter) return false
    if (!searchQuery.trim()) return true

    const query = searchQuery.trim().toLowerCase()
    return formatDate(session.startTimeMs).toLowerCase().includes(query)
      || (session.notes || '').toLowerCase().includes(query)
      || session.storyLabel.toLowerCase().includes(query)
  })

  const selectedSession = filteredHistory.find((session) => session.id === selectedId)
    || intelligence.sessions.find((session) => session.id === selectedId)
    || null

  React.useEffect(() => {
    if (!selectedSession) return
    setTempNote(selectedSession.notes || '')
    setTempRating(selectedSession.rating || 0)
  }, [selectedSession])

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
        <p className="view-title">Ledger</p>
        <h1 className="page-title">Session history with a point of view.</h1>
        <p className="page-subtitle">
          Every run gets re-read into a story: efficient, heavy, warm-up-only, or quick. The log is still the source of truth, but this view tries to answer what each session actually meant.
        </p>
      </header>

      <div className="toolbar">
        <div style={{ position: 'relative', flex: '1 1 220px' }}>
          <Search size={16} style={{ position: 'absolute', top: '16px', left: '14px', opacity: 0.45 }} />
          <input
            className="toolbar-input"
            style={{ paddingLeft: '40px' }}
            placeholder="Search date, notes, or session read"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>
        {HISTORY_RANGES.map((item) => (
          <button
            key={item}
            className={`btn-secondary range-chip ${range === item ? 'active' : ''}`}
            onClick={() => setRange(item)}
          >
            {item}
          </button>
        ))}
      </div>

      <div className="filter-row">
        {filters.map((item) => (
          <button
            key={item.id}
            className={`btn-secondary range-chip ${filter === item.id ? 'active' : ''}`}
            onClick={() => setFilter(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="summary-grid">
        <MetricTile icon={<Calendar size={16} />} label="Sessions" value={filteredHistory.length} note={`${range} scope`} />
        <MetricTile icon={<Wind size={16} />} label="Productive" value={`${intelligence.summary.productiveShare}%`} note="With at least one hit" />
        <MetricTile icon={<Clock3 size={16} />} label="Avg duration" value={intelligence.summary.avgDurationMin ? `${intelligence.summary.avgDurationMin.toFixed(1)}m` : '—'} note="Session time" />
        <MetricTile icon={<Flame size={16} />} label="Top signal" value={intelligence.recommendations[0]?.title || '—'} note="From this window" />
      </div>

      {!filteredHistory.length ? (
        <div className="glass-card empty-state">
          <Calendar size={28} style={{ opacity: 0.5 }} />
          <div style={{ fontSize: '18px', fontWeight: 800 }}>No sessions match this view</div>
          <div style={{ maxWidth: '280px', fontSize: '13px', lineHeight: 1.55, color: 'var(--text-muted)' }}>
            Widen the range, drop the filter, or finish a session to give the ledger more raw material.
          </div>
          <button className="btn-secondary" onClick={() => setView('overview')}>Back to Flight</button>
        </div>
      ) : (
        <div className="session-list">
          {filteredHistory.map((session) => (
            <motion.button
              key={session.id}
              type="button"
              className={`glass-card history-card tone-${session.storyKey}`}
              onClick={() => setSelectedId(session.id)}
              whileTap={{ scale: 0.985 }}
              style={{ border: 'none', cursor: 'pointer', textAlign: 'left' }}
            >
              <div className="history-card-head">
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                    <div style={{ fontSize: '17px', fontWeight: 800 }}>{formatDate(session.startTimeMs)}</div>
                    <span className="pill">{session.storyLabel}</span>
                    <span className="pill">Score {session.sessionScore}</span>
                  </div>
                  <div className="history-meta">
                    <span className="pill"><Clock3 size={12} /> {formatDuration(session.durationMs)}</span>
                    <span className="pill"><Wind size={12} /> {session.hitCount || 0} hits</span>
                    <span className="pill"><Flame size={12} /> {session.battery || 0}% drain</span>
                    <span className="pill">{displayTemp(session.peakTempC, isCelsius)}°{isCelsius ? 'C' : 'F'}</span>
                  </div>
                </div>
                {!!session.rating && (
                  <span className="pill" style={{ color: '#f3cd66' }}>
                    <Star size={12} /> {session.rating}/5
                  </span>
                )}
              </div>

              <div className="story-copy" style={{ marginTop: '12px' }}>{session.storyDetail}</div>
              {session.notes && <div className="history-note">{session.notes}</div>}
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
                  <div className="view-title" style={{ marginBottom: '8px' }}>Session Read</div>
                  <h2 style={{ margin: 0, fontSize: '24px', letterSpacing: '-0.03em' }}>{formatDate(selectedSession.startTimeMs)}</h2>
                </div>
                <button className="adj-btn" onClick={() => setSelectedId(null)}>
                  <X size={16} />
                </button>
              </div>

              <div className="summary-grid" style={{ marginBottom: '18px' }}>
                <MetricTile icon={<Wind size={16} />} label="Hits" value={selectedSession.hitCount || 0} note="Detected" />
                <MetricTile icon={<Clock3 size={16} />} label="Duration" value={formatDuration(selectedSession.durationMs)} note="Heater active" />
                <MetricTile icon={<Flame size={16} />} label="Drain" value={`${selectedSession.battery || 0}%`} note="Battery used" />
                <MetricTile icon={<Calendar size={16} />} label="Score" value={selectedSession.sessionScore} note={selectedSession.storyLabel} />
              </div>

              <div className="glass-panel" style={{ padding: '14px 16px', marginBottom: '18px' }}>
                <div className="section-heading" style={{ marginBottom: '8px' }}>Interpretation</div>
                <div style={{ fontSize: '16px', fontWeight: 800, marginBottom: '4px' }}>{selectedSession.storyLabel}</div>
                <div style={{ fontSize: '13px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
                  {selectedSession.storyDetail}
                </div>
              </div>

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
    <div className="metric-value" style={{ fontSize: typeof value === 'string' && value.length > 12 ? '18px' : '24px' }}>{value}</div>
    <div className="metric-note">{note}</div>
  </div>
)

const formatDate = (value) => (
  new Date(value).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
)

const formatDuration = (durationMs) => {
  const totalSeconds = Math.round((durationMs || 0) / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${`${seconds}`.padStart(2, '0')}`
}

const displayTemp = (celsiusValue, isCelsius) => (
  isCelsius ? celsiusValue : Math.round(celsiusValue * 1.8 + 32)
)

export default HistoryView
