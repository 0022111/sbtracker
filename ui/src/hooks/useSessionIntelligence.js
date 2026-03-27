import { useMemo } from 'react'
import useStore from '../store/useStore'
import { buildSessionIntelligence } from '../lib/sessionIntelligence'

export function useSessionIntelligence(range = '30d') {
  const telemetry = useStore((state) => state.telemetry)
  const sessionHistory = useStore((state) => state.sessionHistory)

  return useMemo(
    () => buildSessionIntelligence({ telemetry, sessionHistory, range }),
    [range, sessionHistory, telemetry]
  )
}
