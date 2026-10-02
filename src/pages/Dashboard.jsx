import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'
import { getProjects, createProject } from '../services/api/projects'
import CreateProjectModal from '../components/CreateProjectModal'

export default function Dashboard() {
    const [collapsed, setCollapsed] = useState(false)
    const [projects, setProjects] = useState([])
    const [activeProject, setActiveProject] = useState(null)
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState('')
    const [error, setError] = useState('')
    const [projectName, setProjectName] = useState('')
    const [repositoryUrl, setRepositoryUrl] = useState('')
    const [creating, setCreating] = useState(false)
    const [showCreateModal, setShowCreateModal] = useState(false)

    useEffect(() => {
        let cancelled = false
        async function fetchProjects() {
            try {
                const data = await getProjects()
                if (!Array.isArray(data)) throw new Error('The server returned an invalid project list.')
                if (!cancelled) {
                    setProjects(data)
                    setActiveProject(data[0] ?? null)
                }
            } catch (err) {
                if (!cancelled) setLoadError(err.message || 'Failed to load projects.')
            } finally {
                if (!cancelled) setLoading(false)
            }
        }
        fetchProjects()
        return () => { cancelled = true }
    }, [])

    async function handleCreateProject(event) {
        event.preventDefault()
        setError('')
        if (!projectName.trim()) {
            setError('Enter a project name.')
            return
        }
        setCreating(true)
        try {
            const project = await createProject({
                name: projectName.trim(),
                repositoryUrl: repositoryUrl.trim() || null,
                description: null,
                status: 'ACTIVE',
            })
            setProjects([project])
            setActiveProject(project)
        } catch (err) {
            setError(err.message || 'Failed to create project.')
        } finally {
            setCreating(false)
        }
    }

    if (loading) {
        return <div className="flex h-screen items-center justify-center bg-ivory-100">Loading projects...</div>
    }

    if (loadError) {
        return (
            <div className="flex h-screen flex-col items-center justify-center gap-4 bg-ivory-100 px-6 text-center">
                <p className="text-red-600">Could not load projects: {loadError}</p>
                <button className="rounded-lg bg-plum px-4 py-2 text-sm font-medium text-white" onClick={() => window.location.reload()}>
                    Retry
                </button>
            </div>
        )
    }

    if (!activeProject) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-ivory-100 px-6 py-12">
                <form onSubmit={handleCreateProject} className="w-full max-w-lg rounded-xl2 border border-sand-line bg-ivory-card p-8 shadow-soft">
                    <h1 className="font-serif text-2xl font-bold text-plum">Create your first project</h1>
                    <p className="mt-2 text-sm text-plum/65">Your projects and reviews are private to your account.</p>
                    <label className="mt-6 block text-sm font-medium text-plum" htmlFor="project-name">Project name</label>
                    <input id="project-name" value={projectName} onChange={e => setProjectName(e.target.value)} required maxLength={160}
                        className="mt-1 w-full rounded-lg border border-sand-line bg-white px-3 py-2 text-sm text-plum outline-none focus:border-plum/50" />
                    <label className="mt-4 block text-sm font-medium text-plum" htmlFor="repository-url">Repository URL <span className="font-normal text-plum/50">(optional)</span></label>
                    <input id="repository-url" type="url" value={repositoryUrl} onChange={e => setRepositoryUrl(e.target.value)}
                        className="mt-1 w-full rounded-lg border border-sand-line bg-white px-3 py-2 text-sm text-plum outline-none focus:border-plum/50" />
                    {error && <p role="alert" className="mt-4 rounded-lg bg-coral/10 px-3 py-2 text-sm text-coral">{error}</p>}
                    <button type="submit" disabled={creating} className="mt-6 w-full rounded-lg bg-plum px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60">
                        {creating ? 'Creating projectâ€¦' : 'Create project'}
                    </button>
                </form>
            </div>
        )
    }

    return (
        <div className="flex h-screen w-full overflow-hidden bg-ivory-100">
            <Sidebar collapsed={collapsed} onToggle={() => setCollapsed(c => !c)} />
            <div className="flex flex-1 flex-col overflow-hidden">
                <Topbar projects={projects} activeProject={activeProject} setActiveProject={setActiveProject} onNewProject={() => setShowCreateModal(true)} />
                {showCreateModal && (
                    <CreateProjectModal
                        onClose={() => setShowCreateModal(false)}
                        onProjectCreated={(project) => {
                            setProjects(prev => [...prev, project])
                            setActiveProject(project)
                            setShowCreateModal(false)
                        }}
                    />
                )}
                <main className="flex-1 overflow-y-auto px-6 py-7 md:px-9">
                    <Outlet context={{ activeProject }} />
                </main>
            </div>
        </div>
    )
}
