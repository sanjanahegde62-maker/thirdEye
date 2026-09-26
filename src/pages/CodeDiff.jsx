import { useState, useEffect } from 'react'
import { useOutletContext } from 'react-router-dom'
import { FileCode2, Plus, Minus, FilePlus } from 'lucide-react'
import { createReview, getReviewsByProject } from '../services/api/reviews'

const lineStyle = {
  add: 'bg-plum-50/70 border-l-2 border-plum/40',
  del: 'bg-terracotta-soft/40 border-l-2 border-terracotta/50',
  context: 'border-l-2 border-transparent',
}

const marker = { add: '+', del: '−', context: '' }
const markerColor = {
  add: 'text-plum',
  del: 'text-terracotta-deep',
  context: 'text-plum/25',
}

export default function CodeDiff() {
  const { activeProject } = useOutletContext()

  const [reviews, setReviews] = useState([])
  const [selectedReview, setSelectedReview] = useState(null)
  const [selected, setSelected] = useState('')
  const [title, setTitle] = useState('')
  const [codeDiff, setCodeDiff] = useState('')
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    if (!activeProject?.id) return

    const loadReviews = async () => {
      setLoading(true)
      setError('')
      setSelectedReview(null)
      setSelected('')
      setSuccess('')

      try {
        const data = await getReviewsByProject(activeProject.id)
        setReviews(data)

        if (data.length > 0) {
          setSelectedReview(data[0])
          setSelected(data[0].title)
          setTitle(data[0].title)
          setCodeDiff(data[0].codeDiff || '')
        } else {
          setTitle('')
          setCodeDiff('')
        }
      } catch (err) {
        setError(err.message || 'Failed to load reviews')
      } finally {
        setLoading(false)
      }
    }

    loadReviews()
  }, [activeProject?.id])

  const handleSubmit = async (e) => {
    e.preventDefault()

    if (!activeProject?.id || !title.trim() || !codeDiff.trim()) {
      setError('Please enter a review title and code diff.')
      return
    }

    setSubmitting(true)
    setError('')
    setSuccess('')

    try {
      const review = await createReview(activeProject.id, {
        title: title.trim(),
        codeDiff,
        filesChanged: 1,
        linesAdded: codeDiff.split('\n').filter(line => line.startsWith('+') && !line.startsWith('+++')).length,
        linesRemoved: codeDiff.split('\n').filter(line => line.startsWith('-') && !line.startsWith('---')).length,
        commits: 1,
      })

      setReviews(prev => [review, ...prev])
      setSelectedReview(review)
      setSelected(review.title)
      setSuccess(`Review created successfully. Review ID: ${review.id}`)
    } catch (err) {
      setError(err.message || 'Failed to create review')
    } finally {
      setSubmitting(false)
    }
  }

  if (!activeProject) {
    return <p className="text-plum/60">Select a project to continue.</p>
  }

  return (
      <div className="mx-auto flex max-w-6xl flex-col gap-6">
        <div>
          <h1 className="font-serif text-2xl font-semibold text-plum">
            Code diff
          </h1>
          <p className="mt-1 text-sm text-plum/60">
            {activeProject.name}
          </p>
        </div>

        <form
            onSubmit={handleSubmit}
            className="flex flex-col gap-4 rounded-xl2 border border-sand-line bg-ivory-card p-5 shadow-soft"
        >
          <h2 className="font-serif text-lg font-semibold text-plum">
            Submit code for review
          </h2>

          <input
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="Review title"
              className="rounded-lg border border-sand-line bg-ivory px-4 py-3 text-sm text-plum outline-none focus:border-plum/40"
              required
          />

          <textarea
              value={codeDiff}
              onChange={e => setCodeDiff(e.target.value)}
              placeholder="Paste your code diff here..."
              rows={10}
              className="w-full resize-y rounded-lg border border-sand-line bg-ivory px-4 py-3 font-mono text-sm text-plum outline-none focus:border-plum/40"
              required
          />

          <button
              type="submit"
              disabled={submitting}
              className="w-fit rounded-lg bg-plum px-5 py-2.5 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {submitting ? 'Submitting...' : 'Submit for review'}
          </button>

          {error && (
              <p className="text-sm text-terracotta-deep">{error}</p>
          )}

          {success && (
              <p className="text-sm text-plum">{success}</p>
          )}
        </form>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[280px_1fr]">
          <div className="h-fit overflow-hidden rounded-xl2 border border-sand-line bg-ivory-card shadow-soft">
            <div className="border-b border-sand-line px-4 py-3 text-xs font-medium text-plum/50">
              Reviews
            </div>

            {loading ? (
                <p className="p-4 text-sm text-plum/60">Loading reviews...</p>
            ) : reviews.length === 0 ? (
                <p className="p-4 text-sm text-plum/60">
                  No reviews yet. Submit a code diff to get started.
                </p>
            ) : (
                <div className="flex flex-col">
                  {reviews.map(review => (
                      <button
                          key={review.id}
                          onClick={() => {
                            setSelectedReview(review)
                            setSelected(review.title)
                            setTitle(review.title)
                            setCodeDiff(review.codeDiff || '')
                            setSuccess('')
                            setError('')
                          }}
                          className={`flex items-center gap-2.5 border-b border-sand-line/70 px-4 py-3 text-left transition-colors last:border-b-0 ${
                              selectedReview?.id === review.id
                                  ? 'bg-sand/50'
                                  : 'hover:bg-sand/25'
                          }`}
                      >
                        <FileCode2
                            size={15}
                            className="shrink-0 text-plum/50"
                        />

                        <span className="min-w-0 flex-1 truncate text-xs text-plum">
                    {review.title}
                  </span>

                        <span className="text-[10px] text-plum/50">
                    #{review.id}
                  </span>
                      </button>
                  ))}
                </div>
            )}
          </div>

          <div className="overflow-hidden rounded-xl2 border border-sand-line bg-ivory-card shadow-soft">
            <div className="flex items-center justify-between border-b border-sand-line bg-peach-soft/60 px-5 py-3">
            <span className="font-mono text-sm text-plum">
              {selected || 'No review selected'}
            </span>

              {selectedReview && (
                  <span className="rounded-full bg-sand px-3 py-1 text-xs text-plum">
                {selectedReview.status}
              </span>
              )}
            </div>

            {selectedReview ? (
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse font-mono text-[13px]">
                    <tbody>
                    {selectedReview.codeDiff
                        ?.split('\n')
                        .map((text, i) => {
                          const type = text.startsWith('+')
                              ? 'add'
                              : text.startsWith('-')
                                  ? 'del'
                                  : 'context'

                          return (
                              <tr key={i} className={lineStyle[type]}>
                                <td className="w-10 select-none px-2 py-0.5 text-right text-plum/30">
                                  {type === 'del' ? i + 1 : ''}
                                </td>

                                <td className="w-10 select-none px-2 py-0.5 text-right text-plum/30">
                                  {type === 'add' ? i + 1 : ''}
                                </td>

                                <td className={`w-5 select-none py-0.5 text-center font-semibold ${markerColor[type]}`}>
                                  {marker[type]}
                                </td>

                                <td className="whitespace-pre px-3 py-0.5 text-plum/90">
                                  {text || '\u00A0'}
                                </td>
                              </tr>
                          )
                        })}
                    </tbody>
                  </table>
                </div>
            ) : (
                <div className="p-6 text-sm text-plum/50">
                  Submit a code diff or select an existing review.
                </div>
            )}
          </div>
        </div>
      </div>
  )
}