export const HISTORY_RANGES = ['7d', '30d', '90d', 'All']

const RANGE_MAP = {
  '7d':  7 * 86400000,
  '30d': 30 * 86400000,
  '90d': 90 * 86400000,
  'All': null,
}

export function filterSessionsByRange(list, range) {
  if (!list || !list.length) return []
  const ms = RANGE_MAP[range]
  if (!ms) return list
  const cutoff = Date.now() - ms
  return list.filter(item => (item.startTimeMs ?? 0) >= cutoff)
}
