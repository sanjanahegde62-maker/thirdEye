import { useState, useEffect, useCallback } from 'react'
import { useOutletContext, useNavigate } from 'react-router-dom'
import { Check, Loader2, Circle, ThumbsUp, MessageSquareWarning, Ban, ArrowRight, RefreshCw } from 'lucide-react'
import ProgressRing from '../components/ProgressRing'
import StatCard from '../components/StatCard'
import SeverityBadge from '../components/SeverityBadge'
import Button from '../components/Button'
import { getReviewsByProject } from '../services/api/reviews'
import { getFindingsByReview } from '../services/api/findings'

// ── Stage pipeline helpers ─────────────────────────────────────────────────────

const stageIcon = {
  done:    <Check   size={13} className="text-white" />,
  active:  <Loader2 size={13} className="animate-spin text-white" />,
  pending: <Circle  size={8}  className="fill-plum/25 text-plum/25" />,
}
const stageStyle = {
  done:    'bg-plum',
  active:  'bg-coral',
  pending: 'bg-transparent border-2 border-sand-line',
}

function buildStages(status, progress) {
  const p = progress ?? 0
  if (status === 'PENDING') return [
    { id: 'parse',    label: 'Parsing changeset',   state: 'active'  },
    { id: 'static',   label: 'Static analysis',     state: 'pending' },
    { id: 'security', label: 'Security scan',       state: 'pending' },
    { id: 'findings', label: 'Generating findings', state: 'pending' },
    { id: 'report',   label: 'Compiling report',    state: 'pending' },
  ]
  if (status === 'ANALYZING') return [
    { id: 'parse',    label: 'Parsing changeset',   state: p >= 30  ? 'done' : 'active'  },
    { id: 'static',   label: 'Static analysis',     state: p >= 50  ? 'done' : p >= 30  ? 'active' : 'pending' },
    { id: 'security', label: 'Security scan',       state: p >= 65  ? 'done' : p >= 50  ? 'active' : 'pending' },
    { id: 'findings', label: 'Generating findings', state: p >= 80  ? 'done' : p >= 65  ? 'active' : 'pending' },
    { id: 'report',   label: 'Compiling report',    state: p >= 100 ? 'done' : p >= 80  ? 'active' : 'pending' },
  ]
  if (status === 'COMPLETED') return [
    { id: 'parse',    label: 'Parsing changeset',   state: 'done' },
    { id: 'static',   label: 'Static analysis',     state: 'done' },
    { id: 'security', label: 'Security scan',       state: 'done' },
    { id: 'findings', label: 'Generating findings', state: 'done' },
    { id: 'report',   label: 'Compiling report',    state: 'done' },
  ]
  return [
    { id: 'parse',    label: 'Parsing changeset',   state: 'done'    },
    { id: 'static',   label: 'Static analysis',     state: 'pending' },
    { id: 'security', label: 'Security scan',       state: 'pending' },
    { id: 'findings', label: 'Generating findings', state: 'pending' },
    { id: 'report',   label: 'Analysis failed',     state: 'pending' },
  ]
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function Overview() {
  const { activeProject } = useOutletContext()
  const navigate = useNavigate()

  const [review,   setReview]   = useState(null)
  const [findings, setFindings] = useState([])
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState('')
  const [decision, setDecision] = useState(null)

  const loadData = useCallback(async () => {
    if (!activeProject?.id) return
    try {
      const reviews = await getReviewsByProject(activeProject.id)
      if (!reviews || reviews.length === 0) {
        setReview(null); setFindings([]); setLoading(false); return
      }
      const latest = reviews.reduce((a, b) => (a.id > b.id ? a : b))
      setReview(latest)
      if (latest.status === 'COMPLETED' || latest.status === 'FAILED') {
        const f = await getFindingsByReview(latest.id)
        setFindings(f || [])
      }
    } catch (err) {
      setError(err.message || 'Failed to load review data')
    } finally {
      setLoading(false)
    }
  }, [activeProject?.id])

  useEffect(() => {
    setLoading(true); setError(''); setReview(null); setFindings([])
    loadData()
  }, [loadData])

  useEffect(() => {
    if (!review) return
    if (review.status === 'COMPLETED' || review.status === 'FAILED') return
    const id = setInterval(loadData, 3000)
    return () => clearInterval(id)
  }, [review, loadData])

  if (loading) return (
    <div className="mx-auto flex max-w-5xl items-center justify-center py-20">
      <Loader2 size={24} className="animate-spin text-plum/40" />
    </div>
  )

  if (error) return (
    <div className="mx-auto max-w-5xl rounded-lg bg-coral/10 p-4 text-sm text-coral">{error}</div>
  )

  if (!review) return (
    <div className="mx-auto max-w-5xl rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-14 text-center text-sm text-plum/50">
      No reviews found for <strong>{activeProject.name}</strong>.{' '}
      <button onClick={() => navigate('/dashboard/diff')}
        className="font-medium text-terracotta-deep underline underline-offset-2">
        Submit a code diff
      </button>{' '}to get started.
    </div>
  )

  const stages        = buildStages(review.status, review.progress)
  const isRunning     = review.status === 'PENDING' || review.status === 'ANALYZING'
  const criticalCount = findings.filter(f => f.severity === 'critical').length
  const topFindings   = findings.filter(f => f.severity === 'critical' || f.severity === 'high')
  const statusLabel   = { PENDING: 'Queued', ANALYZING: 'In review', COMPLETED: 'Completed', FAILED: 'Failed' }[review.status] ?? review.status
  const statusBg      = { PENDING: 'bg-sand text-plum/70', ANALYZING: 'bg-coral-soft text-coral-deep', COMPLETED: 'bg-plum-50 text-plum', FAILED: 'bg-coral/10 text-coral-deep' }[review.status] ?? 'bg-sand text-plum/70'

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-7">

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold text-plum">Review overview</h1>
          <p className="mt-1 text-sm text-plum/60">
            {activeProject.name} · {review.commits} commit{review.commits !== 1 ? 's' : ''} · #{review.id}: {review.title}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {isRunning && (
            <button onClick={loadData}
              className="flex items-center gap-1.5 rounded-full border border-sand-line bg-ivory-card px-3 py-1.5 text-xs text-plum/60 hover:text-plum transition-colors">
              <RefreshCw size={12} /> Refresh
            </button>
          )}
          <span className={`rounded-full px-3.5 py-1.5 text-sm font-medium ${statusBg}`}>{statusLabel}</span>
        </div>
      </div>

      {/* Progress + stats */}
      <div className="grid grid-cols-1 gap-5 md:grid-cols-[auto_1fr]">
        <div className="flex items-center gap-5 rounded-xl2 border border-sand-line bg-ivory-card p-6 shadow-soft">
          <ProgressRing
            progress={review.progress}
            sub={isRunning ? 'analysing…' : review.status === 'COMPLETED' ? 'complete' : review.status.toLowerCase()}
          />
          <div className="flex flex-col gap-2.5">
            {stages.map(s => (
              <div key={s.id} className="flex items-center gap-2.5">
                <span className={`flex h-5 w-5 items-center justify-center rounded-full ${stageStyle[s.state]}`}>
                  {stageIcon[s.state]}
                </span>
                <span className={`text-sm ${s.state === 'pending' ? 'text-plum/40' : 'text-plum font-medium'}`}>{s.label}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Files changed"  value={review.filesChanged} />
          <StatCard label="Lines added"    value={`+${review.linesAdded}`}   accent="terracotta" />
          <StatCard label="Lines removed"  value={`−${review.linesRemoved}`} />
          <StatCard label="Open findings"  value={findings.length} sub={`${criticalCount} critical`} accent="coral" />
        </div>
      </div>

      {/* Top findings (COMPLETED only) */}
      {review.status === 'COMPLETED' && (
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-6 shadow-soft">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-serif text-lg font-semibold text-plum">Needs your attention</h2>
            <button onClick={() => navigate('/dashboard/findings')}
              className="flex items-center gap-1 text-sm font-medium text-terracotta-deep hover:text-terracotta">
              See all findings <ArrowRight size={14} />
            </button>
          </div>
          {topFindings.length === 0
            ? <p className="text-sm text-plum/50">No critical or high findings — looking good!</p>
            : <div className="flex flex-col divide-y divide-sand-line">
                {topFindings.slice(0, 5).map(f => (
                  <div key={f.id} className="flex items-center justify-between gap-4 py-3.5 first:pt-0 last:pb-0">
                    <div className="flex items-center gap-3.5 min-w-0">
                      <SeverityBadge severity={f.severity} />
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-plum">{f.title}</p>
                        <p className="truncate text-xs text-plum/50 font-mono">{f.file}:{f.line}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
          }
        </div>
      )}

      {/* Running banner */}
      {isRunning && (
        <div className="flex items-center gap-3 rounded-xl2 border border-sand-line bg-ivory-card px-6 py-4 shadow-soft">
          <Loader2 size={16} className="animate-spin shrink-0 text-coral" />
          <p className="text-sm text-plum/70">Analysis is running in the background — this page refreshes automatically every 3 seconds.</p>
        </div>
      )}

      {/* FAILED banner */}
      {review.status === 'FAILED' && (
        <div className="rounded-xl2 border border-coral/25 bg-coral-soft/30 p-4 text-sm text-coral-deep">
          Analysis failed. You can re-trigger it from the{' '}
          <button onClick={() => navigate('/dashboard/diff')} className="font-medium underline underline-offset-2">Code diff</button> page.
        </div>
      )}

      {/* Human approval (COMPLETED only) */}
      {review.status === 'COMPLETED' && (
        <div className="rounded-xl2 border border-sand-line bg-ivory-card p-6 shadow-soft">
          <h2 className="font-serif text-lg font-semibold text-plum">Human approval</h2>
          <p className="mt-1 mb-5 text-sm text-plum/60">
            ThirdEye doesn't merge anything on its own. Review the findings and generated tests, then record your decision here.
          </p>
          {decision ? (
            <div className={`flex items-center gap-3 rounded-xl border px-4 py-3.5 ${
              decision === 'approved' ? 'border-plum/20 bg-plum-50 text-plum'
              : decision === 'changes' ? 'border-terracotta/25 bg-terracotta-soft/40 text-terracotta-deep'
              : 'border-coral/25 bg-coral-soft/50 text-coral-deep'}`}>
              {decision === 'approved' && <ThumbsUp size={16} />}
              {decision === 'changes'  && <MessageSquareWarning size={16} />}
              {decision === 'blocked'  && <Ban size={16} />}
              <span className="text-sm font-medium">
                {decision === 'approved' && 'Approved. This is ready to merge.'}
                {decision === 'changes'  && 'Sent back for changes. The author has been notified.'}
                {decision === 'blocked'  && 'Blocked. This cannot merge until the critical finding is resolved.'}
              </span>
              <button onClick={() => setDecision(null)} className="ml-auto text-xs font-medium underline underline-offset-2 opacity-70 hover:opacity-100">Undo</button>
            </div>
          ) : (
            <div className="flex flex-wrap gap-3">
              <Button variant="primary" icon={ThumbsUp}            onClick={() => setDecision('approved')}>Approve</Button>
              <Button variant="outline" icon={MessageSquareWarning} onClick={() => setDecision('changes')}>Request changes</Button>
              <Button variant="ghost"   icon={Ban}                  onClick={() => setDecision('blocked')}>Block merge</Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
