
import { apiRequest } from './client'

// Fetch all projects from the Spring Boot backend
export const getProjects = () => {
    return apiRequest('/api/projects', {
        method: 'GET',
    })
}

// Fetch one project by its ID
export const getProjectById = (id) => {
    return apiRequest(`/api/projects/${id}`, {
        method: 'GET',
    })
}

// Create a new project
export const createProject = (project) => {
    return apiRequest('/api/projects', {
        method: 'POST',
        body: project,
    })
}

// Import a project from a GitHub repository URL.
// Validates the URL on the backend and checks for duplicates per user.
export const importProject = (name, repositoryUrl, description = null) => {
    return apiRequest('/api/projects/import', {
        method: 'POST',
        body: { name, repositoryUrl, description },
    })
}
