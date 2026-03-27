import React from 'react'
import { motion } from 'framer-motion'
import useStore from '../store/useStore'

const BubbleMatrix = () => {
  const telemetry = useStore((state) => state.telemetry)
  const heaterMode = telemetry.status?.heaterMode || 0
  const setpointReached = telemetry.status?.setpointReached || false

  const accent = heaterMode === 0
    ? 'rgba(93, 214, 192, 0.08)'
    : setpointReached
      ? 'rgba(126, 224, 129, 0.08)'
      : 'rgba(245, 156, 67, 0.1)'

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: -1,
      overflow: 'hidden',
      background: 'linear-gradient(180deg, #0c1713 0%, #08110d 100%)'
    }}>
      <motion.div
        animate={{ opacity: heaterMode > 0 ? 1 : 0.8 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        style={{
          position: 'absolute',
          inset: 0,
          background: `
            radial-gradient(circle at 18% 12%, ${accent}, transparent 28%),
            radial-gradient(circle at 82% 18%, rgba(255,255,255,0.03), transparent 22%),
            radial-gradient(circle at 50% 100%, rgba(0,0,0,0.28), transparent 40%)
          `
        }}
      />

      <div style={{
        position: 'absolute',
        inset: 0,
        opacity: 0.06,
        backgroundImage: `
          linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px),
          linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px)
        `,
        backgroundSize: '24px 24px'
      }} />

      <div style={{
        position: 'absolute',
        inset: 0,
        opacity: 0.025,
        pointerEvents: 'none',
        backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`
      }} />
    </div>
  )
}

export default BubbleMatrix
