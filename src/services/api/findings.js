import { apiRequest } from './client'

export const getFindingsByReview = (reviewId) =>
    apiRequest(`/api/reviews/${reviewId}/findings`, {
        method: 'GET',
    })

export const createFinding = (reviewId, finding) =>
    apiRequest(`/api/reviews/${reviewId}/findings`, {
        method: 'POST',
        body: finding,
    })