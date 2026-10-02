const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() ?? ''

/** The configured API base URL, with trailing slashes removed. */
export const API_BASE_URL = configuredBaseUrl.replace(/\/+$/, '')

/** HTTP error returned by the shared API request helper. */
export class ApiError extends Error {
  constructor(message, { status, data } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.data = data
  }
}

/** Network-level error (no HTTP response received). */
export class NetworkError extends Error {
  constructor(cause) {
    super(
      'Could not reach the server. Please check your internet connection and try again.'
    )
    this.name = 'NetworkError'
    this.cause = cause
  }
}

function buildUrl(path) {
  if (typeof path !== 'string' || path.length === 0) {
    throw new TypeError('An API path or URL is required.')
  }

  if (/^https?:\/\//i.test(path)) return path
  if (!API_BASE_URL) return path

  return `${API_BASE_URL}/${path.replace(/^\/+/, '')}`
}

/**
 * Send a request to a caller-supplied path.
 *
 * credentials: 'include' is required for session-based auth so the browser
 * sends the JSESSIONID cookie with cross-origin requests to the Spring backend.
 *
 * Throws:
 *  - NetworkError  if the request could not reach the server at all
 *  - ApiError      if the server returned a non-2xx status
 */
export async function apiRequest(path, options = {}) {
  const { headers: suppliedHeaders, body, ...requestOptions } = options
  const headers = new Headers(suppliedHeaders)
  headers.set('Accept', headers.get('Accept') ?? 'application/json')

  let requestBody = body
  const canSerializeBody =
    body != null &&
    typeof body === 'object' &&
    !(body instanceof FormData) &&
    !(body instanceof Blob) &&
    !(body instanceof ArrayBuffer)

  if (canSerializeBody) {
    requestBody = JSON.stringify(body)
    if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(buildUrl(path), {
      ...requestOptions,
      headers,
      credentials: 'include',
      ...(requestBody === undefined ? {} : { body: requestBody }),
    })
  } catch (fetchError) {
    // TypeError from fetch = network-level failure (offline, DNS, CORS preflight blocked)
    throw new NetworkError(fetchError)
  }

  if (response.status === 204) return null

  const contentType = response.headers.get('content-type') ?? ''
  const data = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message =
      (data && typeof data === 'object' && (data.message || data.error)) ||
      (typeof data === 'string' && data) ||
      `API request failed with status ${response.status}`
    throw new ApiError(message, { status: response.status, data })
  }

  return data
}
