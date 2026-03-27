import React from 'react'
import { motion } from 'framer-motion'
import { Activity, Battery, Gauge, Radar, TrendingDown, TrendingUp, Wind } from 'lucide-react'
import { useSessionIntelligence } from '../hooks/useSessionIntelligence'

const AnalysisView = () => {
  const intelligence = useSessionIntelligence('30d')
  const bestBand = intelligence.tempBands[0]
  const secondBand = intelligence.tempBands[1]

  return (
    <motion.section
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="view-container"
    >
      <header className="page-header">
        <p className="view-title">Signals</p>
        <h1 className="page-title">Read the pattern, not just the totals.</h1>
        <p className="page-subtitle">
          This rebuild treats session history as a behavior trace. The goal is to show what is getting denser, sloppier, more battery-hungry, or more repeatable.
        </p>
      </header>

      <div className="summary-grid">
        <SignalCard
          icon={<Activity size={16} />}
          label="Sessions in scope"
          value={intelligence.summary.sessionCount}
          note={`${intelligence.pace.activeDays} active days`}
        />
        <SignalCard
          icon={<Wind size={16} />}
          label="Average hits"
          value={intelligence.summary.avgHits ? intelligence.summary.avgHits.toFixed(1) : '—'}
          note={`Momentum ${formatDelta(intelligence.momentum.hitDelta, 'hits')}`}
        />
        <SignalCard
          icon={<Gauge size={16} />}
          label="Efficiency"
          value={intelligence.summary.avgEfficiency ? `${Math.round(intelligence.summary.avgEfficiency)}` : '—'}
          note={bestBand ? `Best band ${bestBand.bucket}C` : 'Need more history'}
        />
        <SignalCard
          icon={<Battery size={16} />}
          label="Battery read"
          value={intelligence.battery.sessionsRemaining ?? '—'}
          note={intelligence.battery.confidenceLabel}
        />
      </div>

      <div className="split-layout">
        <div className="glass-card panel-card">
          <div className="feature-panel-head">
            <div className="feature-icon"><Radar size={16} /></div>
            <div className="section-heading">What changed recently</div>
          </div>
          <InfoRow label="Hits per session" value={formatDelta(intelligence.momentum.hitDelta, 'vs prior 10')} />
          <InfoRow label="Drain per session" value={formatDelta(intelligence.momentum.drainDelta, 'points')} />
          <InfoRow label="Session duration" value={formatDelta(intelligence.momentum.durationDelta, 'min')} />
          <InfoRow label="Sessions per active day" value={intelligence.pace.sessionsPerActiveDay || '—'} />
          <InfoRow label="Peak day" value={intelligence.pace.peakDay || '—'} />
        </div>

        <div className="glass-card panel-card">
          <div className="feature-panel-head">
            <div className="feature-icon">
              {intelligence.momentum.hitDelta >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
            </div>
            <div className="section-heading">Operating read</div>
          </div>
          <div className="feature-body">
            {intelligence.recommendations[0]?.detail || 'The session model needs more completed runs before it can separate noise from pattern.'}
          </div>
          <div className="feature-footer">
            {intelligence.recommendations[0]?.title || 'No clear lead yet'}
          </div>
        </div>
      </div>

      <div className="glass-card panel-card">
        <div className="section-heading">Temperature bands that actually pay back</div>
        {!intelligence.tempBands.length ? (
          <div className="empty-copy">
            Once a few sessions have peak temperature data, this will rank the bands by hits returned against battery spent.
          </div>
        ) : (
          <div className="band-list">
            {intelligence.tempBands.slice(0, 4).map((band, index) => (
              <div key={band.bucket} className="band-row">
                <div className="band-rank">{index + 1}</div>
                <div className="band-main">
                  <div className="band-title">{band.bucket}C</div>
                  <div className="band-copy">{band.count} sessions, {band.avgHits} hits average, {band.avgDrain}% drain average</div>
                </div>
                <div className="band-score">{band.avgScore}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="split-layout">
        <div className="glass-card panel-card">
          <div className="section-heading">Strongest signal</div>
          <div className="feature-body">
            {bestBand
              ? `${bestBand.bucket}C is the strongest operating band right now. It is producing the best blend of hit count and battery economy.`
              : 'No temperature preference yet.'}
          </div>
          <div className="feature-footer">
            {bestBand ? `Score ${bestBand.avgScore} across ${bestBand.count} sessions` : 'Need more history'}
          </div>
        </div>

        <div className="glass-card panel-card">
          <div className="section-heading">Secondary signal</div>
          <div className="feature-body">
            {secondBand
              ? `${secondBand.bucket}C is the next viable lane. Useful if you want variety without abandoning the data-backed pattern.`
              : 'A second operating lane has not emerged yet.'}
          </div>
          <div className="feature-footer">
            {secondBand ? `Score ${secondBand.avgScore} across ${secondBand.count} sessions` : 'Still sorting'}
          </div>
        </div>
      </div>

      <div className="glass-card panel-card">
        <div className="section-heading">Actionable flags</div>
        <div className="story-list">
          {intelligence.recommendations.map((item) => (
            <div key={item.title} className={`story-card tone-${item.tone}`}>
              <div className="story-title">{item.title}</div>
              <div className="story-copy">{item.detail}</div>
            </div>
          ))}
        </div>
      </div>
    </motion.section>
  )
}

const SignalCard = ({ icon, label, value, note }) => (
  <div className="metric-card glass-card">
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent-cyan)' }}>
      {icon}
      <span className="metric-label">{label}</span>
    </div>
    <div className="metric-value" style={{ fontSize: '24px' }}>{value}</div>
    <div className="metric-note">{note}</div>
  </div>
)

const InfoRow = ({ label, value }) => (
  <div className="info-row">
    <span className="info-row-label">{label}</span>
    <span className="info-row-value">{value}</span>
  </div>
)

const formatDelta = (value, suffix) => {
  if (!value) return `Flat ${suffix}`
  return `${value > 0 ? '+' : ''}${value.toFixed ? value.toFixed(1) : value} ${suffix}`
}

export default AnalysisView
