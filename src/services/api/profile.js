import { apiRequest } from './client'

/** GET /api/profile */
export const getProfile = () =>
  apiRequest('/api/profile', { method: 'GET' })

/**
 * PATCH /api/profile
 * @param {{ name?: string, email?: string }} fields
 */
export const updateProfile = (fields) =>
  apiRequest('/api/profile', { method: 'PATCH', body: fields })
