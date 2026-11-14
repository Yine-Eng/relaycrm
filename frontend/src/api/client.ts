import { ApiError } from './errors'

/**
 * The one way the app talks to the API. It:
 *   - sends the current user's X-Api-Key (set by the "View as" switcher);
 *   - sends JSON, or a JSON Merge Patch for PATCH, and If-Match when given;
 *   - turns every error response into an ApiError (problem+json parsed);
 *   - returns the response's ETag next to the data, for a later If-Match.
 *
 * Paths are relative ("/api/me"): in development the Vite server forwards
 * /api to the backend, so the browser never makes a cross-origin request.
 */

let apiKey: string | null = null

/** Called by the session when the user changes. null sends no key (the API then answers 401). */
export function setApiKey(key: string | null): void {
  apiKey = key
}

export function getApiKey(): string | null {
  return apiKey
}

export interface ApiResponse<T> {
  data: T
  status: number
  /** The record's version as sent by the server, for example "3" (quotes included), or null. */
  etag: string | null
}

export interface ApiRequest {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** Sent as JSON; for PATCH, as application/merge-patch+json. */
  body?: unknown
  /** The ETag of the version this change is based on. Required by the API for PUT and PATCH. */
  ifMatch?: string
  signal?: AbortSignal
}

export async function apiFetch<T>(path: string, request: ApiRequest = {}): Promise<ApiResponse<T>> {
  const method = request.method ?? 'GET'
  const headers = new Headers({ Accept: 'application/json, application/problem+json' })
  if (apiKey) {
    headers.set('X-Api-Key', apiKey)
  }
  if (request.ifMatch) {
    headers.set('If-Match', request.ifMatch)
  }
  let body: string | undefined
  if (request.body !== undefined) {
    headers.set('Content-Type', method === 'PATCH' ? 'application/merge-patch+json' : 'application/json')
    body = JSON.stringify(request.body)
  }

  let response: Response
  try {
    response = await fetch(path, { method, headers, body, signal: request.signal ?? null })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError({ status: 0, title: 'Network error', detail: 'The server could not be reached.' })
  }

  const payload = await readBody(response)
  if (!response.ok) {
    throw ApiError.fromProblem(response.status, payload, response.statusText || `HTTP ${response.status}`)
  }
  return { data: payload as T, status: response.status, etag: response.headers.get('ETag') }
}

/** JSON (including problem+json) is parsed; an empty body (204) is undefined; anything else is ignored. */
async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return undefined
  }
  const type = response.headers.get('Content-Type') ?? ''
  if (!type.includes('json')) {
    return undefined
  }
  const text = await response.text()
  if (!text) {
    return undefined
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return undefined
  }
}
