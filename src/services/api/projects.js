
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