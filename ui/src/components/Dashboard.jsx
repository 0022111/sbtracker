import React from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Activity, Bluetooth, Sparkles } from 'lucide-react'
import { useVaporizerData } from '../hooks/useVaporizerData'
import { useSessionIntelligence } from '../hooks/useSessionIntelligence'
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
  const intelligence = useSessionIntelligence('30d')

  return (
    <div className="dashboard-container">
      <BubbleMatrix />
      <StatusPill state={telemetry.connectionState} deviceType={telemetry.status?.deviceType} />
      <MissionRibbon telemetry={telemetry} intelligence={intelligence} />

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
      <div className="glass-card status-chip">
        <motion.span
          className="status-dot"
          animate={{ opacity: [0.55, 1, 0.55], scale: [1, 1.15, 1] }}
          transition={{ duration: 1.8, repeat: Infinity }}
          style={{ background: color, boxShadow: `0 0 14px ${color}` }}
        />
        <span className="status-chip-label">{label}</span>
      </div>
    </div>
  )
}

const MissionRibbon = ({ telemetry, intelligence }) => {
  const isConnected = telemetry.connectionState === 'Connected'

  return (
    <div className="mission-ribbon">
      <div className="glass-card mission-ribbon-card">
        <div className="mission-ribbon-item">
          <Bluetooth size={14} />
          <span>{isConnected ? 'Bridge live' : 'Bridge idle'}</span>
        </div>
        <div className="mission-ribbon-item">
          <Activity size={14} />
          <span>{intelligence.summary.sessionCount} sessions in focus</span>
        </div>
        <div className="mission-ribbon-item">
          <Sparkles size={14} />
          <span>{intelligence.recommendations[0]?.title || 'Waiting for enough history'}</span>
        </div>
      </div>
    </div>
  )
}

export default Dashboard
