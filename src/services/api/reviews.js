import { apiRequest } from './client'

export const getReviewsByProject = (projectId) =>
    apiRequest(`/api/projects/${projectId}/reviews`, {
        method: 'GET'
    })

export const getReviewById = (reviewId) =>
    apiRequest(`/api/reviews/${reviewId}`, {
        method: 'GET'
    })

export const createReview = (projectId, review) =>
    apiRequest(`/api/projects/${projectId}/reviews`, {
        method: 'POST',
        body: review
    })

export const getReviewDiff = (reviewId) =>
    apiRequest(`/api/reviews/${reviewId}/diff`, {
        method: 'GET'
    })

export const triggerAnalysis = (reviewId) =>
    apiRequest(`/api/reviews/${reviewId}/analyze`, { method: 'POST' })