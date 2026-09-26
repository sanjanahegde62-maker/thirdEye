import { useState, useMemo, useEffect } from 'react'
import { ChevronDown, Lightbulb } from 'lucide-react'
import { useOutletContext } from 'react-router-dom'
import SeverityBadge from '../components/SeverityBadge'
import { getReviewsByProject } from '../services/api/reviews'
import { getFindingsByReview } from '../services/api/findings'

const severityOrder = ['critical', 'high', 'medium', 'low']
const categories = ['All', 'Security', 'Bugs', 'Quality', 'Coverage']

const severityBorder = {
    critical: 'border-l-coral',
    high: 'border-l-terracotta',
    medium: 'border-l-sand-line',
    low: 'border-l-sand-line',
}

export default function Findings() {
    const { activeProject } = useOutletContext()
    const [reviews, setReviews] = useState([])
    const [selectedReviewId, setSelectedReviewId] = useState('')
    const [findings, setFindings] = useState([])
    const [severityFilter, setSeverityFilter] = useState('all')
    const [category, setCategory] = useState('All')
    const [expanded, setExpanded] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')

    useEffect(() => {
        if (!activeProject?.id) return

        const loadReviews = async () => {
            try {
                setLoading(true)
                setError('')
                setFindings([])
                setSelectedReviewId('')

                const data = await getReviewsByProject(activeProject.id)
                setReviews(data)

                if (data.length > 0) {
                    setSelectedReviewId(String(data[0].id))
                }
            } catch (err) {
                setError(err.message || 'Failed to load reviews')
            } finally {
                setLoading(false)
            }
        }

        loadReviews()
    }, [activeProject?.id])

    useEffect(() => {
        if (!selectedReviewId) {
            setFindings([])
            return
        }

        const loadFindings = async () => {
            try {
                setLoading(true)
                setError('')

                const data = await getFindingsByReview(selectedReviewId)
                setFindings(data)
                setExpanded(data.length > 0 ? data[0].id : null)
            } catch (err) {
                setError(err.message || 'Failed to load findings')
                setFindings([])
            } finally {
                setLoading(false)
            }
        }

        loadFindings()
    }, [selectedReviewId])

    const severityCounts = useMemo(
        () =>
            findings.reduce(
                (counts, finding) => {
                    const severity = finding.severity?.toLowerCase()
                    counts[severity] = (counts[severity] || 0) + 1
                    return counts
                },
                {}
            ),
        [findings]
    )

    const filtered = useMemo(
        () =>
            findings.filter(
                (f) =>
                    (severityFilter === 'all' ||
                        f.severity?.toLowerCase() === severityFilter) &&
                    (category === 'All' ||
                        f.category?.toLowerCase() === category.toLowerCase())
            ),
        [findings, severityFilter, category]
    )

    return (
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
            <div>
                <h1 className="font-serif text-2xl font-semibold text-plum">
                    Findings
                </h1>
                <p className="mt-1 text-sm text-plum/60">
                    {findings.length} issues found across the changeset
                </p>
            </div>

            {reviews.length > 0 && (
                <div>
                    <label className="mb-2 block text-sm font-medium text-plum">
                        Select review
                    </label>
                    <select
                        value={selectedReviewId}
                        onChange={(e) => setSelectedReviewId(e.target.value)}
                        className="w-full rounded-xl border border-sand-line bg-ivory-card px-4 py-3 text-sm text-plum focus:outline-none"
                    >
                        {reviews.map((review) => (
                            <option key={review.id} value={String(review.id)}>
                                {review.title}
                            </option>
                        ))}
                    </select>
                </div>
            )}

            <div className="flex flex-wrap items-center gap-2.5">
                <button
                    onClick={() => setSeverityFilter('all')}
                    className={`rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors ${
                        severityFilter === 'all'
                            ? 'border-plum bg-plum text-ivory-100'
                            : 'border-sand-line bg-ivory-card text-plum/60 hover:text-plum'
                    }`}
                >
                    All ({findings.length})
                </button>

                {severityOrder.map((sev) => (
                    <button
                        key={sev}
                        onClick={() => setSeverityFilter(sev)}
                        className={`rounded-full border px-3.5 py-1.5 text-sm font-medium capitalize transition-colors ${
                            severityFilter === sev
                                ? 'border-plum bg-plum text-ivory-100'
                                : 'border-sand-line bg-ivory-card text-plum/60 hover:text-plum'
                        }`}
                    >
                        {sev} ({severityCounts[sev] || 0})
                    </button>
                ))}

                <div className="ml-auto">
                    <select
                        value={category}
                        onChange={(e) => setCategory(e.target.value)}
                        className="rounded-full border border-sand-line bg-ivory-card px-3.5 py-1.5 text-sm font-medium text-plum focus:outline-none"
                    >
                        {categories.map((c) => (
                            <option key={c} value={c}>
                                {c} category
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {loading && (
                <p className="py-6 text-center text-sm text-plum/60">
                    Loading findings...
                </p>
            )}

            {error && (
                <p className="rounded-lg bg-coral/10 p-4 text-sm text-coral">
                    {error}
                </p>
            )}

            {!loading && !error && reviews.length === 0 && (
                <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
                    No reviews found for this project. Submit a code review first.
                </div>
            )}

            {!loading && !error && reviews.length > 0 && (
                <div className="flex flex-col gap-3">
                    {filtered.map((f) => {
                        const isOpen = expanded === f.id
                        const severity = f.severity?.toLowerCase()

                        return (
                            <div
                                key={f.id}
                                className={`overflow-hidden rounded-xl2 border border-l-4 border-sand-line bg-ivory-card shadow-soft ${
                                    severityBorder[severity] || 'border-l-sand-line'
                                }`}
                            >
                                <button
                                    onClick={() => setExpanded(isOpen ? null : f.id)}
                                    className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left"
                                >
                                    <div className="flex min-w-0 items-center gap-3.5">
                                        <SeverityBadge severity={severity} />
                                        <div className="min-w-0">
                                            <p className="truncate text-sm font-medium text-plum">
                                                {f.title}
                                            </p>
                                            <p className="truncate font-mono text-xs text-plum/50">
                                                {f.file}:{f.line} · {f.category}
                                            </p>
                                        </div>
                                    </div>

                                    <ChevronDown
                                        size={16}
                                        className={`shrink-0 text-plum/40 transition-transform ${
                                            isOpen ? 'rotate-180' : ''
                                        }`}
                                    />
                                </button>

                                {isOpen && (
                                    <div className="border-t border-sand-line px-5 py-4">
                                        <p className="text-sm leading-relaxed text-plum/75">
                                            {f.description}
                                        </p>

                                        <div className="mt-3 flex items-start gap-2 rounded-lg bg-plum-50 px-3.5 py-3">
                                            <Lightbulb
                                                size={15}
                                                className="mt-0.5 shrink-0 text-plum/60"
                                            />
                                            <p className="text-sm leading-relaxed text-plum/80">
                                                {f.suggestion}
                                            </p>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )
                    })}

                    {filtered.length === 0 && (
                        <div className="rounded-xl2 border border-dashed border-sand-line bg-ivory-card px-5 py-10 text-center text-sm text-plum/50">
                            No findings match these filters.
                        </div>
                    )}
                </div>
            )}
        </div>
    )
}