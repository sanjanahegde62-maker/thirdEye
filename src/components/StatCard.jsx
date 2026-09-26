export default function StatCard({ label, value, sub, accent = 'plum' }) {
  const accents = {
    plum: 'text-plum',
    coral: 'text-coral-deep',
    terracotta: 'text-terracotta-deep',
  }
  return (
    <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
      <p className="text-xs font-medium text-plum/50">{label}</p>
      <p className={`mt-2 font-serif text-2xl font-semibold ${accents[accent]}`}>{value}</p>
      {sub && <p className="mt-1 text-xs text-plum/45">{sub}</p>}
    </div>
  )
}
