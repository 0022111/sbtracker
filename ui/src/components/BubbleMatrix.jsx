import React from 'react'
import { motion } from 'framer-motion'
import useStore from '../store/useStore'

const BubbleMatrix = () => {
  const telemetry = useStore((state) => state.telemetry)
  const heaterMode = telemetry.status?.heaterMode || 0
  const setpointReached = telemetry.status?.setpointReached || false

  const glow = heaterMode === 0
    ? 'rgba(113, 215, 192, 0.11)'
    : setpointReached
      ? 'rgba(125, 217, 146, 0.12)'
      : 'rgba(231, 164, 91, 0.14)'

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 0,
        overflow: 'hidden',
        pointerEvents: 'none',
      }}
    >
      <motion.div
        animate={{ opacity: heaterMode > 0 ? 1 : 0.82 }}
        transition={{ duration: 0.45, ease: 'easeOut' }}
        style={{
          position: 'absolute',
          inset: 0,
          background: `
            radial-gradient(circle at 14% 10%, ${glow}, transparent 28%),
            radial-gradient(circle at 85% 12%, rgba(255,255,255,0.035), transparent 22%),
            radial-gradient(circle at 50% 120%, rgba(0,0,0,0.28), transparent 44%)
          `,
        }}
      />

      <div
        style={{
          position: 'absolute',
          inset: 0,
          opacity: 0.05,
          backgroundImage: `
            linear-gradient(rgba(255,255,255,0.06) 1px, transparent 1px),
            linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px)
          `,
          backgroundSize: '26px 26px',
          maskImage: 'linear-gradient(180deg, rgba(0,0,0,0.75), transparent 90%)',
        }}
      />
    </div>
  )
}

export default BubbleMatrix
