import { useState } from 'react'
import './App.css'
import ControlView  from './components/ControlView'
import SessionsView from './components/SessionsView'
import SettingsView from './components/SettingsView'
import { useVaporizerData } from './hooks/useVaporizerData'

const TABS = [
  { id: 'control',   label: 'Control',  icon: ControlIcon  },
  { id: 'sessions',  label: 'Sessions', icon: SessionsIcon },
  { id: 'settings',  label: 'Settings', icon: SettingsIcon },
]

export default function App() {
  const [activeTab, setActiveTab] = useState('control')
  useVaporizerData() // WebSocket bridge to native BLE service

  return (
    <div className="app">
      {activeTab === 'control'  && <ControlView  />}
      {activeTab === 'sessions' && <SessionsView />}
      {activeTab === 'settings' && <SettingsView />}

      <nav className="bottom-nav">
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            id={`nav-${id}`}
            className={`nav-tab${activeTab === id ? ' active' : ''}`}
            onClick={() => setActiveTab(id)}
          >
            <Icon size={22} active={activeTab === id} />
            <span className="nav-tab-label">{label}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}

/* ── Nav Icons ── */

function ControlIcon({ size, active }) {
  const c = active ? 'var(--ember)' : 'currentColor'
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke={c} strokeWidth="1.5" />
      <circle cx="12" cy="12" r="4" fill={active ? 'var(--ember)' : 'none'} stroke={c} strokeWidth="1.5" />
      <path d="M12 3v2M12 19v2M3 12h2M19 12h2" stroke={c} strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}

function SessionsIcon({ size, active }) {
  const c = active ? 'var(--ember)' : 'currentColor'
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="3" y="14" width="4" height="7" rx="1" fill={active ? c : 'none'} stroke={c} strokeWidth="1.5" />
      <rect x="10" y="9" width="4" height="12" rx="1" fill={active ? c : 'none'} stroke={c} strokeWidth="1.5" />
      <rect x="17" y="4" width="4" height="17" rx="1" fill={active ? c : 'none'} stroke={c} strokeWidth="1.5" />
    </svg>
  )
}

function SettingsIcon({ size, active }) {
  const c = active ? 'var(--ember)' : 'currentColor'
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="3" stroke={c} strokeWidth="1.5" />
      <path
        d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
        stroke={c} strokeWidth="1.5"
      />
    </svg>
  )
}
