const DAY_MS = 24 * 60 * 60 * 1000

const RANGE_TO_MS = {
  '7d': 7 * DAY_MS,
  '30d': 30 * DAY_MS,
  '90d': 90 * DAY_MS,
  All: Number.POSITIVE_INFINITY,
}

export const HISTORY_RANGES = ['7d', '30d', '90d', 'All']

export function filterSessionsByRange(sessionHistory, range) {
  const maxAgeMs = RANGE_TO_MS[range] ?? RANGE_TO_MS['30d']
  if (!Number.isFinite(maxAgeMs)) return [...sessionHistory]
  const threshold = Date.now() - maxAgeMs
  return sessionHistory.filter((session) => (session.startTimeMs || 0) >= threshold)
}

export function buildSessionIntelligence({ telemetry, sessionHistory, range = '30d' }) {
  const sessions = filterSessionsByRange(sessionHistory, range)
    .slice()
    .sort((a, b) => (b.startTimeMs || 0) - (a.startTimeMs || 0))
    .map(enrichSession)

  const recent = sessions.slice(0, 12)
  const last10 = sessions.slice(0, 10)
  const prev10 = sessions.slice(10, 20)
  const tempBands = buildTempBands(sessions)
  const warmups = sessions.filter((session) => session.storyKey === 'warmup')
  const productive = sessions.filter((session) => session.hitCount > 0)
  const headline = buildHeadline(telemetry, sessions)
  const today = buildTodaySnapshot(sessionHistory)
  const momentum = compareWindows(last10, prev10)
  const pace = buildPaceSignal(sessions)
  const battery = buildBatterySignal(telemetry, sessions, last10)
  const recommendations = buildRecommendations({
    telemetry,
    sessions,
    warmups,
    productive,
    tempBands,
    battery,
    momentum,
    pace,
  })

  return {
    headline,
    today,
    recent,
    sessions,
    summary: {
      sessionCount: sessions.length,
      productiveShare: sessions.length ? Math.round((productive.length / sessions.length) * 100) : 0,
      avgIntensity: average(sessions.map((session) => session.intensityScore)),
      avgEfficiency: average(sessions.map((session) => session.efficiencyScore)),
      avgHits: average(sessions.map((session) => session.hitCount)),
      avgDurationMin: average(sessions.map((session) => session.durationMin)),
    },
    momentum,
    pace,
    battery,
    tempBands,
    recommendations,
    alerts: recommendations.filter((item) => item.tone !== 'good').slice(0, 3),
  }
}

function enrichSession(session) {
  const durationMin = Math.max(0, (session.durationMs || 0) / 60000)
  const battery = Math.max(0, session.batteryConsumed || 0)
  const hits = Math.max(0, session.hitCount || 0)
  const hitDensity = durationMin > 0 ? hits / durationMin : 0
  const hitsPerDrain = battery > 0 ? hits / battery : hits > 0 ? hits : 0
  const efficiencyScore = clamp((hits * 10) - (battery * 4) + (hitDensity * 24), 0, 100)
  const intensityScore = clamp((durationMin * 5) + (battery * 3) + (hits * 2.5), 0, 100)
  const story = classifyStory({ durationMin, battery, hits, hitDensity })

  return {
    ...session,
    durationMin,
    battery,
    hitDensity,
    hitsPerDrain,
    efficiencyScore: Math.round(efficiencyScore),
    intensityScore: Math.round(intensityScore),
    storyKey: story.key,
    storyLabel: story.label,
    storyDetail: story.detail,
    sessionScore: Math.round((efficiencyScore * 0.6) + (Math.max(0, 100 - Math.abs(intensityScore - 58)) * 0.4)),
  }
}

function classifyStory({ durationMin, battery, hits, hitDensity }) {
  if (hits === 0 && battery >= 2) {
    return {
      key: 'warmup',
      label: 'Warm-up only',
      detail: 'Battery moved, but the log never saw a real extraction pattern.',
    }
  }

  if (hits <= 2 && durationMin <= 4) {
    return {
      key: 'quick',
      label: 'Quick pass',
      detail: 'Short run, low commitment, usually a check-in or light reset.',
    }
  }

  if (hitDensity >= 1.3 && battery <= 10) {
    return {
      key: 'efficient',
      label: 'Dense extraction',
      detail: 'High hit density without chewing through much battery.',
    }
  }

  if (battery >= 14 || durationMin >= 12) {
    return {
      key: 'heavy',
      label: 'Long burner',
      detail: 'A longer session arc with heavier battery and time commitment.',
    }
  }

  if (hits >= 8) {
    return {
      key: 'full',
      label: 'Full cycle',
      detail: 'A complete session with enough hits to count as a committed run.',
    }
  }

  return {
    key: 'steady',
    label: 'Steady cruise',
    detail: 'Balanced session with no obvious waste or overreach.',
  }
}

function buildHeadline(telemetry, sessions) {
  const status = telemetry?.status
  const stats = telemetry?.stats
  const isConnected = telemetry?.connectionState === 'Connected'
  const isHeating = (status?.heaterMode || 0) > 0
  const last = sessions[0]

  if (!isConnected) {
    return {
      eyebrow: 'Offline',
      title: 'Logger ready, waiting for a device.',
      detail: 'Connection is down, but the rebuilt session model is ready as soon as the device comes back.',
      tone: 'warn',
    }
  }

  if (status?.isCharging) {
    return {
      eyebrow: 'Charging',
      title: 'Battery is recovering for the next run.',
      detail: stats?.chargeEtaMinutes
        ? `About ${stats.chargeEtaMinutes} minutes to a full charge at the current rate.`
        : 'Charge ETA is still building from live data.',
      tone: 'good',
    }
  }

  if (isHeating) {
    return {
      eyebrow: 'Live Session',
      title: status?.setpointReached ? 'Session is in the pocket.' : 'Heating toward target.',
      detail: status?.setpointReached
        ? `${stats?.hitCount || 0} hits detected so far across ${formatMinutes(stats?.durationSeconds || 0)}.`
        : stats?.estHeatUpMs
          ? `Roughly ${Math.round(stats.estHeatUpMs / 1000)}s to setpoint based on prior sessions.`
          : 'Watching the live curve for the first setpoint.',
      tone: 'accent',
    }
  }

  return {
    eyebrow: 'Ready',
    title: last
      ? `Last session was a ${last.storyLabel.toLowerCase()}.`
      : 'No session history yet.',
    detail: last
      ? `${Math.round(last.durationMin)} min, ${last.hitCount} hits, ${last.battery}% battery used.`
      : 'The logging backbone is live. Finish a session and this surface will start learning your pattern.',
    tone: 'neutral',
  }
}

function buildTodaySnapshot(sessionHistory) {
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  const sessions = sessionHistory.filter((session) => (session.startTimeMs || 0) >= now.getTime()).map(enrichSession)

  return {
    sessionCount: sessions.length,
    hitCount: sessions.reduce((sum, session) => sum + session.hitCount, 0),
    durationMin: sessions.reduce((sum, session) => sum + session.durationMin, 0),
    battery: sessions.reduce((sum, session) => sum + session.battery, 0),
  }
}

function compareWindows(current, previous) {
  const currentAvgHits = average(current.map((session) => session.hitCount))
  const previousAvgHits = average(previous.map((session) => session.hitCount))
  const currentAvgDrain = average(current.map((session) => session.battery))
  const previousAvgDrain = average(previous.map((session) => session.battery))
  const currentAvgDuration = average(current.map((session) => session.durationMin))
  const previousAvgDuration = average(previous.map((session) => session.durationMin))

  return {
    currentCount: current.length,
    previousCount: previous.length,
    hitDelta: round1(currentAvgHits - previousAvgHits),
    drainDelta: round1(currentAvgDrain - previousAvgDrain),
    durationDelta: round1(currentAvgDuration - previousAvgDuration),
  }
}

function buildPaceSignal(sessions) {
  const activeDays = new Set()
  sessions.forEach((session) => {
    const stamp = new Date(session.startTimeMs || 0)
    activeDays.add(`${stamp.getFullYear()}-${stamp.getMonth()}-${stamp.getDate()}`)
  })

  const dayCounts = new Map()
  sessions.forEach((session) => {
    const stamp = new Date(session.startTimeMs || 0)
    const key = `${stamp.getFullYear()}-${stamp.getMonth()}-${stamp.getDate()}`
    dayCounts.set(key, (dayCounts.get(key) || 0) + 1)
  })

  const cadence = average([...dayCounts.values()])
  const peakDay = Math.max(0, ...dayCounts.values())

  return {
    activeDays: activeDays.size,
    sessionsPerActiveDay: round1(cadence),
    peakDay,
  }
}

function buildBatterySignal(telemetry, sessions, recent) {
  const stats = telemetry?.stats || {}
  const avgDrain = average(recent.map((session) => session.battery))
  const avgDuration = average(recent.map((session) => session.durationMin))

  return {
    sessionsRemaining: stats.sessionsRemaining ?? null,
    sessionsLow: stats.sessionsRemainingLow ?? null,
    sessionsHigh: stats.sessionsRemainingHigh ?? null,
    reliable: Boolean(stats.drainEstimateReliable),
    chargeEtaMinutes: stats.chargeEtaMinutes ?? null,
    avgDrain: round1(avgDrain),
    avgDuration: round1(avgDuration),
    confidenceLabel: stats.drainEstimateReliable ? 'Grounded in recent sessions' : 'Still building confidence',
  }
}

function buildTempBands(sessions) {
  return Object.values(
    sessions.reduce((acc, session) => {
      const bucket = roundToFive(session.peakTempC || session.avgTempC || 0)
      if (!bucket) return acc

      const current = acc[bucket] || {
        bucket,
        count: 0,
        hits: 0,
        drain: 0,
        score: 0,
      }

      current.count += 1
      current.hits += session.hitCount
      current.drain += session.battery
      current.score += session.efficiencyScore
      acc[bucket] = current
      return acc
    }, {})
  )
    .map((band) => ({
      ...band,
      avgHits: round1(band.hits / Math.max(1, band.count)),
      avgDrain: round1(band.drain / Math.max(1, band.count)),
      avgScore: Math.round(band.score / Math.max(1, band.count)),
    }))
    .sort((a, b) => b.avgScore - a.avgScore)
}

function buildRecommendations({ sessions, warmups, tempBands, battery, momentum, pace }) {
  const recommendations = []

  if (!sessions.length) {
    return [{
      tone: 'neutral',
      title: 'Nothing to analyze yet',
      detail: 'Logging and detection are ready. One complete session is enough to start producing useful patterns.',
    }]
  }

  const warmupShare = warmups.length / sessions.length
  if (warmupShare >= 0.2) {
    recommendations.push({
      tone: 'warn',
      title: 'Too many warm-up-only runs',
      detail: `${Math.round(warmupShare * 100)}% of sessions in this window spent battery without a detected hit. That usually means aborted starts, missed inhalations, or a temp band that is not paying back.`,
    })
  }

  const bestBand = tempBands[0]
  if (bestBand && bestBand.count >= 2) {
    recommendations.push({
      tone: 'good',
      title: `Best operating band looks like ${bestBand.bucket}C`,
      detail: `${bestBand.count} sessions here averaged ${bestBand.avgHits} hits on ${bestBand.avgDrain}% battery, which is your strongest return in this window.`,
    })
  }

  if (battery.sessionsRemaining !== null) {
    recommendations.push({
      tone: battery.reliable ? 'accent' : 'neutral',
      title: battery.reliable ? 'Battery runway is trustworthy' : 'Battery runway is still provisional',
      detail: battery.reliable
        ? `Current estimate says about ${battery.sessionsRemaining} sessions remain, with a likely band of ${battery.sessionsLow ?? '—'} to ${battery.sessionsHigh ?? '—'}.`
        : `The app estimates about ${battery.sessionsRemaining} sessions remaining, but the range still needs more stable drain history.`,
    })
  }

  if (momentum.hitDelta <= -1.5) {
    recommendations.push({
      tone: 'warn',
      title: 'Recent sessions are landing lighter',
      detail: `Your last 10 sessions averaged ${Math.abs(momentum.hitDelta).toFixed(1)} fewer hits than the 10 before them. That usually means shorter runs, less effective temperatures, or more interruptions.`,
    })
  } else if (momentum.hitDelta >= 1.5) {
    recommendations.push({
      tone: 'good',
      title: 'Recent sessions are getting denser',
      detail: `Your last 10 sessions are averaging ${momentum.hitDelta.toFixed(1)} more hits than the prior 10, without needing a longer run.`,
    })
  }

  if (pace.peakDay >= 4) {
    recommendations.push({
      tone: 'neutral',
      title: 'High-stack days are part of the pattern',
      detail: `The busiest day in this range hit ${pace.peakDay} sessions. That is useful context for battery expectations and for separating routine use from exceptional days.`,
    })
  }

  return recommendations.slice(0, 4)
}

function average(values) {
  if (!values.length) return 0
  return values.reduce((sum, value) => sum + (Number.isFinite(value) ? value : 0), 0) / values.length
}

function round1(value) {
  return Math.round((value || 0) * 10) / 10
}

function roundToFive(value) {
  if (!value) return 0
  return Math.round(value / 5) * 5
}

function formatMinutes(seconds) {
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return `${minutes}:${`${remainder}`.padStart(2, '0')}`
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}
