
import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'
import { getProjects } from '../services/api/projects'

export default function Dashboard() {
    const [collapsed, setCollapsed] = useState(false)
    const [projects, setProjects] = useState([])
    const [activeProject, setActiveProject] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        async function fetchProjects() {
            try {
                const data = await getProjects()

                setProjects(data)

                if (data.length > 0) {
                    setActiveProject(data[0])
                }
            } catch (err) {
                setError(err.message || 'Failed to load projects')
            } finally {
                setLoading(false)
            }
        }

        fetchProjects()
    }, [])

    if (loading) {
        return (
            <div className="flex h-screen items-center justify-center bg-ivory-100">
                Loading projects...
            </div>
        )
    }

    if (error) {
        return (
            <div className="flex h-screen items-center justify-center bg-ivory-100">
                <p className="text-red-600">
                    Could not load projects: {error}
                </p>
            </div>
        )
    }

    if (!activeProject) {
        return (
            <div className="flex h-screen items-center justify-center bg-ivory-100">
                No projects found. Please add a project first.
            </div>
        )
    }

    return (
        <div className="flex h-screen w-full overflow-hidden bg-ivory-100">
            <Sidebar
                collapsed={collapsed}
                onToggle={() => setCollapsed((c) => !c)}
            />

            <div className="flex flex-1 flex-col overflow-hidden">
                <Topbar
                    projects={projects}
                    activeProject={activeProject}
                    setActiveProject={setActiveProject}
                />

                <main className="flex-1 overflow-y-auto px-6 py-7 md:px-9">
                    <Outlet context={{ activeProject }} />
                </main>
            </div>
        </div>
    )
}