import { useState, useEffect, useCallback } from 'react'
import { useOutletContext } from 'react-router-dom'
import { Download, ShieldCheck, Loader2 } from 'lucide-react'
import Button from '../components/Button'
import ProgressRing from '../components/ProgressRing'
import SeverityBadge from '../components/SeverityBadge'
import { getReviewsByProject } from '../services/api/reviews'
import { getFindingsByReview } from '../services/api/findings'
import { apiRequest } from '../services/api/client'

export default function Report() {
  const { activeProject } = useOutletContext()

  const [review,   setReview]   = useState(null)
  const [findings, setFindings] = useState([])
  const [summary,  setSummary]  = useState(null)
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState('')

  const loadData = useCallback(async () => {
    if (!activeProject?.id) return
    try {
      const reviews = await getReviewsByProject(activeProject.id)
      if (!reviews || reviews.length === 0) { setLoading(false); return }

      const latest = reviews.reduce((a, b) => (a.id > b.id ? a : b))
      setReview(latest)

      if (latest.status === 'COMPLETED') {
        const [f, s] = await Promise.all([
          getFindingsByReview(latest.id),
          apiRequest(`/api/reviews/${latest.id}/findings/summary`, { method: 'GET' }),
        ])
        setFindings(f || [])
        setSummary(s)
      }
    } catch (err) {
      setError(err.message || 'Failed to load report data')
    } finally {
      setLoading(false)
    }
  }, [activeProject?.id])

  useEffect(() => {
    setLoading(true); setError(''); setReview(null); setFindings([]); setSummary(null)
    loadData()
  }, [loadData])

  if (loading) return (
    <div className="mx-auto flex max-w-4xl items-center justify-center py-20">
      <Loader2 size={24} className="animate-spin text-plum/40" />
    </div>
  )

  if (error) return (
    <div className="mx-auto max-w-4xl rounded-lg bg-coral/10 p-4 text-sm text-coral">{error}</div>
  )

  if (!review) return (
    <div className="mx-auto max-w-4xl rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-14 text-center text-sm text-plum/50">
      No reviews found for <strong>{activeProject.name}</strong>. Submit a code diff first.
    </div>
  )

  if (review.status !== 'COMPLETED') return (
    <div className="mx-auto max-w-4xl rounded-xl2 border border-sand-line bg-ivory-card p-6 text-sm text-plum/60">
      <p className="font-medium text-plum mb-1">Report not yet available</p>
      Review #{review.id} is currently <strong>{review.status}</strong>.
      The report will be available once analysis completes.
    </div>
  )

  const criticalCount = summary?.critical ?? findings.filter(f => f.severity === 'critical').length
  const highCount     = summary?.high     ?? findings.filter(f => f.severity === 'high').length
  const total         = summary?.total    ?? findings.length
  // Risk score: weighted estimate based on severity counts (not AI-computed)
  const riskScore = Math.min(100, Math.round(criticalCount * 30 + highCount * 15 + (summary?.medium ?? 0) * 5))

  const handleExportPdf = () => window.print()

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold text-plum">Final report</h1>
          <p className="mt-1 text-sm text-plum/60">
            {activeProject.name} · review #{review.id} · {review.title}
          </p>
        </div>
        <Button variant="outline" icon={Download} onClick={handleExportPdf} data-print-hide>Export PDF</Button>
      </div>

      {/* Summary hero */}
      <div className="rounded-xl2 border border-sand-line bg-plum p-7 text-ivory-100 shadow-lifted">
        <div className="flex flex-wrap items-center gap-8">
          <ProgressRing progress={riskScore} size={104} label={riskScore} sub="risk score" />
          <div className="flex flex-col gap-1.5">
            <span className={`w-fit rounded-full px-3 py-1 text-xs font-medium text-white ${criticalCount > 0 ? 'bg-coral' : 'bg-plum/60'}`}>
              {criticalCount > 0 ? 'Changes required' : 'No critical issues'}
            </span>
            <h2 className="font-serif text-xl font-semibold">
              {criticalCount > 0
                ? `${criticalCount} critical issue${criticalCount > 1 ? 's' : ''} must be resolved before merging`
                : 'No critical issues — review the findings before merging'}
            </h2>
            <p className="max-w-md text-sm text-ivory-100/70">
              {total} finding{total !== 1 ? 's' : ''} across the changeset.
              {criticalCount > 0 && ' Resolve all critical findings first.'}
            </p>
            {/* Risk score is a weighted estimate based on finding severity — not AI-computed */}
            <p className="text-xs text-ivory-100/40 mt-1">⚠ Risk score is a weighted estimate based on finding severity — not AI-computed</p>
          </div>
        </div>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
          <p className="text-xs font-medium text-plum/50">Findings</p>
          <p className="mt-2 font-serif text-2xl font-semibold text-plum">{total}</p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {[['critical', criticalCount], ['high', highCount], ['medium', summary?.medium ?? 0], ['low', summary?.low ?? 0]].map(([sev, count]) =>
              count > 0 ? <span key={sev} className="text-[11px] font-medium text-plum/50">{count} {sev}</span> : null
            )}
          </div>
        </div>
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
          <p className="text-xs font-medium text-plum/50">Files changed</p>
          <p className="mt-2 font-serif text-2xl font-semibold text-plum">{review.filesChanged}</p>
        </div>
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
          <p className="text-xs font-medium text-plum/50">Lines added</p>
          <p className="mt-2 font-serif text-2xl font-semibold text-terracotta-deep">+{review.linesAdded}</p>
        </div>
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
          <p className="text-xs font-medium text-plum/50">Lines removed</p>
          <p className="mt-2 font-serif text-2xl font-semibold text-plum">−{review.linesRemoved}</p>
        </div>
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
          <p className="text-xs font-medium text-plum/50">Generated tests</p>
          <p className="mt-2 font-serif text-lg font-semibold text-plum/50">NOT_RUN</p>
          <p className="mt-1 text-[11px] text-plum/40">Scaffolds generated; not executed</p>
        </div>
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
          <p className="text-xs font-medium text-plum/50">Coverage delta</p>
          <p className="mt-2 font-serif text-lg font-semibold text-plum/50">Not available</p>
          <p className="mt-1 text-[11px] text-plum/40">Tests have not been executed</p>
        </div>
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft col-span-2">
          <p className="text-xs font-medium text-plum/50">Approver</p>
          <p className="mt-2 font-serif text-lg font-semibold text-plum/50">Not available</p>
          <p className="mt-1 text-[11px] text-plum/40">No approval has been recorded</p>
        </div>
      </div>

      {/* Findings list */}
      <div className="rounded-xl2 border border-sand-line bg-ivory-card p-6 shadow-soft">
        <h2 className="mb-4 font-serif text-lg font-semibold text-plum">Findings summary</h2>
        {findings.length === 0
          ? <p className="text-sm text-plum/50">No findings for this review.</p>
          : <div className="flex flex-col divide-y divide-sand-line">
              {findings.map(f => (
                <div key={f.id} className="flex items-center justify-between gap-4 py-3">
                  <div className="flex items-center gap-3">
                    <SeverityBadge severity={f.severity} />
                    <span className="text-sm text-plum">{f.title}</span>
                  </div>
                  <span className="font-mono text-xs text-plum/45">{f.file?.split('/').pop()}</span>
                </div>
              ))}
            </div>
        }
      </div>

      {/* Footer note */}
      <div className="flex items-center gap-3 rounded-xl2 border border-sand-line bg-peach-soft px-6 py-4 shadow-soft">
        <ShieldCheck size={18} className="shrink-0 text-terracotta-deep" />
        <p className="text-sm text-plum/75">
          Findings produced by <span className="font-medium text-plum">ThirdEye AI</span> deterministic static analysis (rule-based, not AI-powered).
          Test scaffolds are generated from findings but have not been executed.
          Coverage delta and approver are not available.
        </p>
      </div>
    </div>
  )
}
