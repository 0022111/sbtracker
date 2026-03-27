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

  return (
    <div className="dashboard-container">
      <BubbleMatrix />
      <StatusPill state={telemetry.connectionState} deviceType={telemetry.status?.deviceType} />

      <main className="dashboard-main">
        <AnimatePresence mode="wait">
          <motion.div
            key={view}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
            style={{ width: '100%', display: 'flex', justifyContent: 'center', minHeight: 0 }}
          >
            <CurrentView view={view} />
          </motion.div>
        </AnimatePresence>
      </main>

      <Sidebar />
    </div>
  )
}

const CurrentView = ({ view }) => {
  switch (view) {
    case 'overview':
      return <OverviewView />
    case 'session':
      return <SessionView />
    case 'analysis':
      return <AnalysisView />
    case 'history':
      return <HistoryView />
    case 'battery':
      return <BatteryView />
    case 'settings':
      return <SettingsView />
    default:
      return <OverviewView />
  }
}

const StatusPill = ({ state, deviceType }) => {
  const { devMode } = useStore()
  const isConnected = state === 'Connected'
  const isBusy = state === 'Connecting' || state === 'Scanning' || state === 'Reconnecting'

  const color = devMode
    ? 'var(--accent-orange)'
    : isConnected
      ? 'var(--accent-green)'
      : isBusy
        ? 'var(--accent-orange)'
        : 'var(--accent-red)'

  const label = devMode
    ? 'Developer tools'
    : isConnected
      ? (deviceType || 'Connected')
      : isBusy
        ? 'Connecting'
        : 'Offline'

  return (
    <div className="dashboard-status-pill">
      <div
        className="glass-card"
        style={{
          padding: '8px 14px',
          borderRadius: '999px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          background: 'rgba(15, 24, 20, 0.92)',
          borderColor: `${color}33`,
        }}
      >
        <motion.span
          className="status-dot"
          animate={{ opacity: [0.55, 1, 0.55], scale: [1, 1.15, 1] }}
          transition={{ duration: 1.8, repeat: Infinity }}
          style={{ background: color, boxShadow: `0 0 14px ${color}` }}
        />
        <span style={{ fontSize: '11px', fontWeight: 800, letterSpacing: '0.14em', textTransform: 'uppercase' }}>
          {label}
        </span>
      </div>
    </div>
  )
}

export default Dashboard
