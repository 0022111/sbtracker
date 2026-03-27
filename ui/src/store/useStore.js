import { create } from 'zustand'

const WEB_ONBOARDING_KEY = 'sbtracker.web.onboarding.v1'

const readStoredOnboarding = () => {
  if (typeof window === 'undefined') return false
  return window.localStorage.getItem(WEB_ONBOARDING_KEY) === 'true'
}

const useStore = create((set, get) => ({
  view: 'overview',
  setView: (view) => set({ view }),
  devMode: false,
  hasSeenOnboarding: readStoredOnboarding(),
  setDevMode: (val) => set({ devMode: val }),
  setHasSeenOnboarding: (val) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(WEB_ONBOARDING_KEY, val ? 'true' : 'false')
    }
    set({ hasSeenOnboarding: val })
  },
  telemetry: {
    connectionState: 'Disconnected',
    status: null,
    isSynthetic: false,
    deviceLimits: { min: 40, max: 210 }, 
    stats: {
      durationSeconds: 0,
      hitCount: 0,
      isHitActive: false,
      currentHitDurationSec: 0,
      chargeEtaMinutes: null,
      chargeEta80Minutes: null
    }
  },
  programs: [],
  sessionHistory: [],
  tempHistory: [], // Last 60s for real-time ring
  currentSessionSeries: [], // Full session for Strava-style recap
  sessionHits: [], // Hits in the current session for graph markers
  sessionStartBattery: null,
  heatUpTimeSeconds: null,
  socket: null,
  setSocket: (socket) => set({ socket }),
  setTelemetry: (data) => {
    if (data.type === 'programs') {
      set({ programs: data.data })
      return
    }
    if (data.type === 'history') {
      set({ sessionHistory: data.data })
      return
    }
    
    // Ensure this is a telemetry packet before updating telemetry state
    if (!data.connectionState && !data.status) return
    
    // Inject device-specific thermal limits (Normalization engine)
    const deviceType = data.status?.deviceType || 'Unknown'
    const limits = { min: 40, max: 210 } // Default for Veazy/Crafty/Mighty
    if (deviceType === 'Venty' || deviceType === 'Volcano Hybrid') {
      limits.max = 230
    }
    data.deviceLimits = limits

    // Process real-time telemetry and update temperature graph
    const { tempHistory, currentSessionSeries, telemetry, sessionStartBattery, heatUpTimeSeconds } = get()
    const newPoint = data.status ? { t: Date.now(), temp: data.status.currentTempC } : null
    
    // Manage real-time ring history (fixed 60 points)
    const newTempHistory = newPoint ? [...tempHistory.slice(-59), newPoint] : tempHistory
    
    // Manage full session series for Strava-style graphing
    let newSessionSeries = currentSessionSeries
    let newSessionHits = get().sessionHits
    let newStartBattery = sessionStartBattery
    let newHeatup = heatUpTimeSeconds

    if (data.status?.heaterMode > 0) {
      // If just started, clear previous series and snapshot battery
      if (!telemetry.status || telemetry.status.heaterMode === 0) {
        newSessionSeries = []
        newSessionHits = []
        newStartBattery = data.status.batteryLevel
        newHeatup = null
      }
      if (newPoint) newSessionSeries = [...newSessionSeries, newPoint]

      // Capture initial heat-up time when setpoint is first reached
      // Capture initial heat-up time when setpoint is first reached
      if (!newHeatup && data.status.setpointReached && data.stats.durationSeconds > 0) {
        newHeatup = data.stats.durationSeconds
      }

      // Detect hit completion to record for graph markers
      if (telemetry.stats?.isHitActive && !data.stats.isHitActive) {
        // Hit just finished
        const duration = telemetry.stats.currentHitDurationSec
        if (duration > 0) {
           newSessionHits = [...newSessionHits, { 
             endTime: Date.now(), 
             durationSec: duration,
             peakTemp: telemetry.status?.currentTempC 
           }]
        }
      }
    } else {
      newStartBattery = null
      newHeatup = null
      // Do not clear sessionHits here, keep them for the "finished" view if needed
    }

    set({ 
      telemetry: data, 
      tempHistory: newTempHistory, 
      currentSessionSeries: newSessionSeries,
      sessionHits: newSessionHits,
      sessionStartBattery: newStartBattery,
      heatUpTimeSeconds: newHeatup
    })
  },
  sendCommand: (command, value) => {
    const { socket } = get()
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ command, value }))
      // Tactical feedback for every user interaction
      if (command !== 'vibrate') {
        socket.send(JSON.stringify({ command: 'vibrate' }))
      }
    }
  }
}))

export default useStore
