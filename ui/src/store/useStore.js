import { create } from 'zustand'

const useStore = create((set, get) => ({
  // ── Telemetry (live device state) ──
  telemetry: {
    connectionState: 'Disconnected',
    status: null,
    stats: {
      durationSeconds: 0,
      hitCount: 0,
      isHitActive: false,
      currentHitDurationSec: 0,
      chargeEtaMinutes: null,
      chargeEta80Minutes: null,
    },
    deviceLimits: { min: 40, max: 210 },
    deviceInfo: null,
    displaySettings: null,
    firmwareVersion: null,
  },

  // ── Session tracking (current session) ──
  currentSessionSeries: [], // {t, temp} points since heater on
  sessionHits: [],          // completed hits: {endTime, durationSec, peakTemp}
  heatUpTimeSeconds: null,

  // ── History (from native DB) ──
  sessionHistory: [],
  chargeHistory: [],
  programs: [],

  // ── WebSocket / BLE transport ──
  socket: null,
  setSocket: (socket) => set({ socket }),

  // ── History setters (used by web PWA IndexedDB loader) ──
  setSessionHistory: (rows) => set({ sessionHistory: rows }),
  setChargeHistory:  (rows) => set({ chargeHistory: rows }),


  // ── Telemetry update handler ──
  setTelemetry: (data) => {
    // Typed data packets from native
    if (data.type === 'programs') { set({ programs: data.data ?? [] }); return }
    if (data.type === 'history')  { set({ sessionHistory: data.data ?? [] }); return }
    if (data.type === 'charges')  { set({ chargeHistory: data.data ?? [] }); return }

    if (!data.connectionState && !data.status) return

    // Device-specific temp limits
    const deviceType = data.status?.deviceType || ''
    data.deviceLimits = (deviceType === 'Venty' || deviceType === 'Volcano Hybrid')
      ? { min: 40, max: 230 }
      : { min: 40, max: 210 }

    const {
      currentSessionSeries,
      telemetry,
      heatUpTimeSeconds,
    } = get()

    const newPoint = data.status ? { t: Date.now(), temp: data.status.currentTempC } : null
    const isNowHeating = (data.status?.heaterMode ?? 0) > 0
    const wasHeating   = (telemetry.status?.heaterMode ?? 0) > 0

    let newSessionSeries = currentSessionSeries
    let newSessionHits   = get().sessionHits
    let newHeatup        = heatUpTimeSeconds

    if (isNowHeating) {
      // Session start: clear series
      if (!wasHeating) {
        newSessionSeries = []
        newSessionHits   = []
        newHeatup        = null
      }
      if (newPoint) newSessionSeries = [...newSessionSeries, newPoint]

      // Capture heat-up time
      if (!newHeatup && data.status.setpointReached && (data.stats?.durationSeconds ?? 0) > 0) {
        newHeatup = data.stats.durationSeconds
      }

      // Detect hit completion
      if (telemetry.stats?.isHitActive && !data.stats?.isHitActive) {
        const dur = telemetry.stats.currentHitDurationSec ?? 0
        if (dur > 0) {
          newSessionHits = [...newSessionHits, {
            endTime:     Date.now(),
            durationSec: dur,
            peakTemp:    telemetry.status?.currentTempC,
          }]
        }
      }
    } else if (wasHeating) {
      // Session ended — preserve hits for Trace sheet, clear series
      newHeatup = null
    }

    set({
      telemetry: {
        // Preserve sticky fields that may not be in every packet
        ...get().telemetry,
        // Always update from packet
        connectionState: data.connectionState ?? get().telemetry.connectionState,
        status:          data.status          ?? get().telemetry.status,
        stats:           data.stats           ?? get().telemetry.stats,
        deviceLimits:    data.deviceLimits    ?? get().telemetry.deviceLimits,
        // Sticky — only update when present
        ...(data.displaySettings  && { displaySettings:  data.displaySettings }),
        ...(data.deviceInfo       && { deviceInfo:       data.deviceInfo }),
        ...(data.firmwareVersion  && { firmwareVersion:  data.firmwareVersion }),
        ...(data.extended         && { extended:         data.extended }),
      },
      currentSessionSeries: newSessionSeries,
      sessionHits: newSessionHits,
      heatUpTimeSeconds: newHeatup,
    })
  },

  // ── Commands ──
  sendCommand: (command, value) => {
    const { socket } = get()
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ command, value }))
    }
  },
}))

export default useStore
