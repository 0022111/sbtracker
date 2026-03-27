import React from 'react'
import { motion } from 'framer-motion'
import { Battery, Zap, Clock, ShieldCheck, Info } from 'lucide-react'
import useStore from '../store/useStore'

const BatteryView = () => {
  const { telemetry, sendCommand } = useStore()
  const { status, stats, batteryInsights } = telemetry
  
  const isCharging = status?.isCharging
  const batteryLevel = status?.batteryLevel || 0
  
  const formatChargeTime = (min) => {
    if (!min || min <= 0) return '—'
    if (min >= 60) return `${Math.floor(min/60)}h ${min%60}m`
    return `${min}m`
  }

  return (
    <motion.div 
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="view-container"
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h2 className="view-title">Battery</h2>
        <ShieldCheck size={14} style={{ opacity: 0.3 }} />
      </div>

      {/* Battery Hero */}
      <motion.div 
        layout
        className="glass-card future-glass" 
        style={{ padding: '32px 24px', position: 'relative', overflow: 'hidden', marginBottom: '24px' }}
      >
        {isCharging && (
          <motion.div 
            animate={{ 
              opacity: [0.05, 0.15, 0.05],
              background: [
                'radial-gradient(circle at 50% 50%, rgba(34, 211, 238, 0.1), transparent)',
                'radial-gradient(circle at 50% 50%, rgba(34, 211, 238, 0.2), transparent)'
              ]
            }}
            transition={{ duration: 3, repeat: Infinity }}
            style={{ position: 'absolute', inset: 0, zIndex: 0 }}
          />
        )}
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'relative', zIndex: 1 }}>
          <div>
            <div style={{ fontSize: '56px', fontWeight: '900', lineHeight: '1', display: 'flex', alignItems: 'flex-start', letterSpacing: '-0.04em' }}>
              {batteryLevel}
              <span style={{ fontSize: '24px', marginLeft: '6px', opacity: 0.3, fontWeight: '500' }}>%</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '12px' }}>
              <motion.div 
                animate={isCharging ? { scale: [1, 1.2, 1], opacity: [0.5, 1, 0.5] } : {}}
                transition={{ duration: 1.5, repeat: Infinity }}
                style={{ width: '8px', height: '8px', borderRadius: '50%', background: isCharging ? 'var(--accent-cyan)' : 'var(--accent-cyan)', boxShadow: `0 0 12px ${isCharging ? 'var(--accent-cyan)' : 'transparent'}` }} 
              />
              <span style={{ fontSize: '11px', fontWeight: '900', textTransform: 'uppercase', letterSpacing: '0.15em', opacity: 0.6 }}>
                {isCharging ? 'Charging' : 'Battery Status'}
              </span>
            </div>
          </div>
          
          <div style={{ width: '80px', height: '140px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '16px', padding: '6px', position: 'relative', boxShadow: 'inset 0 4px 12px rgba(0,0,0,0.2)' }}>
             <motion.div 
               initial={{ height: 0 }}
               animate={{ 
                 height: `${batteryLevel}%`,
                 background: isCharging ? 'linear-gradient(to top, var(--accent-cyan), var(--accent-purple))' : 'linear-gradient(to top, var(--accent-cyan), var(--accent-cyan)88)'
               }}
               transition={{ type: 'spring', damping: 20, stiffness: 100 }}
               style={{ 
                 borderRadius: '12px',
                 position: 'absolute',
                 bottom: '6px',
                 left: '6px',
                 right: '6px',
                 width: 'calc(100% - 12px)',
                 boxShadow: isCharging ? '0 0 20px rgba(34, 211, 238, 0.4)' : 'none'
               }}
             />
             <div style={{ position: 'absolute', top: '-8px', left: '50%', transform: 'translateX(-50%)', width: '24px', height: '8px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px 3px 0 0', border: '1px solid rgba(255,255,255,0.08)', borderBottom: 'none' }} />
          </div>
        </div>
        
        {isCharging && (stats.chargeEtaMinutes || stats.chargeEta80Minutes) && (
          <motion.div 
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            style={{ marginTop: '32px', paddingTop: '24px', borderTop: '1px solid rgba(255,255,255,0.05)', display: 'flex', flexDirection: 'column', gap: '16px' }}
          >
            {stats.chargeEta80Minutes && status.batteryLevel < 80 && (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <Zap size={16} color="var(--accent-cyan)" style={{ opacity: 0.6 }} />
                  <span style={{ fontSize: '12px', opacity: 0.5, fontWeight: '600', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Time to 80% Capacity</span>
                </div>
                <span style={{ fontSize: '16px', fontWeight: '900', color: 'var(--accent-cyan)' }} className="text-glow-cyan">{stats.chargeEta80Minutes}m</span>
              </div>
            )}
            {stats.chargeEtaMinutes && (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <Clock size={16} style={{ opacity: 0.4 }} />
                  <span style={{ fontSize: '12px', opacity: 0.5, fontWeight: '600', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Time to Full</span>
                </div>
                <span style={{ fontSize: '16px', fontWeight: '900', opacity: 0.8 }}>{stats.chargeEtaMinutes}m</span>
              </div>
            )}
          </motion.div>
        )}
      </motion.div>

      {/* Health & Maintenance Grid */}
      <div className="grid-stats" style={{ marginBottom: '24px' }}>
        <motion.div layout className="glass-card future-glass" style={{ padding: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
            <Zap size={16} color="var(--accent-cyan)" />
            <span style={{ fontSize: '10px', textTransform: 'uppercase', opacity: 0.5, fontWeight: '800', letterSpacing: '0.1em' }}>Efficiency</span>
          </div>
          <div style={{ fontSize: '24px', fontWeight: '900', letterSpacing: '-0.02em' }} className="text-glow-cyan">{batteryInsights?.avgDrainRecent ? `${batteryInsights.avgDrainRecent.toFixed(1)}%` : '—'}</div>
          <div style={{ fontSize: '10px', opacity: 0.4, marginTop: '6px', fontWeight: '600' }}>Avg drain per session</div>
        </motion.div>
        
        <motion.div layout className="glass-card future-glass" style={{ padding: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
            <Battery size={16} color="var(--accent-cyan)" />
            <span style={{ fontSize: '10px', textTransform: 'uppercase', opacity: 0.5, fontWeight: '800', letterSpacing: '0.1em' }}>Endurance</span>
          </div>
          <div style={{ fontSize: '24px', fontWeight: '900', letterSpacing: '-0.02em' }}>{stats.sessionsToCritical || '—'}</div>
          <div style={{ fontSize: '10px', opacity: 0.4, marginTop: '6px', fontWeight: '600' }}>Sessions to 15%</div>
        </motion.div>
      </div>

      {!isCharging && stats?.drainEstimateReliable && stats.sessionsRemainingLow > 0 && stats.sessionsRemainingHigh > 0 && stats.sessionsRemainingLow !== stats.sessionsRemainingHigh && (
        <div className="glass-card" style={{ padding: '16px 18px', marginBottom: '20px' }}>
          <div style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.12em', opacity: 0.45, marginBottom: '6px' }}>Estimate Range</div>
          <div style={{ fontSize: '14px', fontWeight: '700' }}>
            About {stats.sessionsRemainingLow} to {stats.sessionsRemainingHigh} sessions before 15%
          </div>
        </div>
      )}

      <div className="grid-stats" style={{ marginTop: '0', marginBottom: '24px' }}>
        <motion.div layout className="glass-card" style={{ padding: '20px' }}>
          <div style={{ fontSize: '10px', textTransform: 'uppercase', opacity: 0.5, fontWeight: '800', letterSpacing: '0.1em', marginBottom: '10px' }}>Median Drain</div>
          <div style={{ fontSize: '24px', fontWeight: '900' }}>{batteryInsights?.medianDrain ? `${batteryInsights.medianDrain.toFixed(1)}%` : '—'}</div>
          <div style={{ fontSize: '10px', opacity: 0.4, marginTop: '6px' }}>Typical battery drop</div>
        </motion.div>

        <motion.div layout className="glass-card" style={{ padding: '20px' }}>
          <div style={{ fontSize: '10px', textTransform: 'uppercase', opacity: 0.5, fontWeight: '800', letterSpacing: '0.1em', marginBottom: '10px' }}>Longest Run</div>
          <div style={{ fontSize: '24px', fontWeight: '900' }}>{batteryInsights?.longestRunSessions || '—'}</div>
          <div style={{ fontSize: '10px', opacity: 0.4, marginTop: '6px' }}>Sessions on one charge</div>
        </motion.div>
      </div>

      {(telemetry.extended?.heaterRuntimeMinutes || 0) >= 600 && (
        <motion.div 
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
          className="glass-card" 
          style={{ padding: '20px', background: 'rgba(251, 146, 60, 0.1)', border: '1px solid rgba(251, 146, 60, 0.3)' }}
        >
          <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
            <div style={{ padding: '10px', borderRadius: '12px', background: 'rgba(251, 146, 60, 0.2)' }}>
              <ShieldCheck size={24} color="#fb923c" />
            </div>
            <div>
              <div style={{ fontSize: '14px', fontWeight: '800', color: '#fb923c' }}>Clean Recommended</div>
              <div style={{ fontSize: '10px', opacity: 0.5, marginTop: '2px' }}>Heater usage has exceeded 10 hours. For peak thermal performance, a deep cleaning is suggested.</div>
            </div>
          </div>
        </motion.div>
      )}

      {/* Charging Options */}
      <div className="settings-section-label" style={{ padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>Charge Protection</div>
      <motion.div layout className="glass-card future-glass" style={{ padding: '0', marginBottom: '24px' }}>
        <div className="setting-toggle-item" onClick={() => sendCommand('setChargeCurrentOptimization', !status?.chargeCurrentOptimization)} style={{ padding: '20px 24px', borderBottom: '1px solid rgba(255,255,255,0.05)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <ShieldCheck size={20} color="var(--accent-cyan)" style={{ opacity: status?.chargeCurrentOptimization ? 1 : 0.4 }} />
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: '15px', fontWeight: '700', opacity: status?.chargeCurrentOptimization ? 1 : 0.6 }}>Charge Optimization</span>
              <span style={{ fontSize: '10px', opacity: 0.4, fontWeight: '600' }}>Reduce battery wear while charging</span>
            </div>
          </div>
          <div className={`tg-switch ${status?.chargeCurrentOptimization ? 'active' : ''}`} style={{ width: '44px', height: '24px', borderRadius: '12px', background: status?.chargeCurrentOptimization ? 'var(--accent-cyan)' : 'rgba(255,255,255,0.1)', position: 'relative', transition: 'all 0.3s' }}>
            <motion.div animate={{ x: status?.chargeCurrentOptimization ? 22 : 2 }} style={{ position: 'absolute', top: 2, width: '20px', height: '20px', borderRadius: '10px', background: '#fff' }} />
          </div>
        </div>
        
        <div className="setting-toggle-item" onClick={() => sendCommand('setChargeVoltageLimit', !status?.chargeVoltageLimit)} style={{ padding: '20px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <Zap size={20} color="var(--accent-cyan)" style={{ opacity: status?.chargeVoltageLimit ? 1 : 0.4 }} />
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: '15px', fontWeight: '700', opacity: status?.chargeVoltageLimit ? 1 : 0.6 }}>80% Charge Limit</span>
              <span style={{ fontSize: '10px', opacity: 0.4, fontWeight: '600' }}>Stop charging early to preserve battery health</span>
            </div>
          </div>
          <div className={`tg-switch ${status?.chargeVoltageLimit ? 'active' : ''}`} style={{ width: '44px', height: '24px', borderRadius: '12px', background: status?.chargeVoltageLimit ? 'var(--accent-cyan)' : 'rgba(255,255,255,0.1)', position: 'relative', transition: 'all 0.3s' }}>
            <motion.div animate={{ x: status?.chargeVoltageLimit ? 22 : 2 }} style={{ position: 'absolute', top: 2, width: '20px', height: '20px', borderRadius: '10px', background: '#fff' }} />
          </div>
        </div>
      </motion.div>

      <div style={{ padding: '12px', textAlign: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', opacity: 0.3 }}>
          <Info size={12} />
          <span style={{ fontSize: '10px' }}>Cleaning is { (telemetry.extended?.heaterRuntimeMinutes || 0) < 600 ? 'not needed yet' : 'recommended now' }</span>
        </div>
      </div>
    </motion.div>
  )
}

export default BatteryView
