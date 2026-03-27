import React, { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Battery, Wind, Power, Settings, Flame, Activity, Zap, X } from 'lucide-react'
import useStore from '../store/useStore'

const OverviewView = () => {
  const { telemetry, sendCommand, currentSessionSeries, sessionHits, heatUpTimeSeconds, sessionStartBattery, hasSeenOnboarding, setHasSeenOnboarding, sessionHistory } = useStore()
  const { status, stats, connectionState } = telemetry
  const ringRef = React.useRef(null)
  const [isDragging, setIsDragging] = React.useState(false)
  const [hitPulse, setHitPulse] = React.useState(false)
  const prevHitCount = React.useRef(stats?.hitCount || 0)
  
  const isConnected = connectionState === 'Connected'
  const isHeating = status?.heaterMode > 0
  const isHitActive = stats?.isHitActive || false
  const unit = status?.isCelsius ? '°C' : '°F'

  // Observe hit count for dynamic feedback
  React.useEffect(() => {
    if (stats?.hitCount > prevHitCount.current) {
      setHitPulse(true)
      const timer = setTimeout(() => setHitPulse(false), 1200)
      prevHitCount.current = stats.hitCount
      return () => clearTimeout(timer)
    }
  }, [stats?.hitCount])
  
  const { min, max } = telemetry.deviceLimits || { min: 40, max: 210 }
  const tempRatio = status ? Math.max(0, Math.min(1, (status.targetTempC - min) / (max - min))) : 190 / 210
  const currentRatio = status ? Math.max(0, Math.min(1, (status.currentTempC - min) / (max - min))) : 0

  const isCelsius = status?.isCelsius ?? true
  
  const displayTemp = (c) => {
    if (c === undefined || c === null) return '--'
    return isCelsius ? c : Math.round(c * 1.8 + 32)
  }
  const displayDelta = (c) => {
    if (c === undefined || c === null) return '--'
    return isCelsius ? c : Math.round(c * 1.8)
  }

  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)
  const todaySessions = sessionHistory.filter((session) => session.startTimeMs >= todayStart.getTime())
  const lastSession = sessionHistory[0] || null

  const normalizeAngle = (angle) => ((angle % 360) + 360) % 360

  const angleFromEvent = (e) => {
    if (!ringRef.current) return null
    const rect = ringRef.current.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    const clientX = e.clientX
    const clientY = e.clientY
    if (clientX == null || clientY == null) return null

    const dx = clientX - centerX
    const dy = clientY - centerY
    const distance = Math.hypot(dx, dy)

    // Only treat touches near the ring as dial interaction so center taps and
    // normal vertical gestures do not accidentally hijack scrolling.
    if (distance < rect.width * 0.22 || distance > rect.width * 0.52) return null

    return normalizeAngle((Math.atan2(dy, dx) * 180) / Math.PI + 90)
  }

  const setTempFromAngle = React.useCallback((angle) => {
    const ratio = Math.max(0, Math.min(1, angle / 360))
    const nextTemp = Math.round(min + ratio * (max - min))
    const currentTarget = status?.targetTempC ?? 190
    if (nextTemp !== currentTarget) {
      sendCommand('setTemp', nextTemp)
    }
  }, [min, max, status?.targetTempC, sendCommand])
  
  const handlePointerDown = (e) => {
    if (!isConnected) return
    const angle = angleFromEvent(e)
    if (angle == null) return
    e.preventDefault()
    e.currentTarget.setPointerCapture?.(e.pointerId)
    setIsDragging(true)
    setTempFromAngle(angle)
  }

  const handlePointerMove = React.useCallback((e) => {
    if (!isDragging) return
    const angle = angleFromEvent(e)
    if (angle == null) return
    e.preventDefault()
    setTempFromAngle(angle)
  }, [isDragging, setTempFromAngle])

  React.useEffect(() => {
    if (isDragging) {
      const move = (e) => handlePointerMove(e)
      const up = () => setIsDragging(false)
      window.addEventListener('pointermove', move)
      window.addEventListener('pointerup', up)
      return () => {
        window.removeEventListener('pointermove', move)
        window.removeEventListener('pointerup', up)
      }
    }
  }, [isDragging, handlePointerMove])

  const toggleHeater = () => sendCommand('setHeater', !isHeating)

  return (
    <motion.div 
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 1.02 }}
      className="view-container"
      style={{ paddingBottom: '200px' }}
    >
      {!hasSeenOnboarding && (
        <motion.div
          layout
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          className="glass-card future-glass"
          style={{ 
            padding: '24px', 
            marginBottom: '24px', 
            width: '100%',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          <motion.div 
            animate={{ 
              background: ['radial-gradient(circle at 0% 0%, rgba(6, 182, 212, 0.15), transparent)', 'radial-gradient(circle at 100% 100%, rgba(249, 115, 22, 0.15), transparent)']
            }}
            transition={{ duration: 5, repeat: Infinity, repeatType: 'reverse' }}
            style={{ position: 'absolute', inset: 0, zIndex: -1 }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
            <h3 style={{ margin: 0, fontSize: '18px', fontWeight: '900', letterSpacing: '-0.02em', className: 'text-glow-cyan' }}>Welcome to SBTracker</h3>
            <button onClick={() => setHasSeenOnboarding(true)} style={{ background: 'none', border: 'none', color: '#fff', opacity: 0.5, cursor: 'pointer' }}><X size={16} /></button>
          </div>
          <p style={{ margin: 0, fontSize: '12px', opacity: 0.7, lineHeight: '1.6', fontWeight: '500' }}>
            Control your device, start a session, and review your history from one place. Temperature, battery, and session stats update here in real time while you use the app.
          </p>
          <motion.button 
            whileHover={{ scale: 1.05, boxShadow: '0 0 20px rgba(6, 182, 212, 0.4)' }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setHasSeenOnboarding(true)}
            style={{ marginTop: '16px', padding: '10px 24px', borderRadius: '100px', background: 'var(--accent-cyan)', color: '#000', border: 'none', fontWeight: '900', fontSize: '11px', letterSpacing: '0.1em', cursor: 'pointer' }}
          >
            GOT IT
          </motion.button>
        </motion.div>
      )}

      {/* V1.0 Hit Glassmorphism Overlay */}
      <AnimatePresence>
        {isHitActive && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            style={{
              position: 'fixed', inset: 0, zIndex: 1,
              background: 'radial-gradient(circle at center, rgba(34, 211, 238, 0.05) 0%, transparent 70%)',
              backdropFilter: 'blur(8px)',
              pointerEvents: 'none'
            }}
          />
        )}
      </AnimatePresence>

      {!isHeating && isConnected && (
        <motion.div
          layout
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="glass-card future-glass"
          style={{
            padding: '24px',
            marginBottom: '20px',
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}
          >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '10px', fontWeight: '800', opacity: 0.5, letterSpacing: '0.2em' }}>READY</span>
            <motion.div 
              animate={{ opacity: [0.5, 1, 0.5] }}
              transition={{ duration: 2, repeat: Infinity }}
              style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '10px', fontWeight: '700' }}
            >
              <Zap size={12} color="var(--accent-orange)" />
              <span style={{ opacity: 0.8 }}>{stats?.sessionsRemaining || 0} sessions left</span>
            </motion.div>
          </div>
          <div style={{ display: 'flex', gap: '24px' }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '32px', fontWeight: '900', letterSpacing: '-0.04em' }} className="text-glow-orange">{displayTemp(status?.targetTempC)}°</div>
              <div style={{ fontSize: '9px', opacity: 0.4, fontWeight: '700', textTransform: 'uppercase', marginTop: '4px' }}>Target Temp</div>
            </div>
            <div style={{ flex: 1, textAlign: 'right' }}>
              <div style={{ fontSize: '32px', fontWeight: '900', letterSpacing: '-0.04em', color: 'var(--accent-orange)' }}>
                {stats?.estHeatUpMs ? Math.round(stats.estHeatUpMs / 1000) : '--'}
                <span style={{ fontSize: '14px', opacity: 0.5, fontWeight: '500' }}>s</span>
              </div>
              <div style={{ fontSize: '9px', opacity: 0.4, fontWeight: '700', textTransform: 'uppercase', marginTop: '4px' }}>Est. Heat-up</div>
            </div>
          </div>
        </motion.div>
      )}

      {/* [F-060] Cleaning Reminder Banner */}
      {(telemetry.extended?.heaterRuntimeMinutes || 0) >= 600 && !isHeating && (
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          style={{
            background: 'rgba(251, 146, 60, 0.1)',
            border: '1px solid rgba(251, 146, 60, 0.2)',
            borderRadius: '12px',
            padding: '12px 16px',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            zIndex: 10
          }}
        >
          <Settings size={16} color="#fb923c" className="pulse-slow" />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '11px', fontWeight: '800', color: '#fb923c', textTransform: 'uppercase' }}>Cleaning Reminder</div>
            <div style={{ fontSize: '10px', opacity: 0.6 }}>This device has about 10 hours of heater use since its last clean.</div>
          </div>
        </motion.div>
      )}

      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: '32px', zIndex: 5 }}>
        <div 
          ref={ringRef}
          className="temp-ring-container" 
          onPointerDown={handlePointerDown}
          style={{ cursor: isConnected ? 'pointer' : 'default', touchAction: 'none' }}
        >
          <Ring status={status} isHeating={isHeating} tempRatio={tempRatio} currentRatio={currentRatio} hitPulse={hitPulse || isHitActive} isDragging={isDragging} deviceLimits={telemetry.deviceLimits} />
          
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 10, pointerEvents: 'none' }}>
            <motion.div 
              className="temp-text"
              animate={{
                scale: isHitActive ? [1, 1.05, 1] : (isHeating ? [1, 1.02, 1] : 1),
                color: isDragging ? 'var(--accent-orange)' : (isHitActive ? 'var(--accent-cyan)' : '#fff'),
                opacity: isConnected ? 1 : 0.5
              }}
              transition={{ repeat: isHitActive ? Infinity : 0, duration: 0.5 }}
              style={{ className: isHitActive ? 'text-glow-cyan' : (isHeating ? 'text-glow-orange' : '') }}
            >
              {isConnected ? (isDragging ? displayTemp(status?.targetTempC) : displayTemp(status?.currentTempC)) : '--'}
              <span className="temp-unit" style={{ color: 'rgba(255,255,255,0.2)', fontWeight: '400' }}>{unit}</span>
            </motion.div>
            
            <div style={{ height: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
                <AnimatePresence mode="wait">
                  {status?.isSynthetic && !isDragging && (
                    <motion.div 
                      key="est"
                      initial={{ opacity: 0 }} animate={{ opacity: 0.6 }} exit={{ opacity: 0 }}
                      className="glass-card"
                      style={{ fontSize: '8px', letterSpacing: '0.1em', color: 'var(--accent-orange)', padding: '2px 8px', borderRadius: '4px', border: '1px solid rgba(249, 115, 22, 0.3)', fontWeight: '800' }}
                    >
                      ESTIMATED DATA
                    </motion.div>
                  )}
                  {isDragging ? (
                    <motion.span 
                      key="set-target"
                      initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -5 }}
                      style={{ fontSize: '11px', fontWeight: '900', color: 'var(--accent-orange)', letterSpacing: '0.2em' }}
                    >
                      ADJUSTING...
                    </motion.span>
                  ) : isHeating ? (
                    <motion.div
                      key="heating"
                      initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -5 }}
                      style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}
                    >
                        <span style={{ fontSize: '10px', letterSpacing: '0.3em', textTransform: 'uppercase', color: isHitActive ? 'var(--accent-cyan)' : 'var(--accent-orange)', fontWeight: '900' }} className={isHitActive ? 'text-glow-cyan' : 'text-glow-orange'}>
                        {isHitActive ? 'IN SESSION' : 'HEATING'}
                      </span>
                    </motion.div>
                  ) : (
                    <motion.span 
                      key="ready"
                      initial={{ opacity: 0 }} animate={{ opacity: 0.5 }} exit={{ opacity: 0 }}
                      style={{ fontSize: '10px', letterSpacing: '0.2em', fontWeight: '700', opacity: 0.4 }}
                    >
                      READY
                    </motion.span>
                  )}
                </AnimatePresence>
            </div>
          </div>
        </div>
      </div>

      <div className="control-panel" style={{ width: '100%', maxWidth: '340px', zIndex: 10 }}>
        {!isHeating ? (
          <div className="glass-card" style={{ padding: '24px' }}>
            {status?.isCharging && (
              <div style={{ marginBottom: '20px', paddingBottom: '20px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em', color: '#22d3ee' }}>Fast Charging</div>
                  <div style={{ fontSize: '16px', fontWeight: '800', color: '#fff' }}>{status?.batteryLevel}%</div>
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                   <div className="glass-card" style={{ flex: 1, padding: '12px', background: 'rgba(34,211,238,0.05)', border: 'none', textAlign: 'center' }}>
                     <div style={{ fontSize: '8px', opacity: 0.4, textTransform: 'uppercase', marginBottom: '4px' }}>Time to Full</div>
                     <div style={{ fontSize: '14px', fontWeight: '800' }}>{stats?.chargeEtaMinutes ? `${stats.chargeEtaMinutes}m` : 'Calculating...'}</div>
                   </div>
                   <div className="glass-card" style={{ flex: 1, padding: '12px', background: 'rgba(34,211,238,0.05)', border: 'none', textAlign: 'center' }}>
                     <div style={{ fontSize: '8px', opacity: 0.4, textTransform: 'uppercase', marginBottom: '4px' }}>Rate</div>
                     <div style={{ fontSize: '14px', fontWeight: '800', color: '#22d3ee' }}>+{stats?.chargeRatePctPerMin?.toFixed(1) || '0.0'}%<span style={{fontSize: '8px', opacity: 0.5}}>/m</span></div>
                   </div>
                </div>
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em', opacity: 0.4 }}>Session Settings</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <button className="adj-btn" onClick={(e) => { e.stopPropagation(); sendCommand('setTemp', (status?.targetTempC || 190) - 1); }}>-</button>
                <div style={{ fontSize: '20px', fontWeight: '800', color: '#fb923c', minWidth: '45px', textAlign: 'center' }}>{displayTemp(status?.targetTempC || 190)}°</div>
                <button className="adj-btn" onClick={(e) => { e.stopPropagation(); sendCommand('setTemp', (status?.targetTempC || 190) + 1); }}>+</button>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div className="adjustment-row">
                  <span className="adj-label">Boost</span>
                  <div className="adj-controls">
                    <button className="adj-btn" onClick={() => sendCommand('setBoostDelta', Math.max(0, (status?.boostOffsetC || 15) - 1))}>-</button>
                    <span className="adj-value" style={{ color: '#22d3ee' }}>+{displayDelta(status?.boostOffsetC || 15)}°</span>
                    <button className="adj-btn" onClick={() => sendCommand('setBoostDelta', Math.min(30, (status?.boostOffsetC || 15) + 1))}>+</button>
                  </div>
                </div>
                <div className="adjustment-row">
                  <span className="adj-label">Super</span>
                  <div className="adj-controls">
                    <button className="adj-btn" onClick={() => sendCommand('setSuperBoostDelta', Math.max(0, (status?.superBoostOffsetC || 30) - 2))}>-</button>
                    <span className="adj-value" style={{ color: '#f43f5e' }}>+{displayDelta(status?.superBoostOffsetC || 30)}°</span>
                    <button className="adj-btn" onClick={() => sendCommand('setSuperBoostDelta', Math.min(30, (status?.superBoostOffsetC || 30) + 2))}>+</button>
                  </div>
                </div>
            </div>
          </div>
        ) : (
          <div className="glass-card" style={{ padding: '24px', position: 'relative', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '100px', opacity: 0.25, pointerEvents: 'none' }}>
              <TempGraph data={currentSessionSeries} hits={sessionHits} />
            </div>

            <AnimatePresence>
              {isHitActive && (
                <ActiveHitBanner duration={stats?.currentHitDurationSec || 0} />
              )}
            </AnimatePresence>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', zIndex: 5, position: 'relative' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                  <div style={{ fontSize: '10px', opacity: 0.4, textTransform: 'uppercase', letterSpacing: '0.1em' }}>Target {displayTemp(status?.targetTempC)}{unit}</div>
                  <div style={{ background: isHitActive ? 'rgba(34, 211, 238, 0.2)' : 'rgba(251, 146, 60, 0.2)', color: isHitActive ? '#22d3ee' : '#fb923c', fontSize: '10px', fontWeight: '800', padding: '2px 8px', borderRadius: '10px', letterSpacing: '0.05em', transition: 'all 0.3s' }}>{stats?.hitCount || 0} HITS</div>
                </div>
                <div style={{ fontSize: '28px', fontWeight: '900', letterSpacing: '-0.02em' }}>{isConnected ? displayTemp(status?.currentTempC) : '--'}{unit}</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <Activity size={24} className={status?.heaterMode > 1 ? 'pulse-fast' : 'pulse-slow'} color={status?.heaterMode === 3 ? '#f43f5e' : (status?.heaterMode === 2 ? '#22d3ee' : '#fb923c')} />
                <div style={{ fontSize: '9px', opacity: 0.4, marginTop: '4px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  {status?.heaterMode === 3 ? 'SuperBoost' : (status?.heaterMode === 2 ? 'Boost' : 'Standard')}
                </div>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', marginBottom: '24px', zIndex: 5, position: 'relative' }}>
              <div className="glass-card" style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', border: 'none', alignItems: 'center' }}>
                <span style={{ fontSize: '9px', opacity: 0.3, textTransform: 'uppercase' }}>Elapsed</span>
                <span style={{ fontSize: '14px', fontWeight: '700' }}>{stats ? `${Math.floor(stats.durationSeconds / 60)}:${(stats.durationSeconds % 60).toString().padStart(2, '0')}` : '--:--'}</span>
              </div>
              <div className="glass-card" style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', border: 'none', alignItems: 'center' }}>
                <span style={{ fontSize: '9px', opacity: 0.3, textTransform: 'uppercase' }}>Heatup</span>
                <span style={{ fontSize: '14px', fontWeight: '700', color: heatUpTimeSeconds ? '#4ade80' : '#fb923c' }}>
                  {heatUpTimeSeconds ? `${heatUpTimeSeconds}s` : '...'}
                </span>
              </div>
              <div className="glass-card" style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', border: 'none', alignItems: 'center' }}>
                <span style={{ fontSize: '9px', opacity: 0.3, textTransform: 'uppercase' }}>Drain</span>
                <span style={{ fontSize: '14px', fontWeight: '700', color: '#f43f5e' }}>
                  {sessionStartBattery ? `-${sessionStartBattery - status.batteryLevel}%` : '0%'}
                </span>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', zIndex: 5, position: 'relative' }}>
                <button 
                  className={`btn-secondary ${status?.heaterMode === 2 ? 'active' : ''}`}
                  onClick={() => sendCommand('setBoostPulse', status?.heaterMode !== 2)}
                  style={{ padding: '12px' }}
                >
                  <Wind size={14} color="#22d3ee" style={{ marginRight: '8px' }} />
                  +{displayDelta(status?.boostOffsetC || 15)}°
                </button>
                <button 
                  className={`btn-secondary ${status?.heaterMode === 3 ? 'active' : ''}`}
                  onClick={() => sendCommand('setSuperBoostPulse', status?.heaterMode !== 3)}
                  style={{ padding: '12px' }}
                >
                  <Activity size={14} color="#f43f5e" style={{ marginRight: '8px' }} />
                  +{displayDelta(status?.superBoostOffsetC || 30)}°
                </button>
            </div>
          </div>
        )}

        <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
          <button 
            className={`btn-secondary ${telemetry.prefs?.defaultIsCapsule ? 'active' : ''}`}
            onClick={() => sendCommand('setDefaultIsCapsule', !telemetry.prefs?.defaultIsCapsule)}
            style={{ flex: 1, height: '44px', fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em' }}
          >
            {telemetry.prefs?.defaultIsCapsule ? 'Capsule' : 'Loose Fill'}
          </button>
        </div>

        <motion.button 
          whileHover={{ scale: 1.02, boxShadow: isHeating ? '0 0 30px rgba(239, 68, 68, 0.3)' : '0 0 30px rgba(249, 115, 22, 0.4)' }}
          whileTap={{ scale: 0.98 }}
          className={isHeating ? "btn-stop" : "btn-ignite"} 
          onClick={toggleHeater} 
          style={{ 
            marginTop: '12px', 
            width: '100%', 
            height: '64px', 
            borderRadius: '20px',
            background: isHeating ? 'rgba(239, 68, 68, 0.15)' : 'linear-gradient(135deg, var(--accent-orange), var(--accent-red))',
            border: isHeating ? '1px solid var(--accent-red)' : 'none',
            boxShadow: isHeating ? 'none' : '0 12px 32px rgba(249, 115, 22, 0.3)'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '16px' }}>
            {isHeating ? <Power size={24} /> : <Flame size={24} />}
            <span style={{ fontSize: '18px', fontWeight: '900', letterSpacing: '0.15em' }}>
              {isHeating ? 'STOP' : 'START'}
            </span>
          </div>
        </motion.button>

        {!isConnected && (
            <motion.button
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="btn-secondary"
              onClick={() => sendCommand('startScan')}
              style={{ marginTop: '16px', width: '100%', height: '56px', color: '#22d3ee', border: '1px solid rgba(34, 211, 238, 0.2)', cursor: 'pointer' }}
            >
              Find Devices
            </motion.button>
        )}
      </div>

      <div style={{ width: '100%', maxWidth: '340px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div className="glass-card future-glass" style={{ padding: '18px 20px' }}>
          <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.12em', opacity: 0.45, marginBottom: '10px' }}>Today</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
            <div>
              <div style={{ fontSize: '30px', fontWeight: '900', letterSpacing: '-0.04em' }}>{todaySessions.length}</div>
              <div style={{ fontSize: '11px', opacity: 0.5 }}>sessions</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '20px', fontWeight: '800', color: 'var(--accent-cyan)' }}>{todaySessions.reduce((sum, session) => sum + (session.hitCount || 0), 0)}</div>
              <div style={{ fontSize: '11px', opacity: 0.5 }}>hits today</div>
            </div>
          </div>
        </div>

        {lastSession && (
          <div className="glass-card" style={{ padding: '18px 20px' }}>
            <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.12em', opacity: 0.45, marginBottom: '10px' }}>Last Session</div>
            <div style={{ fontSize: '15px', fontWeight: '800', marginBottom: '6px' }}>{formatSessionDate(lastSession.startTimeMs)}</div>
            <div style={{ display: 'flex', gap: '14px', fontSize: '11px', opacity: 0.6 }}>
              <span>{formatMinutes(lastSession.durationMs)}</span>
              <span>{lastSession.hitCount || 0} hits</span>
              <span>{displayTemp(lastSession.peakTempC)}{unit}</span>
            </div>
            {lastSession.notes && (
              <div style={{ marginTop: '10px', fontSize: '11px', opacity: 0.45, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {lastSession.notes}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Minimalist Stats Bar */}
      <div style={{ display: 'flex', gap: '20px', marginTop: '32px', opacity: 0.6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Battery size={14} color="#22d3ee" />
          <span style={{ fontSize: '12px', fontWeight: '600' }}>{status?.batteryLevel || 0}%</span>
          {stats?.sessionsRemaining > 0 && stats?.drainEstimateReliable && (
            <span style={{ fontSize: '10px', opacity: 0.5, marginLeft: '4px' }}>• {stats.sessionsRemaining} left</span>
          )}
        </div>
        <motion.div 
          animate={hitPulse ? { scale: [1, 1.4, 1], color: ['#fb923c', '#fff', '#fb923c'] } : {}}
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <Wind size={14} color="#fb923c" />
          <span style={{ fontSize: '12px', fontWeight: '600' }}>{stats?.hitCount || 0} Hits</span>
        </motion.div>
      </div>

      <div style={{ height: '40px' }} />
    </motion.div>
  )
}

// Sub-components
const Ring = ({ status, isHeating, tempRatio, currentRatio, hitPulse, isDragging, deviceLimits }) => {
  const min = deviceLimits?.min ?? 40; 
  const max = deviceLimits?.max ?? (status?.deviceType === 'Venty' || status?.deviceType === 'Volcano Hybrid' ? 230 : 210);
  
  const safeTempRatio = Math.max(0, Math.min(1, tempRatio || 0));
  const safeCurrentRatio = Math.max(0, Math.min(1, currentRatio || 0));
  
  const angle = (safeTempRatio * 360) - 90;
  const angleRad = (angle * Math.PI) / 180;
  const radius = 130;
  const handleX = 144 + radius * Math.cos(angleRad);
  const handleY = 144 + radius * Math.sin(angleRad);

  const ticks = [];
  for (let i = 0; i < 72; i++) {
    const a = (i * 5) - 90;
    const ar = (a * Math.PI) / 180;
    const tickLen = i % 2 === 0 ? 12 : 6;
    const x1 = 144 + (radius - tickLen) * Math.cos(ar);
    const y1 = 144 + (radius - tickLen) * Math.sin(ar);
    const x2 = 144 + (radius) * Math.cos(ar);
    const y2 = 144 + (radius) * Math.sin(ar);
    const isActive = i * 5 <= safeTempRatio * 360;
    ticks.push(
      <line 
        key={i} 
        x1={x1} y1={y1} x2={x2} y2={y2} 
        stroke={isActive ? "var(--accent-cyan)" : "white"} 
        strokeWidth={i % 2 === 0 ? "1.5" : "1"} 
        opacity={isActive ? 0.8 : 0.15} 
        style={{ transition: 'stroke 0.3s, opacity 0.3s' }}
      />
    );
  }

  return (
    <svg style={{ position: 'absolute', inset: 0, overflow: 'visible' }} viewBox="0 0 288 288">
      <defs>
        <radialGradient id="ringGlow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor={isHeating ? "var(--accent-orange)" : "var(--accent-cyan)"} stopOpacity="0.2" />
          <stop offset="100%" stopColor="transparent" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="ringGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor={isHeating ? "var(--accent-orange)" : "var(--accent-cyan)"} />
          <stop offset="100%" stopColor={isHeating ? "var(--accent-red)" : "var(--accent-purple)"} />
        </linearGradient>
      </defs>

      <motion.circle 
        cx="144" cy="144" r="160" 
        fill="url(#ringGlow)"
        animate={{ scale: isHeating ? [0.9, 1.1, 0.9] : 1 }}
        transition={{ duration: 3, repeat: Infinity }}
      />

      <g className={`radial-ticks ${isDragging ? 'active' : ''}`}>
        {ticks}
      </g>

      <AnimatePresence>
        {hitPulse && (
          <motion.circle
            key="hit-pulse"
            cx="144" cy="144" r="130"
            fill="none"
            stroke="var(--accent-cyan)"
            strokeWidth="30"
            initial={{ scale: 1, opacity: 0.6 }}
            animate={{ scale: 1.6, opacity: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 1, ease: "easeOut" }}
          />
        )}
      </AnimatePresence>

      <circle
        cx="144" cy="144" r="130"
        className="radial-track-bg"
        fill="none"
        strokeWidth="2"
        stroke="rgba(255,255,255,0.05)"
      />

      <motion.circle
        cx="144" cy="144" r="130"
        fill="none"
        stroke={isHeating ? "var(--accent-orange)" : "var(--accent-cyan)"}
        strokeWidth="12"
        strokeDasharray="816"
        animate={{ 
          strokeDashoffset: 816 - (816 * safeCurrentRatio),
          opacity: isHeating || isDragging ? 0.3 : 0.1
        }}
        transition={{ type: 'spring', damping: 40, stiffness: 60 }}
        strokeLinecap="round"
        style={{ transform: 'rotate(-90deg)', transformOrigin: 'center', filter: 'blur(8px)' }}
      />
      
      <motion.circle
        cx="144" cy="144" r="130"
        fill="none"
        stroke="url(#ringGrad)"
        strokeWidth={isDragging ? 10 : 6}
        strokeDasharray="816"
        initial={{ strokeDashoffset: 816 }}
        animate={{ 
          strokeDashoffset: 816 - (816 * safeTempRatio),
          strokeWidth: isDragging ? 12 : 6
        }}
        transition={{ type: 'spring', damping: 25, stiffness: 200 }}
        strokeLinecap="round"
        style={{ 
          transform: 'rotate(-90deg)', 
          transformOrigin: 'center', 
          filter: `drop-shadow(0 0 ${isDragging ? '15px' : '8px'} ${isHeating ? 'var(--accent-orange)' : 'var(--accent-cyan)'}88)` 
        }}
      />

      <motion.g 
        className="radial-handle"
        animate={{ x: handleX - 144, y: handleY - 144 }}
        transition={{ type: 'spring', damping: 20, stiffness: 400 }}
      >
        <circle cx="144" cy="144" r="16" fill="rgba(0,0,0,0.8)" stroke="rgba(255,255,255,0.2)" strokeWidth="1" />
        <circle cx="144" cy="144" r="8" fill={isHeating ? "var(--accent-orange)" : "var(--accent-cyan)"} style={{ filter: 'drop-shadow(0 0 10px currentColor)' }} />
      </motion.g>
    </svg>
  )
}

const TempGraph = ({ data, hits = [] }) => {
  if (data.length < 2) return null
  const temps = data.map(d => d.temp)
  const startTime = data[0].t
  const endTime = data[data.length - 1].t
  const totalDuration = endTime - startTime
  const minT = Math.min(...temps)
  const maxT = Math.max(...temps)
  const range = Math.max(maxT - minT, 20)
  const pad = 5
  const points = data.map((d, i) => {
    const x = ((d.t - startTime) / totalDuration) * 360
    const y = 60 - (((d.temp - minT + pad) / (range + pad * 2)) * 60)
    return `${x},${y}`
  }).join(' ')

  return (
    <svg width="100%" height="60" viewBox="0 0 360 60" preserveAspectRatio="none">
      <defs>
        <linearGradient id="lineGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#22d3ee" stopOpacity="0.4" />
          <stop offset="100%" stopColor="#22d3ee" stopOpacity="0" />
        </linearGradient>
      </defs>
      {hits.map((hit, i) => {
        const xStart = ((hit.endTime - hit.durationSec * 1000 - startTime) / totalDuration) * 360
        const xEnd = ((hit.endTime - startTime) / totalDuration) * 360
        const width = Math.max(xEnd - xStart, 4)
        return (
          <rect
            key={i} x={xStart} y="0" width={width} height="60"
            fill="rgba(34, 211, 238, 0.15)"
            stroke="rgba(34, 211, 238, 0.2)"
            strokeWidth="0.5"
          />
        )
      })}
      <path d={`M 0 60 L ${points} L 360 60 Z`} fill="url(#lineGrad)" />
      <polyline
        fill="none" stroke="#22d3ee" strokeWidth="2"
        strokeLinejoin="round" strokeLinecap="round" points={points}
      />
    </svg>
  )
}

const ActiveHitBanner = ({ duration }) => (
  <motion.div
    initial={{ opacity: 0, y: -20, scale: 0.9 }}
    animate={{ opacity: 1, y: 0, scale: 1 }}
    exit={{ opacity: 0, scale: 0.9 }}
    className="glass-card"
    style={{
      background: 'rgba(34, 211, 238, 0.12)',
      borderColor: 'rgba(34, 211, 238, 0.3)',
      padding: '12px 20px',
      marginBottom: '16px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      zIndex: 10,
      position: 'relative',
      backdropFilter: 'blur(20px)',
      boxShadow: '0 8px 32px rgba(34, 211, 238, 0.2)'
    }}
  >
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
      <div className="pulse-fast" style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#22d3ee', boxShadow: '0 0 15px #22d3ee' }} />
      <span style={{ fontSize: '11px', fontWeight: '900', letterSpacing: '0.15em', color: '#fff', textTransform: 'uppercase' }}>Draw In Progress</span>
    </div>
    <span style={{ fontSize: '20px', fontWeight: '900', color: '#22d3ee', fontFamily: 'monospace' }}>{duration}s</span>
  </motion.div>
)

const formatMinutes = (ms) => {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}m ${seconds}s`
}

const formatSessionDate = (ms) => new Date(ms).toLocaleString(undefined, {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit'
})


export default OverviewView
