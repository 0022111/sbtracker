import React from 'react'
import { motion } from 'framer-motion'
import { LayoutDashboard, Flame, History, Settings, Battery, BarChart3 } from 'lucide-react'
import useStore from '../store/useStore'

const Sidebar = () => {
  const { view, setView } = useStore()

  const items = [
    { id: 'overview', icon: <LayoutDashboard size={20} />, label: 'Home' },
    { id: 'session', icon: <Flame size={20} />, label: 'Programs' },
    { id: 'analysis', icon: <BarChart3 size={20} />, label: 'Insights' },
    { id: 'history', icon: <History size={20} />, label: 'History' },
    { id: 'battery', icon: <Battery size={20} />, label: 'Battery' },
    { id: 'settings', icon: <Settings size={20} />, label: 'Settings' },
  ]

  return (
    <motion.div 
      initial={{ y: 100 }}
      animate={{ y: 0 }}
      className="sidebar"
      style={{
        borderRadius: '24px 24px 0 0',
        background: 'rgba(255, 255, 255, 0.02)',
        borderTop: '1px solid rgba(255, 255, 255, 0.08)',
        boxShadow: '0 -8px 32px rgba(0,0,0,0.4)',
        padding: '0 12px calc(env(safe-area-inset-bottom, 16px) + 8px) 12px',
        height: 'auto',
        minHeight: '80px'
      }}
    >
      {items.map((item) => {
        const isActive = view === item.id
        return (
          <motion.button
            key={item.id}
            className={`sidebar-item ${isActive ? 'active' : ''}`}
            onClick={() => setView(item.id)}
            whileHover={{ scale: 1.1, y: -2 }}
            whileTap={{ scale: 0.9 }}
            style={{
              position: 'relative',
              padding: '12px 0',
              color: isActive ? 'var(--accent-cyan)' : 'rgba(255, 255, 255, 0.4)',
              transition: 'color 0.3s ease'
            }}
          >
            {isActive && (
              <motion.div
                layoutId="nav-glow"
                style={{
                  position: 'absolute',
                  inset: '8px -4px',
                  background: 'rgba(6, 182, 212, 0.1)',
                  borderRadius: '12px',
                  zIndex: -1,
                  filter: 'blur(8px)'
                }}
              />
            )}
            <div style={{ 
              marginBottom: '4px',
              filter: isActive ? 'drop-shadow(0 0 8px var(--accent-cyan))' : 'none'
            }}>
              {item.icon}
            </div>
            <span style={{ 
              fontSize: '9px', 
              fontWeight: '700', 
              letterSpacing: '0.05em',
              opacity: isActive ? 1 : 0.6
            }}>{item.label}</span>
          </motion.button>
        )
      })}
    </motion.div>
  )
}

export default Sidebar
