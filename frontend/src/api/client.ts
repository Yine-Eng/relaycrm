import { ApiError } from './errors'

/**
 * The one way the app talks to the API. It:
 *   - sends the current user's X-Api-Key (set by the "View as" switcher);
 *   - sends JSON, or a JSON Merge Patch for PATCH, and If-Match when given;
 *   - turns every error response into an ApiError (problem+json parsed);
 *   - returns the response's ETag next to the data, for a later If-Match.
 *
 * Paths are relative ("/api/me"): in development the Vite server forwards
 * /api to the backend, and on the hosted demo Netlify does, so the browser
 * never makes a cross-origin request.
 *
 * Waking a sleeping server: the hosted demo's API sleeps when idle and takes
 * 30-60 seconds to start, longer than Netlify waits for it (about 26 seconds).
 * The first requests then fail with 502 or 504, or never connect. A GET that
 * fails that way is tried again, with growing pauses, for about a minute, and
 * isWakingServer() is true meanwhile. Only GET: a POST, PUT, PATCH or DELETE
 * might have reached the server before the error, so repeating it could apply
 * the change twice.
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

/** Statuses that mean "the server is not up (yet)": no connection, bad gateway, unavailable, gateway timeout. */
const SERVER_STARTING = new Set([0, 502, 503, 504])

/** Pauses between the attempts of a GET while the server starts: 60 seconds in all. */
export const GET_RETRY_DELAYS_MS: readonly number[] = [1000, 2000, 4000, 8000, 15000, 15000, 15000]

let wakingServer = false
const wakingListeners = new Set<() => void>()

function setWakingServer(waking: boolean) {
  if (wakingServer !== waking) {
    wakingServer = waking
    wakingListeners.forEach((listener) => {
      listener()
    })
  }
}

/** True while a GET is being retried because the server did not answer yet. */
export function isWakingServer(): boolean {
  return wakingServer
}

export function subscribeToWakingServer(listener: () => void): () => void {
  wakingListeners.add(listener)
  return () => {
    wakingListeners.delete(listener)
  }
}

export async function apiFetch<T>(path: string, request: ApiRequest = {}): Promise<ApiResponse<T>> {
  const method = request.method ?? 'GET'
  for (let attempt = 0; ; attempt++) {
    try {
      const response = await send<T>(path, method, request)
      setWakingServer(false)
      return response
    } catch (error) {
      const pause = GET_RETRY_DELAYS_MS[attempt]
      const retry =
        method === 'GET' &&
        error instanceof ApiError &&
        SERVER_STARTING.has(error.status) &&
        pause !== undefined
      if (!retry) {
        if (method === 'GET') {
          setWakingServer(false)
        }
        throw error
      }
      setWakingServer(true)
      await wait(pause, request.signal)
    }
  }
}

/** One request, without retries. */
async function send<T>(
  path: string,
  method: NonNullable<ApiRequest['method']>,
  request: ApiRequest,
): Promise<ApiResponse<T>> {
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

function wait(ms: number, signal: AbortSignal | undefined): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer)
        reject(new DOMException('Aborted', 'AbortError'))
      },
      { once: true },
    )
  })
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
