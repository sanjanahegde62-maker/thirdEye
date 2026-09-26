import { apiRequest } from './client'

export function login(email, password) {
  return apiRequest('/api/auth/login', {
    method: 'POST',
    body: { email, password },
  })
}

export function register(fullName, email, password) {
  return apiRequest('/api/auth/register', {
    method: 'POST',
    body: { fullName, email, password, confirmPassword: password },
  })
}

export function logout() {
  return apiRequest('/api/auth/logout', { method: 'POST' })
}

export function getMe() {
  return apiRequest('/api/auth/me')
}
