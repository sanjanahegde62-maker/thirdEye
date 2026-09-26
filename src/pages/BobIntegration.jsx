import { useState, useEffect, useCallback } from 'react'
import { useOutletContext } from 'react-router-dom'
import {
  Bot,
  Loader2,
  CheckCircle2,
  AlertCircle,
  ChevronDown,
  ChevronRight,
  Play,
  RefreshCw,
  ExternalLink,
  Terminal,
  Database,
  Cpu,
  Zap,
  ShieldAlert,
  FlaskConical,
  Info,
} from 'lucide-react'
import SeverityBadge from '../components/SeverityBadge'
import { getProjects } from '../services/api/projects'
import { getReviewsByProject } from '../services/api/reviews'
import { getFindingsByReview } from '../services/api/findings'
import { getTests, generateTests } from '../services/api/tests'
import { apiRequest } from '../services/api/client'

// ── Step configuration ─────────────────────────────────────────────────────────

const STEPS = [
  { id: 'projects',   label: 'List projects',          tool: 'list_projects',        icon: Database   },
  { id: 'reviews',    label: 'List reviews',           tool: 'list_reviews',         icon: Terminal   },
  { id: 'review',     label: 'Get review details',     tool: 'get_review',           icon: Cpu        },
  { id: 'findings',   label: 'Get findings',           tool: 'get_findings',         icon: ShieldAlert },
  { id: 'summary',    label: 'Findings summary',       tool: 'get_findings_summary', icon: Zap        },
  { id: 'tests',      label: 'Get generated tests',    tool: 'get_tests',            icon: FlaskConical },
]

// ── Helpers ────────────────────────────────────────────────────────────────────

function StepBadge({ status }) {
  if (status === 'running') return (
    <span className="flex items-center gap-1.5 rounded-full bg-coral-soft px-2.5 py-0.5 text-[11px] font-medium text-coral-deep">
      <Loader2 size={10} className="animate-spin" /> Running
    </span>
  )
  if (status === 'done') return (
    <span className="flex items-center gap-1.5 rounded-full bg-plum/10 px-2.5 py-0.5 text-[11px] font-medium text-plum">
      <CheckCircle2 size={10} /> Done
    </span>
  )
  if (status === 'error') return (
    <span className="flex items-center gap-1.5 rounded-full bg-coral/10 px-2.5 py-0.5 text-[11px] font-medium text-coral-deep">
      <AlertCircle size={10} /> Error
    </span>
  )
  return (
    <span className="rounded-full bg-sand px-2.5 py-0.5 text-[11px] text-plum/40">
      Pending
    </span>
  )
}

function ToolCallCard({ step, result, status, expanded, onToggle }) {
  const Icon = step.icon
  return (
    <div className={`rounded-xl2 border bg-ivory-card shadow-soft transition-colors ${
      status === 'done'    ? 'border-plum/15'    :
      status === 'running' ? 'border-coral/30'   :
      status === 'error'   ? 'border-coral/25'   :
      'border-sand-line opacity-50'
    }`}>
      <button
        onClick={onToggle}
        disabled={status === 'idle'}
        className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left disabled:cursor-default"
      >
        <div className="flex items-center gap-3.5 min-w-0">
          <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
            status === 'done'    ? 'bg-plum text-white'      :
            status === 'running' ? 'bg-coral text-white'     :
            status === 'error'   ? 'bg-coral/20 text-coral'  :
            'bg-sand text-plum/30'
          }`}>
            {status === 'running'
              ? <Loader2 size={15} className="animate-spin" />
              : <Icon size={15} />}
          </span>
          <div className="min-w-0">
            <p className="text-sm font-medium text-plum">{step.label}</p>
            <p className="font-mono text-[11px] text-plum/45">{step.tool}()</p>
          </div>
        </div>
        <div className="flex items-center gap-2.5 shrink-0">
          <StepBadge status={status} />
          {status !== 'idle' && (
            expanded
              ? <ChevronDown size={15} className="text-plum/40" />
              : <ChevronRight size={15} className="text-plum/40" />
          )}
        </div>
      </button>

      {expanded && status !== 'idle' && result !== undefined && (
        <div className="border-t border-sand-line px-5 py-4">
          {status === 'error' ? (
            <p className="rounded-lg bg-coral/10 p-3 font-mono text-xs text-coral-deep">{String(result)}</p>
          ) : (
            <pre className="overflow-x-auto rounded-lg bg-plum-50 p-3.5 font-mono text-xs text-plum/80 whitespace-pre-wrap max-h-72">
              {typeof result === 'string' ? result : JSON.stringify(result, null, 2)}
            </pre>
          )}
        </div>
      )}
    </div>
  )
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function BobIntegration() {
  const { activeProject } = useOutletContext()

  // Workflow state
  const [running,   setRunning]   = useState(false)
  const [stepsDone, setStepsDone] = useState({})         // stepId → 'done'|'error'|'running'
  const [results,   setResults]   = useState({})         // stepId → data
  const [expanded,  setExpanded]  = useState({})         // stepId → bool
  const [demoError, setDemoError] = useState('')

  // Derived data (shown in the summary panels)
  const [projects,  setProjects]  = useState(null)
  const [reviews,   setReviews]   = useState(null)
  const [review,    setReview]    = useState(null)
  const [findings,  setFindings]  = useState(null)
  const [summary,   setSummary]   = useState(null)
  const [tests,     setTests]     = useState(null)

  // Test generation state
  const [generating,   setGenerating]   = useState(false)
  const [genResult,    setGenResult]    = useState(null)
  const [genError,     setGenError]     = useState('')

  const setStep = useCallback((id, status) => {
    setStepsDone(prev => ({ ...prev, [id]: status }))
    if (status === 'running') {
      setExpanded(prev => ({ ...prev, [id]: true }))
    }
  }, [])

  const setResult = useCallback((id, data) => {
    setResults(prev => ({ ...prev, [id]: data }))
  }, [])

  // Reset on project change
  useEffect(() => {
    setStepsDone({})
    setResults({})
    setExpanded({})
    setProjects(null); setReviews(null); setReview(null)
    setFindings(null); setSummary(null); setTests(null)
    setDemoError(''); setGenResult(null); setGenError('')
  }, [activeProject?.id])

  // ── Run the full Bob workflow ────────────────────────────────────────────────

  const runWorkflow = useCallback(async () => {
    if (!activeProject?.id || running) return
    setRunning(true)
    setDemoError('')
    setStepsDone({})
    setResults({})
    setExpanded({})
    setProjects(null); setReviews(null); setReview(null)
    setFindings(null); setSummary(null); setTests(null)
    setGenResult(null); setGenError('')

    try {
      // Step 1: list_projects
      setStep('projects', 'running')
      const projectList = await getProjects()
      setResult('projects', projectList)
      setProjects(projectList)
      setStep('projects', 'done')

      // Step 2: list_reviews
      setStep('reviews', 'running')
      const reviewList = await getReviewsByProject(activeProject.id)
      setResult('reviews', reviewList)
      setReviews(reviewList)
      setStep('reviews', 'done')

      if (!reviewList || reviewList.length === 0) {
        setDemoError('No reviews found for this project. Submit a code diff first.')
        setRunning(false)
        return
      }

      // Use the highest-ID COMPLETED review
      const completed = reviewList
        .filter(r => r.status === 'COMPLETED')
        .sort((a, b) => b.id - a.id)
      const target = completed[0] ?? reviewList.sort((a, b) => b.id - a.id)[0]

      // Step 3: get_review
      setStep('review', 'running')
      const reviewDetail = await apiRequest(`/api/reviews/${target.id}`, { method: 'GET' })
      setResult('review', reviewDetail)
      setReview(reviewDetail)
      setStep('review', 'done')

      // Step 4: get_findings
      setStep('findings', 'running')
      const findingList = await getFindingsByReview(target.id)
      setResult('findings', findingList)
      setFindings(findingList)
      setStep('findings', 'done')

      // Step 5: get_findings_summary
      setStep('summary', 'running')
      const summaryData = await apiRequest(`/api/reviews/${target.id}/findings/summary`, { method: 'GET' })
      setResult('summary', summaryData)
      setSummary(summaryData)
      setStep('summary', 'done')

      // Step 6: get_tests
      setStep('tests', 'running')
      const testList = await apiRequest(`/api/reviews/${target.id}/tests`, { method: 'GET' })
      setResult('tests', testList)
      setTests(testList)
      setStep('tests', 'done')

    } catch (e) {
      setDemoError(e?.message || String(e))
    } finally {
      setRunning(false)
    }
  }, [activeProject?.id, running, setStep, setResult])

  const handleGenerateTests = async () => {
    if (!review?.id) return
    setGenerating(true)
    setGenError('')
    setGenResult(null)
    try {
      const result = await generateTests(review.id)
      setGenResult(result)
      setTests(result)
    } catch (e) {
      setGenError(e?.message || String(e))
    } finally {
      setGenerating(false)
    }
  }

  const isComplete = STEPS.every(s => stepsDone[s.id] === 'done')

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-7">

      {/* Page header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-plum text-white shadow-soft">
            <Bot size={22} />
          </div>
          <div>
            <h1 className="font-serif text-2xl font-semibold text-plum">IBM Bob Integration</h1>
            <p className="mt-0.5 text-sm text-plum/60">
              ThirdEye AI ↔ IBM Bob via Model Context Protocol (MCP)
            </p>
          </div>
        </div>

        <button
          onClick={runWorkflow}
          disabled={running}
          className="inline-flex items-center gap-2 rounded-xl bg-plum px-5 py-2.5 text-sm font-medium text-white shadow-soft transition-colors hover:bg-plum/90 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {running
            ? <><Loader2 size={15} className="animate-spin" /> Running workflow…</>
            : isComplete
              ? <><RefreshCw size={15} /> Re-run workflow</>
              : <><Play size={15} /> Run Bob workflow</>}
        </button>
      </div>

      {/* Architecture callout */}
      <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
        <h2 className="mb-3 text-sm font-semibold text-plum">How this integration works</h2>
        <div className="flex flex-wrap items-center gap-3 text-sm text-plum/70">
          <span className="flex items-center gap-1.5 rounded-lg bg-plum px-3 py-1.5 text-xs font-medium text-white">
            <Bot size={12} /> IBM Bob (AI assistant)
          </span>
          <ChevronRight size={14} className="text-plum/30" />
          <span className="flex items-center gap-1.5 rounded-lg bg-sand px-3 py-1.5 text-xs font-medium text-plum">
            <Terminal size={12} /> thirdeye MCP server
          </span>
          <ChevronRight size={14} className="text-plum/30" />
          <span className="flex items-center gap-1.5 rounded-lg bg-sand px-3 py-1.5 text-xs font-medium text-plum">
            <Database size={12} /> Spring Boot API (:8080)
          </span>
          <ChevronRight size={14} className="text-plum/30" />
          <span className="flex items-center gap-1.5 rounded-lg bg-sand px-3 py-1.5 text-xs font-medium text-plum">
            <Cpu size={12} /> H2 Database
          </span>
        </div>
        <p className="mt-3 text-xs text-plum/50">
          Bob invokes tools registered in the <code className="rounded bg-sand px-1 py-0.5 font-mono">thirdeye</code> MCP server.
          Each tool makes a real HTTP call to the Spring Boot backend. No AI model is involved in the analysis — the static
          analysis engine is deterministic. Bob's role is discovery, explanation, and test generation orchestration.
        </p>
      </div>

      {/* MCP tools registered */}
      <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
        <h2 className="mb-3 text-sm font-semibold text-plum">MCP tools registered in Bob</h2>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
          {[
            'list_projects', 'list_reviews', 'get_review', 'create_review',
            'trigger_analysis', 'get_findings', 'get_findings_summary',
            'get_review_diff', 'generate_tests', 'get_tests',
          ].map(tool => (
            <span key={tool} className="rounded-lg border border-sand-line bg-sand px-3 py-2 font-mono text-[11px] text-plum/70">
              {tool}()
            </span>
          ))}
        </div>
        <p className="mt-3 text-xs text-plum/45">
          These tools are live — Bob can invoke any of them from its chat panel using natural language.
        </p>
      </div>

      {/* Workflow runner */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-plum">
            Bob workflow — live tool calls against <code className="rounded bg-sand px-1 font-mono text-xs">{activeProject?.name}</code>
          </h2>
          {isComplete && (
            <span className="flex items-center gap-1.5 rounded-full bg-plum/10 px-2.5 py-0.5 text-[11px] font-medium text-plum">
              <CheckCircle2 size={10} /> All steps completed
            </span>
          )}
        </div>

        {!running && !isComplete && Object.keys(stepsDone).length === 0 && (
          <div className="mb-3 flex items-start gap-2.5 rounded-xl border border-sand-line bg-peach-soft px-4 py-3 text-xs text-plum/70">
            <Info size={14} className="mt-0.5 shrink-0 text-terracotta-deep" />
            Click <strong>Run Bob workflow</strong> above to execute all six MCP tool calls live against the backend.
            Each step shows the exact JSON response that IBM Bob would receive.
          </div>
        )}

        {demoError && (
          <div className="mb-3 rounded-lg bg-coral/10 p-3 text-sm text-coral">{demoError}</div>
        )}

        <div className="flex flex-col gap-2.5">
          {STEPS.map(step => (
            <ToolCallCard
              key={step.id}
              step={step}
              result={results[step.id]}
              status={stepsDone[step.id] ?? 'idle'}
              expanded={!!expanded[step.id]}
              onToggle={() => setExpanded(prev => ({ ...prev, [step.id]: !prev[step.id] }))}
            />
          ))}
        </div>
      </div>

      {/* Results panels (shown after workflow completes) */}
      {isComplete && (
        <>
          {/* Projects panel */}
          {projects && (
            <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
              <h2 className="mb-3 font-serif text-base font-semibold text-plum">
                Projects retrieved by Bob
              </h2>
              <div className="flex flex-col gap-2">
                {projects.map(p => (
                  <div key={p.id} className="flex items-center justify-between gap-4 rounded-lg border border-sand-line bg-sand/40 px-4 py-3">
                    <div>
                      <p className="text-sm font-medium text-plum">{p.name}</p>
                      <p className="text-xs text-plum/50">{p.description}</p>
                    </div>
                    <span className="rounded-full bg-plum/10 px-2 py-0.5 text-[11px] font-medium text-plum">
                      #{p.id} · {p.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Review detail */}
          {review && (
            <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
              <h2 className="mb-1 font-serif text-base font-semibold text-plum">
                Review retrieved by Bob
              </h2>
              <p className="mb-3 text-xs text-plum/45">
                Source: <code className="font-mono">GET /api/reviews/{review.id}</code>
              </p>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {[
                  ['Review ID', `#${review.id}`],
                  ['Title', review.title],
                  ['Status', review.status],
                  ['Progress', `${review.progress}%`],
                  ['Files changed', review.filesChanged],
                  ['Lines added', `+${review.linesAdded}`],
                  ['Lines removed', `−${review.linesRemoved}`],
                  ['Commits', review.commits],
                ].map(([label, val]) => (
                  <div key={label} className="rounded-lg border border-sand-line bg-sand/30 px-3 py-2.5">
                    <p className="text-[10px] font-medium uppercase tracking-wide text-plum/40">{label}</p>
                    <p className="mt-1 text-sm font-medium text-plum truncate">{val}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Findings panel */}
          {findings && (
            <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h2 className="font-serif text-base font-semibold text-plum">
                    Findings retrieved by Bob
                  </h2>
                  <p className="text-xs text-plum/45">
                    Source: <code className="font-mono">GET /api/reviews/{review?.id}/findings</code> · Static analysis engine (deterministic — not AI)
                  </p>
                </div>
                {summary && (
                  <div className="flex items-center gap-2 text-xs">
                    {summary.critical > 0 && <span className="rounded-full bg-coral px-2 py-0.5 font-medium text-white">{summary.critical} critical</span>}
                    {summary.high     > 0 && <span className="rounded-full bg-terracotta px-2 py-0.5 font-medium text-white">{summary.high} high</span>}
                    {summary.medium   > 0 && <span className="rounded-full bg-sand-line px-2 py-0.5 font-medium text-plum">{summary.medium} medium</span>}
                    {summary.low      > 0 && <span className="rounded-full bg-sand px-2 py-0.5 text-plum/60">{summary.low} low</span>}
                    {summary.total === 0   && <span className="text-plum/40">No findings</span>}
                  </div>
                )}
              </div>

              {findings.length === 0 ? (
                <p className="text-sm text-plum/50">No findings for this review.</p>
              ) : (
                <div className="flex flex-col gap-3">
                  {findings.map(f => (
                    <div key={f.id} className="rounded-xl border border-sand-line bg-sand/20 p-4">
                      <div className="flex items-start gap-3">
                        <SeverityBadge severity={f.severity} />
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-medium text-plum">{f.title}</p>
                          <p className="mt-0.5 font-mono text-[11px] text-plum/45">{f.file}:{f.line} · {f.category}</p>
                          <p className="mt-2 text-xs leading-relaxed text-plum/65">{f.description}</p>
                          <div className="mt-2 rounded-lg bg-plum-50 px-3 py-2 text-xs leading-relaxed text-plum/75">
                            <span className="font-semibold">Suggestion: </span>{f.suggestion}
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Tests panel */}
          {(tests !== null) && (
            <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="font-serif text-base font-semibold text-plum">
                    Generated tests via Bob
                  </h2>
                  <p className="text-xs text-plum/45">
                    Source: <code className="font-mono">GET /api/reviews/{review?.id}/tests</code> · Rule-based scaffolding (not AI-written or executed)
                  </p>
                </div>
                {review?.status === 'COMPLETED' && (
                  <button
                    onClick={handleGenerateTests}
                    disabled={generating}
                    className="inline-flex items-center gap-2 rounded-lg border border-sand-line bg-ivory-card px-3.5 py-2 text-xs font-medium text-plum transition-colors hover:bg-sand disabled:opacity-50"
                  >
                    {generating
                      ? <><Loader2 size={13} className="animate-spin" /> Generating…</>
                      : <><Play size={13} /> Generate / re-generate tests</>}
                  </button>
                )}
              </div>

              {genError && (
                <p className="mb-3 rounded-lg bg-coral/10 p-3 text-xs text-coral">{genError}</p>
              )}

              {tests.length === 0 ? (
                <p className="text-sm text-plum/50">
                  No tests generated yet.{' '}
                  {review?.status === 'COMPLETED'
                    ? 'Click "Generate / re-generate tests" to create stubs from findings.'
                    : 'Tests can only be generated for COMPLETED reviews.'}
                </p>
              ) : (
                <div className="flex flex-col gap-3">
                  {tests.map(t => (
                    <div key={t.id} className="rounded-xl border border-sand-line bg-sand/20 p-4">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-sm font-medium text-plum">{t.name}</p>
                          <p className="mt-0.5 font-mono text-[11px] text-plum/45">{t.file}</p>
                          {t.linkedFindingId && (
                            <p className="mt-1 text-xs text-plum/40">
                              Covers finding <span className="font-mono">#{t.linkedFindingId}</span>
                            </p>
                          )}
                        </div>
                        <span className="shrink-0 rounded-full bg-sand px-2 py-0.5 text-[10px] text-plum/50">
                          scaffold stub
                        </span>
                      </div>
                      {t.testCode && (
                        <details className="mt-3">
                          <summary className="cursor-pointer text-xs font-medium text-plum/50 hover:text-plum">
                            View generated code
                          </summary>
                          <pre className="mt-2 overflow-x-auto rounded-lg bg-plum-50 p-3 font-mono text-xs text-plum/80 whitespace-pre-wrap max-h-64">
                            {t.testCode}
                          </pre>
                        </details>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Bob explanation — what Bob would say */}
          {findings && findings.length > 0 && (
            <div className="rounded-xl2 border border-plum/20 bg-plum/5 p-5 shadow-soft">
              <div className="mb-3 flex items-center gap-2">
                <Bot size={16} className="text-plum" />
                <h2 className="text-sm font-semibold text-plum">
                  What IBM Bob sees — sample explanation
                </h2>
                <span className="rounded-full border border-plum/20 px-2 py-0.5 text-[10px] text-plum/50">
                  Illustrative — not a live AI response
                </span>
              </div>
              <div className="rounded-xl bg-plum p-4 text-sm leading-relaxed text-ivory-100/90 font-mono">
                <p>
                  <span className="text-coral-soft font-semibold">Bob: </span>
                  I used <code className="text-sand-line">get_findings(reviewId={review?.id})</code> to retrieve the findings
                  from the ThirdEye backend. Review <strong className="text-white">#{review?.id}</strong> — "{review?.title}" —
                  has <strong className="text-white">{findings.length} finding{findings.length !== 1 ? 's' : ''}</strong>:
                </p>
                {findings.map(f => (
                  <p key={f.id} className="mt-2 pl-4 border-l-2 border-coral/40">
                    • <strong className="text-white capitalize">[{f.severity.toUpperCase()}]</strong>{' '}
                    {f.title} in <code className="text-sand-line">{f.file}:{f.line}</code>.{' '}
                    <span className="text-ivory-100/65">{f.suggestion}</span>
                  </p>
                ))}
                <p className="mt-3 text-ivory-100/50 text-xs">
                  ⓘ This explanation is illustrative of how Bob would present these results. The actual data above is real.
                </p>
              </div>
            </div>
          )}

          {/* Judge demo guide */}
          <div className="rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft">
            <h2 className="mb-3 font-serif text-base font-semibold text-plum">
              Judge demo: full Bob workflow
            </h2>
            <ol className="flex flex-col gap-2.5 text-sm text-plum/70">
              {[
                ['Open IBM Bob', 'Open IBM Bob in VS Code or the web IDE. Confirm the "thirdeye" MCP server is listed and connected.'],
                ['Ask Bob to list projects', 'Type: "List all ThirdEye projects." Bob invokes list_projects() → you see the DayFlow and Lost & Found projects.'],
                ['Ask for the SQL Injection review', 'Type: "Get details for review 34 in the Lost & Found project." Bob calls get_review(34) and get_findings(34).'],
                ['Observe the findings', 'Bob reports: 1 HIGH severity SQL injection finding in UserService.java line 2. This matches what ThirdEye shows in the Findings page.'],
                ['Ask Bob to explain the fix', 'Type: "Explain how to fix the SQL injection in review 34." Bob reads the suggestion from get_findings() and explains PreparedStatement usage.'],
                ['Ask Bob to generate tests', 'Type: "Generate tests for review 34." Bob calls generate_tests(34) → ThirdEye creates a Java test scaffold for the SQLI-001 finding.'],
                ['View results here', 'The Generated Tests and Findings pages in ThirdEye show the same real data Bob accessed through the MCP tools.'],
              ].map(([title, desc], i) => (
                <li key={i} className="flex gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-plum text-[11px] font-semibold text-white">{i + 1}</span>
                  <div>
                    <span className="font-semibold text-plum">{title} — </span>
                    {desc}
                  </div>
                </li>
              ))}
            </ol>
            <div className="mt-4 flex items-start gap-2.5 rounded-xl border border-sand-line bg-peach-soft px-4 py-3 text-xs text-plum/60">
              <Info size={13} className="mt-0.5 shrink-0 text-terracotta-deep" />
              The workflow above uses only the <strong>existing</strong> SQL Injection Test 3 review (#34) and its finding (#34).
              No new reviews are created during this demo. The "Run Bob workflow" button above replays all six tool calls live.
            </div>
          </div>
        </>
      )}
    </div>
  )
}
