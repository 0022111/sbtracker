import React from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { History, Calendar, Clock, Activity, Thermometer, X, Wind } from 'lucide-react'
import useStore from '../store/useStore'

const HistoryView = () => {
  const { sessionHistory, telemetry } = useStore()
  const { status } = telemetry
  const isCelsius = status?.isCelsius ?? true
  const unit = isCelsius ? '°C' : '°F'
  const displayTemp = (c) => {
    if (c === undefined || c === null) return '--'
    return isCelsius ? c : Math.round(c * 1.8 + 32)
  }

  const [selectedId, setSelectedId] = React.useState(null)
  const [searchQuery, setSearchQuery] = React.useState('')
  const [tempNote, setTempNote] = React.useState('')
  const [tempRating, setTempRating] = React.useState(0)

  const [range, setRange] = React.useState('All')

  const selectedSession = sessionHistory.find(s => s.id === selectedId)

  // Sync temp state when session is selected
  React.useEffect(() => {
    if (selectedSession) {
      setTempNote(selectedSession.notes || '')
      setTempRating(selectedSession.rating || 0)
    }
  }, [selectedId, selectedSession])

  const handleSave = () => {
    const { sendCommand } = useStore.getState()
    sendCommand('setSessionNote', { id: selectedId, notes: tempNote })
    sendCommand('setSessionRating', { id: selectedId, rating: tempRating })
    setSelectedId(null)
  }

  const filteredHistory = sessionHistory.filter(s => {
    // Range Filter
    if (range !== 'All') {
      const now = Date.now()
      const diffDays = (now - s.startTimeMs) / (1000 * 60 * 60 * 24)
      if (range === '7d' && diffDays > 7) return false
      if (range === '30d' && diffDays > 30) return false
      if (range === '90d' && diffDays > 90) return false
    }
    // Search Filter
    if (!searchQuery) return true
    const q = searchQuery.toLowerCase()
    const dateStr = formatDate(s.startTimeMs).toLowerCase()
    const notesStr = (s.notes || '').toLowerCase()
    return dateStr.includes(q) || notesStr.includes(q)
  })

  // Aggregate stats from filtered history
  const totalHits = Array.isArray(filteredHistory) ? filteredHistory.reduce((acc, s) => acc + (s.hitCount || 0), 0) : 0
  const maxTempC = Array.isArray(filteredHistory) && filteredHistory.length > 0 ? Math.max(...filteredHistory.map(s => s.peakTempC || 0)) : 0

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
      style={{ overflowX: 'hidden', paddingBottom: '160px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2 className="view-title" style={{ margin: 0 }}>Session History</h2>
        <span style={{ fontSize: '10px', opacity: 0.3 }}>{filteredHistory?.length || 0} Sessions</span>
      </div>

      {/* Quick Search */}
      <motion.div 
        layout
        className="glass-card" 
        style={{ marginBottom: '24px', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '14px' }}
      >
        <Wind size={18} style={{ color: 'var(--accent-cyan)', opacity: 0.6 }} />
        <input 
          placeholder="Search by date or notes..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{ background: 'transparent', border: 'none', color: '#fff', fontSize: '14px', width: '100%', outline: 'none', fontWeight: '500' }}
        />
        {searchQuery && <X size={16} style={{ opacity: 0.5, cursor: 'pointer' }} onClick={() => setSearchQuery('')} />}
      </motion.div>
      
      {(!filteredHistory || filteredHistory.length === 0) ? (
        <div className="glass-card" style={{ padding: '60px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px' }}>
          <div style={{ padding: '24px', borderRadius: '50%', background: 'rgba(34, 211, 238, 0.05)', border: '1px solid rgba(34, 211, 238, 0.1)' }}>
            <Activity size={40} color="#22d3ee" style={{ opacity: 0.3 }} />
          </div>
          <div style={{ maxWidth: '240px' }}>
            <span style={{ fontSize: '18px', fontWeight: '900', display: 'block', marginBottom: '8px' }}>History is Empty</span>
            <span style={{ fontSize: '12px', opacity: 0.4, lineHeight: '1.6' }}>
              Finished sessions will appear here automatically. Start a session to build your history.
            </span>
          </div>
          <button className="btn-ignite" style={{ padding: '12px 24px', fontSize: '12px' }} onClick={() => useStore.getState().setView('overview')}>Start First Session</button>
        </div>
      ) : (
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Performance Summary Card */}
          <motion.div 
            layout
            className="glass-card future-glass" 
            style={{ 
              marginBottom: '24px', 
              padding: '24px', 
              position: 'relative',
              overflow: 'hidden'
            }}
          >
            <motion.div 
              animate={{ background: ['radial-gradient(circle at 0% 0%, rgba(6, 182, 212, 0.1), transparent)', 'radial-gradient(circle at 100% 100%, rgba(249, 115, 22, 0.1), transparent)'] }}
              transition={{ duration: 10, repeat: Infinity, repeatType: 'reverse' }}
              style={{ position: 'absolute', inset: 0, zIndex: -1 }}
            />
            
            <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
               {['7d', '30d', '90d', 'All'].map(r => {
                 const isActive = r === range
                 return (
                   <motion.button 
                     key={r}
                     onClick={() => setRange(r)}
                     whileHover={{ scale: 1.05 }}
                     whileTap={{ scale: 0.95 }}
                     className={`btn-secondary ${isActive ? 'active' : ''}`}
                     style={{ padding: '8px 16px', flex: 1, color: isActive ? '#fff' : 'rgba(255,255,255,0.4)' }}
                   >
                     {r}
                   </motion.button>
                 )
               })}
            </div>

            <div style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.25em', opacity: 0.5, marginBottom: '16px', fontWeight: '800' }}>Summary</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
              <div>
                <div style={{ fontSize: '40px', fontWeight: '900', letterSpacing: '-0.04em' }} className="text-glow-cyan">{totalHits}</div>
                <div style={{ fontSize: '10px', opacity: 0.5, fontWeight: '700', textTransform: 'uppercase' }}>Total Hits Detected</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '40px', fontWeight: '900', color: 'var(--accent-orange)', letterSpacing: '-0.04em' }} className="text-glow-orange">{displayTemp(maxTempC)}<span style={{ fontSize: '18px', opacity: 0.4 }}>{unit}</span></div>
                <div style={{ fontSize: '10px', opacity: 0.5, fontWeight: '700', textTransform: 'uppercase' }}>Peak Temp</div>
              </div>
            </div>
          </motion.div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <AnimatePresence mode="popLayout">
              {filteredHistory.map((s) => (
                <motion.div 
                  layout
                  key={s.id} 
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  className="glass-card"
                  onClick={() => setSelectedId(s.id)}
                  whileHover={{ scale: 1.02, background: 'rgba(255,255,255,0.05)' }}
                  whileTap={{ scale: 0.98 }}
                  style={{ cursor: 'pointer', padding: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'relative', overflow: 'hidden' }}
                >
                  {s.notes && (
                    <motion.div 
                      layoutId={`note-indicator-${s.id}`}
                      style={{ position: 'absolute', top: '50%', transform: 'translateY(-50%)', left: 0, width: '4px', height: '60%', background: 'var(--accent-orange)', borderRadius: '0 4px 4px 0', boxShadow: '0 0 12px var(--accent-orange)' }} 
                    />
                  )}
                  <div>
                    <div style={{ fontSize: '16px', fontWeight: '800', marginBottom: '6px' }}>{formatDate(s.startTimeMs)}</div>
                    <div style={{ display: 'flex', gap: '14px', fontSize: '11px', opacity: 0.5, fontWeight: '600' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><Clock size={12} /> {formatDuration(s.durationMs)}</span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }} className="text-glow-cyan">
                        <Wind size={12} /> {s.hitCount} Hits
                      </span>
                    </div>
                    {s.notes && <div style={{ fontSize: '10px', opacity: 0.4, marginTop: '8px', fontStyle: 'italic', maxWidth: '220px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>"{s.notes}"</div>}
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '20px', fontWeight: '900', color: 'var(--accent-orange)' }} className="text-glow-orange">{displayTemp(s.peakTempC)}°</div>
                    <div style={{ fontSize: '9px', opacity: 0.4, fontWeight: '800', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Peak</div>
                  </div>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        </div>
      )}

      {/* Recap Modal */}
      <AnimatePresence>
        {selectedId && selectedSession && (
          <motion.div 
            initial={{ opacity: 0 }} 
            animate={{ opacity: 1 }} 
            exit={{ opacity: 0 }}
            style={{ position: 'fixed', inset: 0, zIndex: 999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px' }}
          >
            <motion.div 
              initial={{ backdropFilter: 'blur(0px)' }}
              animate={{ backdropFilter: 'blur(30px)' }}
              style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.85)' }} 
              onClick={() => setSelectedId(null)} 
            />
            <motion.div 
              layoutId={selectedId}
              initial={{ opacity: 0, y: 40, scale: 0.9 }} 
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 40, scale: 0.9 }}
              className="glass-card future-glass"
              style={{ width: '100%', maxWidth: '380px', padding: '32px', zIndex: 1000, position: 'relative' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                <div>
                  <h3 style={{ margin: 0, fontSize: '24px', fontWeight: '900', letterSpacing: '-0.02em' }}>Session Details</h3>
                  <p style={{ margin: '4px 0 0 0', fontSize: '11px', opacity: 0.4, fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.1em' }}>{formatDate(selectedSession.startTimeMs)}</p>
                </div>
                <motion.button 
                  whileHover={{ scale: 1.1, background: 'rgba(255,255,255,0.1)' }}
                  whileTap={{ scale: 0.9 }}
                  onClick={() => setSelectedId(null)} 
                  style={{ background: 'rgba(255,255,255,0.05)', border: 'none', color: '#fff', padding: '10px', borderRadius: '50%', cursor: 'pointer' }}
                >
                  <X size={20} />
                </motion.button>
              </div>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', margin: '32px 0' }}>
                <MetricBox label="Hits" value={selectedSession.hitCount} unit="Total" icon={<Wind size={12}/>} color="var(--accent-cyan)" />
                <MetricBox label="Peak Temp" value={displayTemp(selectedSession.peakTempC)} unit={unit} icon={<Thermometer size={12}/>} color="var(--accent-orange)" />
                <MetricBox label="Duration" value={Math.round(selectedSession.durationMs/60000)} unit="Min" />
                <MetricBox label="Battery Used" value={selectedSession.batteryConsumed || 0} unit="%" />
              </div>

              {/* Annotation & Rating */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', marginBottom: '32px' }}>
                <div>
                  <div style={{ fontSize: '10px', opacity: 0.5, textTransform: 'uppercase', fontWeight: '800', letterSpacing: '0.15em', marginBottom: '10px' }}>Notes</div>
                  <textarea 
                    value={tempNote}
                    onChange={(e) => setTempNote(e.target.value)}
                    placeholder="Add a note about this session..."
                    style={{ width: '100%', padding: '16px', borderRadius: '16px', background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.1)', color: '#fff', fontSize: '13px', minHeight: '100px', resize: 'none', outline: 'none', fontWeight: '500' }}
                  />
                </div>
                
                <div>
                  <div style={{ fontSize: '10px', opacity: 0.5, textTransform: 'uppercase', fontWeight: '800', letterSpacing: '0.15em', marginBottom: '10px' }}>Rating</div>
                  <div style={{ display: 'flex', gap: '12px' }}>
                    {[1, 2, 3, 4, 5].map((star) => (
                      <motion.div 
                        key={star} 
                        whileHover={{ scale: 1.2 }}
                        whileTap={{ scale: 0.9 }}
                        onClick={() => setTempRating(star)}
                        style={{ fontSize: '28px', cursor: 'pointer', color: star <= tempRating ? 'var(--accent-orange)' : 'rgba(255,255,255,0.1)', filter: star <= tempRating ? 'drop-shadow(0 0 8px var(--accent-orange))' : 'none' }}
                      >
                        ★
                      </motion.div>
                    ))}
                  </div>
                </div>
              </div>

              <motion.button 
                whileHover={{ scale: 1.02, boxShadow: '0 0 20px rgba(255,255,255,0.2)' }}
                whileTap={{ scale: 0.98 }}
                onClick={handleSave}
                style={{ width: '100%', padding: '20px', borderRadius: '18px', background: '#fff', color: '#000', border: 'none', fontWeight: '900', fontSize: '14px', letterSpacing: '0.1em', cursor: 'pointer' }}
              >
                SAVE CHANGES
              </motion.button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

const formatDate = (ms) => {
  return new Date(ms).toLocaleDateString(undefined, { 
    month: 'short', 
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDuration = (ms) => {
  const min = Math.floor(ms / 60000)
  const sec = Math.floor((ms % 60000) / 1000)
  return `${min}m ${sec}s`
}

const MetricBox = ({ label, value, unit, color = '#fff', icon }) => (
  <div>
    <div style={{ fontSize: '10px', opacity: 0.4, textTransform: 'uppercase', fontWeight: '800', letterSpacing: '0.1em', marginBottom: '6px', display: 'flex', alignItems: 'center', gap: '4px' }}>
      {icon} {label}
    </div>
    <div style={{ fontSize: '32px', fontWeight: '900', color: color, letterSpacing: '-0.04em' }}>
      {value}
      <span style={{ fontSize: '14px', opacity: 0.3, marginLeft: '6px', fontWeight: '600' }}>{unit}</span>
    </div>
  </div>
)

export default HistoryView
