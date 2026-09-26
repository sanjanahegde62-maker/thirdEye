
import { useState, useEffect, useCallback } from 'react'
import {
  CheckCircle2,
  XCircle,
  Clock,
  Play,
  Loader2,
  Code,
  Download
} from 'lucide-react'
import { useOutletContext } from 'react-router-dom'
import Button from '../components/Button'
import StatCard from '../components/StatCard'
import { getReviewsByProject } from '../services/api/reviews'
import {
  getTests,
  generateTests,
  checkTestReadiness
} from '../services/api/tests'

// Test status configuration
const statusMeta = {
  PASSED: {
    icon: CheckCircle2,
    color: 'text-plum',
    label: 'Passed'
  },
  FAILED: {
    icon: XCircle,
    color: 'text-coral-deep',
    label: 'Failed'
  },
  PENDING: {
    icon: Clock,
    color: 'text-plum/40',
    label: 'Not executed'
  }
}

const tabs = ['All', 'Passed', 'Failed', 'Pending']

export default function Tests() {
  const { activeProject } = useOutletContext()

  const [review, setReview] = useState(null)
  const [tests, setTests] = useState([])
  const [tab, setTab] = useState('All')
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  const [genError, setGenError] = useState('')

  // Readiness check state
  const [readiness, setReadiness] = useState({})
  const [checkingTestId, setCheckingTestId] = useState(null)

  // Normalize backend test statuses.
  // NOT_RUN means the test has not been executed.
  // It must not be treated as passed or failed.
  const normaliseStatus = (s) => {
    const status = (s ?? 'PENDING').toUpperCase()

    if (status === 'NOT_RUN') return 'PENDING'

    return status
  }

  // Load the latest review and its generated tests
  const loadReviewAndTests = useCallback(async () => {
    if (!activeProject?.id) {
      setLoading(false)
      return
    }

    try {
      setLoading(true)
      setError('')

      const reviews = await getReviewsByProject(activeProject.id)

      if (!reviews || reviews.length === 0) {
        setReview(null)
        setTests([])
        setReadiness({})
        return
      }

      const latest = reviews.reduce((a, b) =>
          a.id > b.id ? a : b
      )

      setReview(latest)

      if (latest.status === 'COMPLETED') {
        const t = await getTests(latest.id)
        setTests(t || [])
      } else {
        setTests([])
      }

      setReadiness({})
    } catch (err) {
      setError(err.message || 'Failed to load tests')
    } finally {
      setLoading(false)
    }
  }, [activeProject?.id])

  useEffect(() => {
    loadReviewAndTests()
  }, [loadReviewAndTests])

  // Generate test scaffolds using the backend
  const handleGenerate = async () => {
    if (!review) return

    setGenerating(true)
    setGenError('')
    setReadiness({})

    try {
      const t = await generateTests(review.id)

      setTests(t || [])
      setTab('All')
    } catch (err) {
      setGenError(err.message || 'Failed to generate tests')
    } finally {
      setGenerating(false)
    }
  }

  // Check whether a generated test is ready for review.
  // This does NOT execute the test or mark it as passed.
  const handleCheckReadiness = async (test) => {
    if (!review?.id || !test?.id) return

    setCheckingTestId(test.id)
    setGenError('')

    try {
      const result = await checkTestReadiness(
          review.id,
          test.id
      )

      setReadiness(prev => ({
        ...prev,
        [test.id]: result
      }))
    } catch (err) {
      setGenError(
          err.message || 'Failed to check test readiness'
      )
    } finally {
      setCheckingTestId(null)
    }
  }

  // Download generated Java test code
  const handleDownload = (test) => {
    if (!test?.testCode) {
      setGenError(
          'No generated test code is available to download.'
      )
      return
    }

    try {
      const blob = new Blob(
          [test.testCode],
          { type: 'text/plain;charset=utf-8' }
      )

      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')

      // Use the backend-provided Java file name
      // and remove any directory path.
      const originalName =
          test.file || `GeneratedTest_${test.id}.java`

      const filename = originalName
          .split(/[\\/]/)
          .pop()

      link.href = url
      link.download = filename.endsWith('.java')
          ? filename
          : `${filename}.java`

      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      // Release the temporary download URL
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (err) {
      setGenError(
          err.message || 'Failed to download generated test'
      )
    }
  }

  // Filter tests according to selected tab
  const filtered = tab === 'All'
      ? tests
      : tests.filter(
          t => normaliseStatus(t.status) === tab.toUpperCase()
      )

  // Calculate test counts
  const counts = tests.reduce((acc, t) => {
    const s = normaliseStatus(t.status)
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})

  if (loading) {
    return (
        <div className="mx-auto flex max-w-4xl items-center justify-center py-20">
          <Loader2
              size={24}
              className="animate-spin text-plum/40"
          />
        </div>
    )
  }

  if (error) {
    return (
        <div className="mx-auto max-w-4xl rounded-lg bg-coral/10 p-4 text-sm text-coral">
          {error}
        </div>
    )
  }

  const canGenerate = review?.status === 'COMPLETED'

  return (
      <div className="mx-auto flex max-w-4xl flex-col gap-6">

        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="font-serif text-2xl font-semibold text-plum">
              Generated tests
            </h1>

            <p className="mt-1 text-sm text-plum/60">
              {tests.length > 0
                  ? `${tests.length} test scaffolds generated for ${activeProject.name}`
                  : `No tests generated yet for ${activeProject.name}`}
            </p>
          </div>

          <Button
              variant="outline"
              icon={Play}
              onClick={handleGenerate}
              disabled={!canGenerate || generating}
          >
            {generating
                ? 'Generating…'
                : tests.length > 0
                    ? 'Re-generate'
                    : 'Generate tests'}
          </Button>
        </div>

        {/* Test generation information */}
        <div className="flex items-start gap-3 rounded-xl border border-sand-line bg-peach-soft px-4 py-3 text-sm text-plum/70">
          <Code
              size={16}
              className="mt-0.5 shrink-0 text-terracotta-deep"
          />

          <span>
          <strong className="text-plum">
            Rule-based test scaffolding
          </strong>
            {' '}— test scaffolds are generated deterministically
          from static analysis findings. Generated tests are not
          automatically executed and may require implementation
          before use. All tests remain unverified until they are
          implemented and run.
        </span>
        </div>

        {/* Generation or download error */}
        {genError && (
            <p className="rounded-lg bg-coral/10 p-3 text-sm text-coral">
              {genError}
            </p>
        )}

        {/* No reviews */}
        {!review && (
            <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
              No reviews found. Submit a code diff first.
            </div>
        )}

        {/* Review is not completed */}
        {review && review.status !== 'COMPLETED' && (
            <div className="rounded-xl2 border border-sand-line bg-ivory-card px-5 py-8 text-center text-sm text-plum/50">
              Review #{review.id} is{' '}
              <strong>{review.status}</strong>.
              {' '}Tests can be generated once analysis completes.
            </div>
        )}

        {/* No tests generated */}
        {canGenerate && tests.length === 0 && !generating && (
            <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
              No tests generated yet. Click{' '}
              <strong>Generate tests</strong> to create scaffolds
              from the findings.
            </div>
        )}

        {/* Generated tests */}
        {tests.length > 0 && (
            <>
              {/* Test statistics */}
              <div className="grid grid-cols-3 gap-4">
                <StatCard
                    label="Passed"
                    value={counts.PASSED || 0}
                    accent="plum"
                />

                <StatCard
                    label="Failed"
                    value={counts.FAILED || 0}
                    accent="coral"
                />

                <StatCard
                    label="Not executed"
                    value={counts.PENDING || 0}
                    accent="terracotta"
                />
              </div>

              {/* Filter tabs */}
              <div className="flex w-fit gap-1 rounded-xl border border-sand-line bg-ivory-card p-1 shadow-soft">
                {tabs.map(t => (
                    <button
                        key={t}
                        onClick={() => setTab(t)}
                        className={`rounded-lg px-4 py-1.5 text-sm font-medium transition-colors ${
                            tab === t
                                ? 'bg-plum text-ivory-100'
                                : 'text-plum/55 hover:text-plum'
                        }`}
                    >
                      {t}
                    </button>
                ))}
              </div>

              {/* Test cards */}
              <div className="flex flex-col gap-3">
                {filtered.map(t => {
                  const status = normaliseStatus(t.status)
                  const meta = statusMeta[status] ?? statusMeta.PENDING
                  const Icon = meta.icon
                  const readinessResult = readiness[t.id]

                  return (
                      <div
                          key={t.id}
                          className="rounded-xl2 border border-sand-line bg-ivory-card px-5 py-4 shadow-soft"
                      >
                        {/* Test heading and status */}
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex items-start gap-3">
                            <Icon
                                size={18}
                                className={`mt-0.5 shrink-0 ${meta.color}`}
                            />

                            <div>
                              <p className="text-sm font-medium text-plum">
                                {t.name}
                              </p>

                              <p className="mt-1 font-mono text-xs text-plum/50">
                                {t.file}
                              </p>
                            </div>
                          </div>

                          <span className="shrink-0 rounded-full bg-sand px-2 py-0.5 text-[10px] text-plum/50">
                      {status === 'PENDING'
                          ? 'Not executed'
                          : meta.label}
                    </span>
                        </div>

                        {/* Linked finding */}
                        {t.linkedFindingId && (
                            <div className="mt-2 ml-8 text-xs text-plum/45">
                              Covers finding{' '}
                              <span className="font-mono">
                        #{t.linkedFindingId}
                      </span>
                            </div>
                        )}

                        {/* Readiness result */}
                        {readinessResult && (
                            <div
                                className={`mt-3 ml-8 rounded-lg border p-3 text-xs ${
                                    readinessResult.readyForExecution
                                        ? 'border-plum/20 bg-plum/5 text-plum'
                                        : 'border-coral/20 bg-coral/5 text-coral-deep'
                                }`}
                            >
                              <p className="font-semibold">
                                {readinessResult.readyForExecution
                                    ? 'No common scaffold placeholders detected'
                                    : 'Not ready for execution'}
                              </p>

                              <p className="mt-1">
                                {readinessResult.message}
                              </p>

                              <p className="mt-2 text-plum/50">
                                This check does not execute the test or
                                verify that its assertions are correct.
                              </p>
                            </div>
                        )}

                        {/* Generated test code and actions */}
                        {t.testCode && (
                            <div className="mt-3 ml-8 flex flex-wrap items-start gap-3">

                              {/* View generated code */}
                              <details className="min-w-0 flex-1">
                                <summary className="cursor-pointer text-xs font-medium text-plum/50 hover:text-plum">
                                  View generated code
                                </summary>

                                <pre className="mt-2 overflow-x-auto rounded-lg bg-plum-50 p-3 font-mono text-xs text-plum/80 whitespace-pre-wrap">
                          {t.testCode}
                        </pre>
                              </details>

                              {/* Check readiness */}
                              <button
                                  type="button"
                                  onClick={() => handleCheckReadiness(t)}
                                  disabled={checkingTestId === t.id}
                                  className="inline-flex shrink-0 items-center gap-2 rounded-lg border border-sand-line bg-ivory-card px-3 py-2 text-xs font-medium text-plum transition-colors hover:bg-sand disabled:cursor-not-allowed disabled:opacity-50"
                                  title="Check whether this test contains scaffold placeholders"
                              >
                                {checkingTestId === t.id ? (
                                    <Loader2
                                        size={14}
                                        className="animate-spin"
                                    />
                                ) : (
                                    <CheckCircle2 size={14} />
                                )}

                                {checkingTestId === t.id
                                    ? 'Checking…'
                                    : 'Check readiness'}
                              </button>

                              {/* Download Java file */}
                              <button
                                  type="button"
                                  onClick={() => handleDownload(t)}
                                  className="inline-flex shrink-0 items-center gap-2 rounded-lg border border-sand-line bg-ivory-card px-3 py-2 text-xs font-medium text-plum transition-colors hover:bg-sand"
                                  title="Download generated Java test"
                              >
                                <Download size={14} />
                                Download .java
                              </button>

                            </div>
                        )}
                      </div>
                  )
                })}

                {/* No matching tests */}
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