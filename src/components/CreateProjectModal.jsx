import { useState, useEffect, useRef } from 'react'
import { X, GitBranch, Loader2 } from 'lucide-react'
import { importProject } from '../services/api/projects'

/**
 * Modal for creating / importing a project from a GitHub repository URL.
 *
 * Props:
 *   onClose()           - called when the user dismisses the modal (no project created)
 *   onProjectCreated(p) - called with the newly-created Project object
 */
export default function CreateProjectModal({ onClose, onProjectCreated }) {
    const [name, setName]       = useState('')
    const [repoUrl, setRepoUrl] = useState('')
    const [desc, setDesc]       = useState('')
    const [error, setError]     = useState('')
    const [saving, setSaving]   = useState(false)

    const nameRef = useRef(null)

    // Auto-focus the name field when the modal opens
    useEffect(() => { nameRef.current?.focus() }, [])

    // Close on Escape key
    useEffect(() => {
        const handler = (e) => { if (e.key === 'Escape') onClose() }
        document.addEventListener('keydown', handler)
        return () => document.removeEventListener('keydown', handler)
    }, [onClose])

    async function handleSubmit(e) {
        e.preventDefault()
        setError('')

        if (!name.trim()) {
            setError('Project name is required.')
            nameRef.current?.focus()
            return
        }
        if (!repoUrl.trim()) {
            setError('GitHub repository URL is required.')
            return
        }

        setSaving(true)
        try {
            const project = await importProject(name.trim(), repoUrl.trim(), desc.trim() || null)
            onProjectCreated(project)
        } catch (err) {
            setError(err.message || 'Failed to create project. Please try again.')
        } finally {
            setSaving(false)
        }
    }

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-plum/20 px-4 backdrop-blur-sm"
            onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-project-title"
        >
            <div className="w-full max-w-lg rounded-xl2 border border-sand-line bg-ivory-card shadow-lifted">

                <div className="flex items-center justify-between border-b border-sand-line px-6 py-4">
                    <div className="flex items-center gap-2.5">
                        <GitBranch size={18} className="text-plum/60" />
                        <h2 id="create-project-title" className="font-serif text-lg font-semibold text-plum">
                            Import GitHub project
                        </h2>
                    </div>
                    <button
                        onClick={onClose}
                        aria-label="Close"
                        className="flex h-8 w-8 items-center justify-center rounded-lg text-plum/40 transition-colors hover:bg-sand/60 hover:text-plum"
                    >
                        <X size={16} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} noValidate>
                    <div className="space-y-4 px-6 py-5">

                        <div>
                            <label htmlFor="cp-name" className="block text-sm font-medium text-plum">
                                Project name
                            </label>
                            <input
                                ref={nameRef}
                                id="cp-name"
                                type="text"
                                value={name}
                                onChange={e => setName(e.target.value)}
                                maxLength={160}
                                placeholder="My Awesome App"
                                className="mt-1.5 w-full rounded-lg border border-sand-line bg-white px-3 py-2 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 focus:ring-1 focus:ring-plum/20"
                            />
                        </div>

                        <div>
                            <label htmlFor="cp-repo" className="block text-sm font-medium text-plum">
                                GitHub repository URL
                            </label>
                            <input
                                id="cp-repo"
                                type="url"
                                value={repoUrl}
                                onChange={e => setRepoUrl(e.target.value)}
                                placeholder="https://github.com/owner/repo"
                                className="mt-1.5 w-full rounded-lg border border-sand-line bg-white px-3 py-2 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 focus:ring-1 focus:ring-plum/20"
                            />
                            <p className="mt-1 text-xs text-plum/45">
                                Must be a valid GitHub HTTPS URL.
                            </p>
                        </div>

                        <div>
                            <label htmlFor="cp-desc" className="block text-sm font-medium text-plum">
                                Description <span className="font-normal text-plum/45">(optional)</span>
                            </label>
                            <textarea
                                id="cp-desc"
                                value={desc}
                                onChange={e => setDesc(e.target.value)}
                                maxLength={500}
                                rows={3}
                                placeholder="A short description of what this project does."
                                className="mt-1.5 w-full resize-none rounded-lg border border-sand-line bg-white px-3 py-2 text-sm text-plum placeholder:text-plum/30 outline-none focus:border-plum/50 focus:ring-1 focus:ring-plum/20"
                            />
                        </div>

                        {error && (
                            <p role="alert" className="rounded-lg bg-coral/10 px-3 py-2 text-sm text-coral">
                                {error}
                            </p>
                        )}
                    </div>

                    <div className="flex items-center justify-end gap-3 border-t border-sand-line px-6 py-4">
                        <button
                            type="button"
                            onClick={onClose}
                            className="rounded-lg border border-sand-line px-4 py-2 text-sm font-medium text-plum/70 transition-colors hover:bg-sand/60 hover:text-plum"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={saving}
                            className="flex items-center gap-2 rounded-lg bg-plum px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-plum/90 disabled:opacity-60"
                        >
                            {saving ? 'Creating...' : 'Create project'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}
