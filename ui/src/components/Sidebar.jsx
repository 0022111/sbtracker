import React from 'react'
import { motion } from 'framer-motion'
import { Battery, BarChart3, Flame, History, Home, Settings } from 'lucide-react'
import useStore from '../store/useStore'

const items = [
  { id: 'overview', icon: Home, label: 'Home' },
  { id: 'session', icon: Flame, label: 'Programs' },
  { id: 'history', icon: History, label: 'History' },
  { id: 'analysis', icon: BarChart3, label: 'Insights' },
  { id: 'battery', icon: Battery, label: 'Battery' },
  { id: 'settings', icon: Settings, label: 'Settings' },
]

const Sidebar = () => {
  const { view, setView } = useStore()

  return (
    <nav className="sidebar">
      {items.map((item) => {
        const Icon = item.icon
        const isActive = view === item.id

        return (
          <motion.button
            key={item.id}
            className={`sidebar-item ${isActive ? 'active' : ''}`}
            onClick={() => setView(item.id)}
            whileTap={{ scale: 0.97 }}
          >
            <Icon size={19} />
            <span>{item.label}</span>
          </motion.button>
        )
      })}
    </nav>
  )
}

export default Sidebar
