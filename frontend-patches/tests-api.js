import { apiRequest } from './client'

/**
 * Get previously generated test stubs for a review.
 * Returns [] if no tests have been generated yet.
 */
export const getTests = (reviewId) =>
  apiRequest(`/api/reviews/${reviewId}/tests`, { method: 'GET' })

/**
 * Generate (or re-generate) mock test stubs from a COMPLETED review's findings.
 * Returns HTTP 400 if the review is not yet COMPLETED.
 *
 * [MOCK] — stubs are not executed; they are template skeletons only.
 */
export const generateTests = (reviewId) =>
  apiRequest(`/api/reviews/${reviewId}/tests/generate`, { method: 'POST' })
