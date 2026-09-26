import { useState, useEffect, useCallback } from 'react'
import { CheckCircle2, XCircle, Clock, Play, Loader2, Code } from 'lucide-react'
import { useOutletContext } from 'react-router-dom'
import Button from '../components/Button'
import StatCard from '../components/StatCard'
import { getReviewsByProject } from '../services/api/reviews'
import { getTests, generateTests } from '../services/api/tests'

const statusMeta = {
  PASSED:  { icon: CheckCircle2, color: 'text-plum',      label: 'Passed'  },
  FAILED:  { icon: XCircle,      color: 'text-coral-deep', label: 'Failed'  },
  PENDING: { icon: Clock,        color: 'text-plum/40',    label: 'Pending' },
  // normalise lowercase values from mockData if still in use
  passed:  { icon: CheckCircle2, color: 'text-plum',      label: 'Passed'  },
  failed:  { icon: XCircle,      color: 'text-coral-deep', label: 'Failed'  },
  pending: { icon: Clock,        color: 'text-plum/40',    label: 'Pending' },
}
const tabs = ['All', 'Passed', 'Failed', 'Pending']

export default function Tests() {
  const { activeProject } = useOutletContext()

  const [review,      setReview]      = useState(null)
  const [tests,       setTests]       = useState([])
  const [tab,         setTab]         = useState('All')
  const [loading,     setLoading]     = useState(true)
  const [generating,  setGenerating]  = useState(false)
  const [error,       setError]       = useState('')
  const [genError,    setGenError]    = useState('')

  const loadReviewAndTests = useCallback(async () => {
    if (!activeProject?.id) return
    try {
      setLoading(true); setError('')
      const reviews = await getReviewsByProject(activeProject.id)
      if (!reviews || reviews.length === 0) { setLoading(false); return }
      const latest = reviews.reduce((a, b) => (a.id > b.id ? a : b))
      setReview(latest)
      if (latest.status === 'COMPLETED') {
        const t = await getTests(latest.id)
        setTests(t || [])
      }
    } catch (err) {
      setError(err.message || 'Failed to load tests')
    } finally {
      setLoading(false)
    }
  }, [activeProject?.id])

  useEffect(() => { loadReviewAndTests() }, [loadReviewAndTests])

  const handleGenerate = async () => {
    if (!review) return
    setGenerating(true); setGenError('')
    try {
      const t = await generateTests(review.id)
      setTests(t || [])
    } catch (err) {
      setGenError(err.message || 'Failed to generate tests')
    } finally {
      setGenerating(false)
    }
  }

  const normaliseStatus = (s) => (s ?? 'pending').toUpperCase()

  const filtered = tab === 'All'
    ? tests
    : tests.filter(t => normaliseStatus(t.status) === tab.toUpperCase())

  const counts = tests.reduce((acc, t) => {
    const s = normaliseStatus(t.status)
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})

  if (loading) return (
    <div className="mx-auto flex max-w-4xl items-center justify-center py-20">
      <Loader2 size={24} className="animate-spin text-plum/40" />
    </div>
  )

  if (error) return (
    <div className="mx-auto max-w-4xl rounded-lg bg-coral/10 p-4 text-sm text-coral">{error}</div>
  )

  const canGenerate = review?.status === 'COMPLETED'

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold text-plum">Generated tests</h1>
          <p className="mt-1 text-sm text-plum/60">
            {tests.length > 0
              ? `${tests.length} test stubs generated for ${activeProject.name}`
              : `No tests generated yet for ${activeProject.name}`}
          </p>
        </div>
        <Button
          variant="outline"
          icon={Play}
          onClick={handleGenerate}
          disabled={!canGenerate || generating}
        >
          {generating ? 'Generating…' : tests.length > 0 ? 'Re-generate' : 'Generate tests'}
        </Button>
      </div>

      {/* Mock disclaimer */}
      <div className="flex items-start gap-3 rounded-xl border border-sand-line bg-peach-soft px-4 py-3 text-sm text-plum/70">
        <Code size={16} className="mt-0.5 shrink-0 text-terracotta-deep" />
        <span>
          <strong className="text-plum">Simulated test generation</strong> — these are template stubs
          derived from findings, not AI-written tests. They are never executed.
          Real IBM Granite test generation will replace this in the next milestone.
        </span>
      </div>

      {genError && (
        <p className="rounded-lg bg-coral/10 p-3 text-sm text-coral">{genError}</p>
      )}

      {!review && (
        <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
          No reviews found. Submit a code diff first.
        </div>
      )}

      {review && review.status !== 'COMPLETED' && (
        <div className="rounded-xl2 border border-sand-line bg-ivory-card px-5 py-8 text-center text-sm text-plum/50">
          Review #{review.id} is <strong>{review.status}</strong>. Tests can be generated once analysis completes.
        </div>
      )}

      {canGenerate && tests.length === 0 && !generating && (
        <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
          No tests generated yet. Click <strong>Generate tests</strong> to create stubs from the findings.
        </div>
      )}

      {tests.length > 0 && (
        <>
          <div className="grid grid-cols-3 gap-4">
            <StatCard label="Passed"  value={counts.PASSED  || 0} accent="plum"       />
            <StatCard label="Failed"  value={counts.FAILED  || 0} accent="coral"      />
            <StatCard label="Pending" value={counts.PENDING || 0} accent="terracotta" />
          </div>

          <div className="flex gap-1 rounded-xl border border-sand-line bg-ivory-card p-1 w-fit shadow-soft">
            {tabs.map(t => (
              <button key={t} onClick={() => setTab(t)}
                className={`rounded-lg px-4 py-1.5 text-sm font-medium transition-colors ${
                  tab === t ? 'bg-plum text-ivory-100' : 'text-plum/55 hover:text-plum'}`}>
                {t}
              </button>
            ))}
          </div>

          <div className="flex flex-col gap-3">
            {filtered.map(t => {
              const status = normaliseStatus(t.status)
              const meta   = statusMeta[status] ?? statusMeta.PENDING
              const Icon   = meta.icon
              return (
                <div key={t.id}
                  className="rounded-xl2 border border-sand-line bg-ivory-card px-5 py-4 shadow-soft">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-start gap-3">
                      <Icon size={18} className={`mt-0.5 shrink-0 ${meta.color}`} />
                      <div>
                        <p className="text-sm font-medium text-plum">{t.name}</p>
                        <p className="mt-1 font-mono text-xs text-plum/50">{t.file}</p>
                      </div>
                    </div>
                    <span className="shrink-0 rounded-full bg-sand px-2 py-0.5 text-[10px] text-plum/50">
                      mock stub
                    </span>
                  </div>
                  {t.linkedFindingId && (
                    <div className="mt-2 ml-8 text-xs text-plum/45">
                      Covers finding <span className="font-mono">#{t.linkedFindingId}</span>
                    </div>
                  )}
                  {t.testCode && (
                    <details className="mt-3 ml-8">
                      <summary className="cursor-pointer text-xs font-medium text-plum/50 hover:text-plum">
                        View generated code
                      </summary>
                      <pre className="mt-2 overflow-x-auto rounded-lg bg-plum-50 p-3 font-mono text-xs text-plum/80 whitespace-pre-wrap">
                        {t.testCode}
                      </pre>
                    </details>
                  )}
                </div>
              )
            })}
            {filtered.length === 0 && (
              <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
                No tests match this filter.
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
