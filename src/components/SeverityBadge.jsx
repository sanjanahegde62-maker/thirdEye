const styles = {
  critical: 'bg-coral-soft text-coral-deep border-coral/30',
  high: 'bg-terracotta-soft text-terracotta-deep border-terracotta/30',
  medium: 'bg-sand text-plum border-sand-line',
  low: 'bg-ivory-200 text-plum/70 border-sand-line',
}

const labels = {
  critical: 'Critical',
  high: 'High',
  medium: 'Medium',
  low: 'Low',
}

export default function SeverityBadge({ severity, size = 'sm' }) {
  const pad = size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3 py-1.5 text-sm'
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border font-medium ${pad} ${styles[severity]}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {labels[severity]}
    </span>
  )
}
