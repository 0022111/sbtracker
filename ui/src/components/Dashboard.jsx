import React from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { useVaporizerData } from '../hooks/useVaporizerData'
import useStore from '../store/useStore'
import Sidebar from './Sidebar'
import OverviewView from './OverviewView'
import SessionView from './SessionView'
import HistoryView from './HistoryView'
import SettingsView from './SettingsView'
import BatteryView from './BatteryView'
import AnalysisView from './AnalysisView'
import BubbleMatrix from './BubbleMatrix'
import '../App.css'

const Dashboard = () => {
  useVaporizerData()
  const { view, telemetry } = useStore()

  const renderView = () => {
    switch (view) {
      case 'overview': return <OverviewView />
      case 'session':  return <SessionView />
      case 'analysis': return <AnalysisView />
      case 'history':  return <HistoryView />
      case 'battery':  return <BatteryView />
      case 'settings': return <SettingsView />
      default: return <OverviewView />
    }
  }

  return (
    <div className="dashboard-container">
      <BubbleMatrix />
      <StatusPill state={telemetry.connectionState} deviceType={telemetry.status?.deviceType} />
      
      <AnimatePresence mode="wait">
        <motion.div 
          key={view} 
          initial={{ opacity: 0, y: 10, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -10, scale: 1.02 }}
          transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
          style={{ 
            width: '100%', 
            flex: 1,
            minHeight: 0,
            display: 'flex', 
            justifyContent: 'center',
            overflow: 'hidden',
            zIndex: 10
          }}
        >
          {renderView()}
        </motion.div>
      </AnimatePresence>
      
      <Sidebar />
    </div>
  )
}

const StatusPill = ({ state, deviceType }) => {
  const isConnected = state === 'Connected'
  const isLinking = state === 'Connecting' || state === 'Scanning' || state === 'Reconnecting'
  const { devMode } = useStore()

  const label = isConnected ? (deviceType || 'SBTracker') : (isLinking ? 'Linking...' : 'Offline')
  const color = isConnected ? 'var(--accent-green)' : (isLinking ? 'var(--accent-orange)' : 'var(--accent-red)')
  
  return (
    <motion.div 
      initial={{ y: -20, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      style={{ position: 'fixed', top: 'calc(var(--safe-top) + 12px)', left: '50%', transform: 'translateX(-50%)', zIndex: 100 }}
    >
      <motion.div 
        className="glass-card" 
        whileHover={{ scale: 1.05 }}
        style={{ 
          padding: '8px 16px', 
          borderRadius: '200px', 
          display: 'flex', 
          alignItems: 'center', 
          gap: '10px', 
          border: `1px solid ${devMode ? 'var(--accent-purple)' : color}44`,
          background: 'rgba(255,255,255,0.03)',
          boxShadow: `0 8px 32px rgba(0,0,0,0.4), inset 0 0 0 1px rgba(255,255,255,0.05)`,
          cursor: 'default'
        }}
      >
        <motion.div 
          animate={{ 
            scale: [1, 1.2, 1],
            opacity: [0.5, 1, 0.5]
          }}
          transition={{ duration: 2, repeat: Infinity }}
          style={{ width: '8px', height: '8px', borderRadius: '50%', background: devMode ? 'var(--accent-purple)' : color, boxShadow: `0 0 12px ${devMode ? 'var(--accent-purple)' : color}` }} 
        />
        <span style={{ fontSize: '10px', fontWeight: '800', textTransform: 'uppercase', letterSpacing: '0.2em', opacity: 0.9, color: devMode ? 'var(--accent-purple)' : '#fff' }}>
          {devMode ? 'Developer Mode' : label}
        </span>
      </motion.div>
    </motion.div>
  )
}

export default Dashboard
