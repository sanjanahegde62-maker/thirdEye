import { apiRequest } from './client'

/** GET /api/notifications */
export const getNotifications = () =>
  apiRequest('/api/notifications', { method: 'GET' })

/** GET /api/notifications/summary */
export const getNotificationSummary = () =>
  apiRequest('/api/notifications/summary', { method: 'GET' })

/** PATCH /api/notifications/{id}/read */
export const markNotificationRead = (id) =>
  apiRequest(`/api/notifications/${id}/read`, { method: 'PATCH' })

/** PATCH /api/notifications/read-all */
export const markAllNotificationsRead = () =>
  apiRequest('/api/notifications/read-all', { method: 'PATCH' })
