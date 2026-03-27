import React from 'react'
import { motion } from 'framer-motion'
import { Info, Cpu, Smartphone, Zap, Shield, Thermometer, Bell, Activity, Scale, Package, RefreshCcw, Database, Upload, Download, Trash2 } from 'lucide-react'
import useStore from '../store/useStore'

const Slider = ({ label, icon, value, min, max, unit = '', onChange }) => (
  <div className="setting-control-item" style={{ padding: '20px 24px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
    <div className="setting-label-row" style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{ color: 'var(--accent-cyan)', opacity: 0.8 }}>{icon}</div>
        <span style={{ fontSize: '15px', fontWeight: '600', letterSpacing: '-0.01em' }}>{label}</span>
      </div>
      <motion.span 
        key={value}
        initial={{ scale: 1.2, color: 'var(--accent-cyan)' }}
        animate={{ scale: 1, color: '#fff' }}
        className="setting-value-badge" 
        style={{ background: 'rgba(255,255,255,0.05)', padding: '4px 12px', borderRadius: '8px', fontSize: '13px', fontWeight: '800', fontFamily: 'monospace' }}
      >
        {value}{unit}
      </motion.span>
    </div>
    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
      <input 
        type="range" 
        min={min} max={max} 
        step={label.includes("Weight") ? 0.01 : 1}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        className="settings-slider"
        style={{ flex: 1, height: '6px', borderRadius: '3px', background: 'rgba(255,255,255,0.1)', outline: 'none', appearance: 'none' }}
      />
    </div>
  </div>
)

const SettingToggle = ({ icon, label, active, onClick }) => (
  <motion.div 
    whileTap={{ background: 'rgba(255,255,255,0.03)' }}
    className="setting-toggle-item" 
    onClick={onClick}
    style={{ padding: '20px 24px', borderBottom: '1px solid rgba(255,255,255,0.05)', cursor: 'pointer' }}
  >
    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
      <div style={{ 
        color: active ? 'var(--accent-orange)' : '#fff', 
        opacity: active ? 1 : 0.3, 
        transition: 'all 0.4s cubic-bezier(0.23, 1, 0.32, 1)',
        filter: active ? 'drop-shadow(0 0 8px var(--accent-orange))' : 'none'
      }}>
        {icon}
      </div>
      <span style={{ 
        fontSize: '15px', 
        fontWeight: active ? '700' : '500', 
        opacity: active ? 1 : 0.6,
        transition: 'all 0.3s'
      }}>
        {label}
      </span>
    </div>
    <div className={`tg-switch ${active ? 'active' : ''}`} style={{ width: '44px', height: '24px', borderRadius: '12px', background: active ? 'var(--accent-orange)' : 'rgba(255,255,255,0.1)', position: 'relative', transition: 'all 0.3s' }}>
      <motion.div 
        animate={{ x: active ? 22 : 2 }}
        className="tg-handle" 
        style={{ position: 'absolute', top: 2, width: '20px', height: '20px', borderRadius: '10px', background: '#fff', boxShadow: '0 2px 4px rgba(0,0,0,0.2)' }}
      />
    </div>
  </motion.div>
)

const SettingsView = () => {
  const { telemetry, sendCommand, devMode, setDevMode } = useStore()
  const { status, displaySettings, firmwareVersion, prefs } = telemetry

  const [taps, setTaps] = React.useState(0)
  const [lastTap, setLastTap] = React.useState(0)

  const handleVersionClick = () => {
    const now = Date.now()
    if (now - lastTap < 1000) {
      const nextTaps = taps + 1
      if (nextTaps >= 7) {
        setDevMode(!devMode)
        setTaps(0)
      } else {
        setTaps(nextTaps)
      }
    } else {
      setTaps(1)
    }
    setLastTap(now)
  }

  return (
    <motion.div 
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      className="view-container"
      style={{ paddingBottom: '240px' }}
    >
      <header className="page-header">
        <p className="view-title">Settings</p>
        <h1 className="page-title">Preferences, device behavior, and data</h1>
        <p className="page-subtitle">
          Settings should be boring in the good way: clear defaults, safe data tools, and the device controls you only touch occasionally.
        </p>
      </header>
      
      <div className="settings-section-label" style={{ padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>Dosage Tracking</div>
      <motion.div layout className="glass-card future-glass" style={{ padding: '0', marginBottom: '24px' }}>
        <Slider
          label="Capsule Weight"
          icon={<Scale size={18} />}
          value={prefs?.capsuleWeightGrams || 0.10}
          min={0.05} max={0.25}
          unit="g"
          onChange={(v) => sendCommand('setCapsuleWeight', v)}
        />
        <SettingToggle
          label="Default to Capsule"
          icon={<Package size={20} />}
          active={prefs?.defaultIsCapsule}
          onClick={() => sendCommand('setDefaultIsCapsule', !prefs?.defaultIsCapsule)}
        />
      </motion.div>

      <div className="settings-section-label" style={{ padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>Display & Haptics</div>
      <motion.div layout className="glass-card future-glass" style={{ padding: '0', marginBottom: '24px' }}>
        <Slider 
          label="LED Brightness"
          icon={<Smartphone size={18} />}
          value={displaySettings?.brightness || 5}
          min={1} max={9}
          onChange={(v) => sendCommand('setBrightness', v)}
        />

        <SettingToggle 
          label="Vibration Alerts"
          icon={<Bell size={20} />} 
          active={status?.vibrationEnabled}
          onClick={() => sendCommand('setVibration', !status?.vibrationEnabled)}
        />
        
        <Slider 
          label="Vibration Intensity"
          icon={<Activity size={18} />}
          value={displaySettings?.vibrationLevel || 5}
          min={1} max={9}
          onChange={(v) => sendCommand('setVibrationLevel', v)}
        />
      </motion.div>

      <div className="settings-section-label" style={{ padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>Battery Preservation</div>
      <motion.div layout className="glass-card future-glass" style={{ padding: '0', marginBottom: '24px' }}>
        <SettingToggle 
          icon={<Zap size={20} />} 
          label="Charge Optimization" 
          active={status?.chargeCurrentOptimization} 
          onClick={() => sendCommand('setChargeCurrentOptimization', !status?.chargeCurrentOptimization)} 
        />
        <SettingToggle 
          icon={<Shield size={20} />} 
          label="80% Charge Limit" 
          active={status?.chargeVoltageLimit} 
          onClick={() => sendCommand('setChargeVoltageLimit', !status?.chargeVoltageLimit)} 
        />
      </motion.div>

      <div className="settings-section-label" style={{ padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>Data</div>
      <motion.div layout className="glass-card future-glass" style={{ padding: '0', marginBottom: '24px', overflow: 'hidden' }}>
        {[
          {
            label: 'Backup Data',
            detail: 'Export your local app data',
            icon: <Download size={18} />,
            onClick: () => sendCommand('backupDatabase'),
            danger: false
          },
          {
            label: 'Restore Backup',
            detail: 'Import a backup file',
            icon: <Upload size={18} />,
            onClick: () => sendCommand('restoreDatabase', 'user_triggered'),
            danger: false
          },
          {
            label: 'Clear All History',
            detail: 'Delete stored sessions and history',
            icon: <Trash2 size={18} />,
            onClick: () => {
              if (window.confirm('Delete all sessions and history? This cannot be undone.')) {
                sendCommand('clearHistory')
              }
            },
            danger: true
          }
        ].map((item, index) => (
          <motion.div
            key={item.label}
            whileTap={{ background: 'rgba(255,255,255,0.03)' }}
            onClick={item.onClick}
            style={{
              padding: '20px 24px',
              borderBottom: index < 2 ? '1px solid rgba(255,255,255,0.05)' : 'none',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              cursor: 'pointer'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ color: item.danger ? '#f43f5e' : 'var(--accent-cyan)', opacity: 0.8 }}>
                {item.icon}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span style={{ fontSize: '15px', fontWeight: '600', color: item.danger ? '#f43f5e' : '#fff' }}>{item.label}</span>
                <span style={{ fontSize: '10px', opacity: 0.4, fontWeight: '600' }}>{item.detail}</span>
              </div>
            </div>
            <Database size={16} style={{ opacity: 0.25 }} />
          </motion.div>
        ))}
      </motion.div>

      <div className="settings-section-label" style={{ padding: '0 8px', marginBottom: '12px', fontSize: '11px', fontWeight: '800', opacity: 0.4, letterSpacing: '0.2em' }}>Preferences & Info</div>
      <motion.div layout className="glass-card future-glass" style={{ padding: '0', marginBottom: '24px' }}>
        <SettingToggle 
          icon={<Thermometer size={20} />} 
          label="Use Celsius" 
          active={status?.isCelsius !== false} 
          onClick={() => sendCommand('toggleUnit')} 
        />
        <SettingToggle 
          icon={<Cpu size={20} />} 
          label="Keep Bluetooth On" 
          active={status?.permanentBluetooth} 
          onClick={() => sendCommand('togglePermanentBle', !status?.permanentBluetooth)} 
        />
        <motion.div 
          whileTap={{ background: 'rgba(255,255,255,0.05)' }}
          className="setting-info-item" 
          onClick={handleVersionClick}
          style={{ padding: '20px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <Info size={20} style={{ opacity: 0.4, color: 'var(--accent-cyan)' }} />
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: '15px', fontWeight: '600' }}>Build Version</span>
              <span style={{ fontSize: '10px', opacity: 0.3, letterSpacing: '0.1em', fontWeight: '800' }}>SBTracker app build</span>
            </div>
          </div>
          <span style={{ fontSize: '12px', opacity: 0.5, fontWeight: '800', fontFamily: 'monospace' }}>{firmwareVersion || 'v1.0.0'}</span>
        </motion.div>
      </motion.div>

      {devMode && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} style={{ width: '100%' }}>
          <div className="settings-section-label" style={{ color: '#fb923c' }}>Developer Tools</div>
          <div className="glass-card settings-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <button className="btn-secondary" onClick={() => sendCommand('rebuildHistory')} style={{ justifyContent: 'center', opacity: 0.8 }}>
              Rebuild History from Data
            </button>
            <button className="btn-secondary" onClick={() => sendCommand('injectTestDevice')} style={{ justifyContent: 'center', opacity: 0.8 }}>
              Add Test Device
            </button>
            <button className="btn-secondary" onClick={() => sendCommand('removeTestDevice')} style={{ justifyContent: 'center', opacity: 0.8 }}>
              Remove Test Devices
            </button>
            <button 
              className="btn-secondary" 
              onClick={() => {
                window.location.replace(window.location.origin + window.location.pathname + "?v=" + Date.now())
              }} 
              style={{ justifyContent: 'center', opacity: 0.8, color: '#f43f5e', border: '1px solid rgba(244, 63, 94, 0.2)' }}
            >
              <RefreshCcw size={14} style={{ marginRight: '8px' }} />
              Reload App
            </button>
          </div>
        </motion.div>
      )}
    </motion.div>
  )
}

export default SettingsView
