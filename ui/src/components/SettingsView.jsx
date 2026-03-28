import useStore from '../store/useStore'

export default function SettingsView() {
  const { telemetry, sendCommand } = useStore()
  const { status, deviceInfo, displaySettings, firmwareVersion } = telemetry

  const isConnected = telemetry.connectionState === 'Connected'
  const isCelsius   = status?.isCelsius ?? true

  // Display settings (from device, fallback to defaults for UI render)
  const brightness   = displaySettings?.brightness   ?? null
  const vibLvl       = displaySettings?.vibrationLevel ?? null
  const boostTimeout = displaySettings?.boostTimeout  ?? null

  // Status booleans
  const voltageLimit  = status?.chargeVoltageLimit       ?? false
  const currentOpt    = status?.chargeCurrentOptimization ?? false
  const permaBle      = status?.permanentBluetooth       ?? false
  const boostViz      = status?.boostVisualization       ?? false
  const autoShutSec   = status?.autoShutdownSeconds      ?? 120
  const vibEnabled    = status?.vibrationEnabled         ?? true

  return (
    <div className="view">
      <div className="page-header">
        <div className="page-title">Settings</div>
      </div>

      {/* ── App preferences (always shown) ── */}
      <div className="card" style={{ marginBottom: 12 }}>
        <div className="section-title">App</div>
        <div className="toggle-row">
          <span>Temperature unit</span>
          <div style={{ display: 'flex', gap: 6 }}>
            {['°C', '°F'].map((u, i) => {
              const isSel = i === 0 ? isCelsius : !isCelsius
              return (
                <button key={u} onClick={() => { if (!isSel) sendCommand('toggleUnit') }}
                  style={{
                    padding: '5px 14px', borderRadius: 8,
                    border: `1px solid ${isSel ? 'var(--ember)' : 'var(--border)'}`,
                    background: isSel ? 'var(--ember)' : 'var(--surface-raised)',
                    color: isSel ? '#fff' : 'var(--text-dim)',
                    fontFamily: 'inherit', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                    transition: 'all 0.15s',
                  }}
                >{u}</button>
              )
            })}
          </div>
        </div>
      </div>

      {/* ── Device info ── */}
      {(deviceInfo || isConnected) && (
        <div className="card" style={{ marginBottom: 12 }}>
          <div className="section-title">Device</div>
          <div className="info-row">
            <span className="info-row-label">Model</span>
            <span className="info-row-value">{status?.deviceType || deviceInfo?.deviceType || '—'}</span>
          </div>
          {deviceInfo?.serialNumber && (
            <div className="info-row">
              <span className="info-row-label">Serial</span>
              <span className="info-row-value" style={{ fontFamily: 'monospace', fontSize: 12 }}>
                {deviceInfo.serialNumber}
              </span>
            </div>
          )}
          {firmwareVersion && (
            <div className="info-row">
              <span className="info-row-label">Firmware</span>
              <span className="info-row-value">{firmwareVersion}</span>
            </div>
          )}
          {status && (
            <div className="info-row">
              <span className="info-row-label">Battery</span>
              <span className="info-row-value">{status.batteryLevel}%{status.isCharging ? ' ⚡' : ''}</span>
            </div>
          )}
        </div>
      )}

      {/* ── Device controls (only when connected) ── */}
      {isConnected && status && (
        <>
          {/* Display */}
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="section-title">Display</div>

            {brightness !== null && (
              <div className="adj-row">
                <span className="adj-label">Brightness</span>
                <div className="adj-controls">
                  <button className="adj-btn"
                    onClick={() => sendCommand('setBrightness', Math.max(1, brightness - 1))}>−</button>
                  <span className="adj-value" style={{ fontSize: 16 }}>{brightness}</span>
                  <button className="adj-btn"
                    onClick={() => sendCommand('setBrightness', Math.min(9, brightness + 1))}>+</button>
                </div>
              </div>
            )}

            <div className="adj-row">
              <span className="adj-label">Auto-shutdown</span>
              <div className="adj-controls">
                <button className="adj-btn"
                  onClick={() => sendCommand('setAutoShutdown', Math.max(30, autoShutSec - 30))}>−</button>
                <span className="adj-value" style={{ fontSize: 16 }}>{Math.round(autoShutSec / 60)}m</span>
                <button className="adj-btn"
                  onClick={() => sendCommand('setAutoShutdown', Math.min(900, autoShutSec + 30))}>+</button>
              </div>
            </div>

            {boostTimeout !== null && (
              <div className="adj-row">
                <span className="adj-label">Boost timeout</span>
                <div className="adj-controls">
                  <button className="adj-btn"
                    onClick={() => sendCommand('setBoostTimeout', Math.max(0, boostTimeout - 1))}>−</button>
                  <span className="adj-value" style={{ fontSize: 16 }}>{boostTimeout}s</span>
                  <button className="adj-btn"
                    onClick={() => sendCommand('setBoostTimeout', Math.min(255, boostTimeout + 1))}>+</button>
                </div>
              </div>
            )}
          </div>

          {/* Haptics */}
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="section-title">Haptics</div>

            <div className="toggle-row">
              <span>Vibration</span>
              <label className="toggle">
                <input type="checkbox" checked={vibEnabled}
                  onChange={() => sendCommand('setVibrationLevel', vibEnabled ? 0 : 1)} />
                <div className="toggle-track"><div className="toggle-thumb" /></div>
              </label>
            </div>

            {vibEnabled && vibLvl !== null && (
              <div className="adj-row">
                <span className="adj-label">Vibration level</span>
                <div className="adj-controls">
                  <button className="adj-btn"
                    onClick={() => sendCommand('setVibrationLevel', Math.max(0, vibLvl - 10))}>−</button>
                  <span className="adj-value" style={{ fontSize: 16 }}>{vibLvl}</span>
                  <button className="adj-btn"
                    onClick={() => sendCommand('setVibrationLevel', Math.min(100, vibLvl + 10))}>+</button>
                </div>
              </div>
            )}
          </div>

          {/* Bluetooth */}
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="section-title">Bluetooth</div>
            <div className="toggle-row">
              <div>
                <div style={{ fontSize: 14 }}>Permanent Bluetooth</div>
                <div className="body-sm">Stays connected when heater is off</div>
              </div>
              <label className="toggle">
                <input type="checkbox" checked={permaBle}
                  onChange={() => sendCommand('togglePermanentBle', !permaBle)} />
                <div className="toggle-track"><div className="toggle-thumb" /></div>
              </label>
            </div>
            <div className="toggle-row">
              <div>
                <div style={{ fontSize: 14 }}>Boost visualization</div>
                <div className="body-sm">Show boost on device display</div>
              </div>
              <label className="toggle">
                <input type="checkbox" checked={boostViz}
                  onChange={() => sendCommand('setVibration', !boostViz)} />
                <div className="toggle-track"><div className="toggle-thumb" /></div>
              </label>
            </div>
          </div>

          {/* Charging */}
          <div className="card" style={{ marginBottom: 12 }}>
            <div className="section-title">Charging</div>
            <div className="toggle-row">
              <div>
                <div style={{ fontSize: 14 }}>Limit to 80%</div>
                <div className="body-sm">Voltage limit for battery longevity</div>
              </div>
              <label className="toggle">
                <input type="checkbox" checked={voltageLimit}
                  onChange={() => sendCommand('setChargeVoltageLimit', !voltageLimit)} />
                <div className="toggle-track"><div className="toggle-thumb" /></div>
              </label>
            </div>
            <div className="toggle-row">
              <div>
                <div style={{ fontSize: 14 }}>Optimize charge current</div>
                <div className="body-sm">Adaptive charging speed</div>
              </div>
              <label className="toggle">
                <input type="checkbox" checked={currentOpt}
                  onChange={() => sendCommand('setChargeCurrentOptimization', !currentOpt)} />
                <div className="toggle-track"><div className="toggle-thumb" /></div>
              </label>
            </div>
          </div>
        </>
      )}

      {/* Not connected nudge */}
      {!isConnected && (
        <div className="card-sm" style={{ marginBottom: 12, textAlign: 'center' }}>
          <div className="body-sm">Connect a device to access device settings.</div>
        </div>
      )}

      {/* Data */}
      <div className="card" style={{ marginBottom: 12 }}>
        <div className="section-title">Data</div>
        <button className="btn btn-ghost" style={{ width: '100%', marginBottom: 8 }}
          onClick={() => sendCommand('backupDatabase')}>
          Export backup
        </button>
        <button className="btn btn-ghost" style={{ width: '100%' }}
          onClick={() => sendCommand('restoreDatabase')}>
          Restore backup
        </button>
      </div>

      <div style={{ height: 16 }} />
    </div>
  )
}
