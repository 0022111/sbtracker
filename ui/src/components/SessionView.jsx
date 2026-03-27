import React from 'react'
import { motion } from 'framer-motion'
import { Flame, Play, Clock, Battery } from 'lucide-react'
import useStore from '../store/useStore'

const SessionView = () => {
  const { programs, sendCommand } = useStore()

  return (
    <motion.div 
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="view-container"
      style={{ paddingBottom: '160px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', width: '100%' }}>
        <h2 className="view-title" style={{ margin: 0 }}>Programs</h2>
        <span style={{ fontSize: '10px', opacity: 0.3 }}>{programs.length} available</span>
      </div>

      <div className="program-list">
        {programs.length === 0 && (
          <div className="glass-card" style={{ padding: '60px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
            <div style={{ padding: '16px', borderRadius: '50%', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)' }}>
              <Clock size={32} opacity={0.2} />
            </div>
            <div>
              <span style={{ fontSize: '16px', fontWeight: '800', display: 'block', marginBottom: '4px' }}>Loading Programs</span>
              <span style={{ fontSize: '11px', opacity: 0.3 }}>Connect to your device to load saved programs.</span>
            </div>
          </div>
        )}
        {programs.map((program) => (
          <motion.div 
            key={program.id} 
            className="program-card glass-card"
            whileTap={{ scale: 0.98 }}
            style={{ 
              padding: '24px', 
              background: 'linear-gradient(165deg, rgba(255,255,255,0.03), transparent)',
              position: 'relative',
              overflow: 'hidden'
            }}
          >
            <div className="program-info">
              <span className="program-name" style={{ fontSize: '18px', fontWeight: '900', letterSpacing: '-0.01em' }}>{program.name}</span>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px', marginTop: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Flame size={14} color="#fb923c" />
                  <span style={{ fontSize: '12px', color: '#fb923c', fontWeight: '900' }}>{program.targetTempC}°</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Clock size={14} style={{ opacity: 0.4 }} />
                  <span style={{ fontSize: '12px', opacity: 0.6, fontWeight: '700' }}>{~~(program.estDurationSec / 60)}m {program.estDurationSec % 60}s</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Battery size={14} color="#22d3ee" />
                  <span style={{ fontSize: '12px', color: '#22d3ee', fontWeight: '700' }}>-{program.estDrainPct}%</span>
                </div>
              </div>
            </div>
            <button 
              className="btn-ignite mini"
              onClick={() => sendCommand('setProgram', program.id)}
              style={{ width: '48px', height: '48px', background: 'var(--accent-cyan)', color: '#000' }}
            >
              <Play size={18} fill="currentColor" />
            </button>
          </motion.div>
        ))}
      </div>
    </motion.div>
  )
}

export default SessionView
