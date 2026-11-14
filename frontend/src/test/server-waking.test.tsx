import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { GET_RETRY_DELAYS_MS, apiFetch, isWakingServer, type ApiRequest } from '@/api/client'
import { ApiError } from '@/api/errors'

import { jsonResponse, renderAppAt } from './render'

/** A fetch that answers with these statuses in turn (0 = the connection fails), then 200. */
function serverAnswering(...statuses: number[]) {
  let call = 0
  return vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
    const status = statuses[call++]
    if (status === 0) {
      return Promise.reject(new TypeError('Failed to fetch'))
    }
    if (status !== undefined) {
      return Promise.resolve(new Response('<html>Bad gateway</html>', { status }))
    }
    return Promise.resolve(jsonResponse({ id: 3, orgId: 1, role: 'REP' }))
  })
}

const TOTAL_WAIT_MS = GET_RETRY_DELAYS_MS.reduce((sum, ms) => sum + ms, 0)

describe('Waking a sleeping server', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('retries a GET through 502, 504 and a failed connection, and gets the answer', async () => {
    const fetchMock = serverAnswering(502, 504, 0, 503)

    const result = apiFetch<{ id: number }>('/api/me')
    await vi.advanceTimersByTimeAsync(1)
    expect(isWakingServer()).toBe(true)
    await vi.advanceTimersByTimeAsync(TOTAL_WAIT_MS)

    await expect(result).resolves.toMatchObject({ data: { id: 3 }, status: 200 })
    expect(fetchMock).toHaveBeenCalledTimes(5)
    expect(isWakingServer()).toBe(false)
  })

  it('waits about a minute in all, then gives up with the last error', async () => {
    const fetchMock = serverAnswering(...Array<number>(20).fill(502))
    expect(TOTAL_WAIT_MS).toBe(60_000)

    const result = apiFetch('/api/me')
    const failure = expect(result).rejects.toMatchObject({ status: 502 })
    await vi.advanceTimersByTimeAsync(TOTAL_WAIT_MS - 1)
    expect(fetchMock).toHaveBeenCalledTimes(GET_RETRY_DELAYS_MS.length)
    await vi.advanceTimersByTimeAsync(1)

    await failure
    expect(fetchMock).toHaveBeenCalledTimes(GET_RETRY_DELAYS_MS.length + 1)
    expect(isWakingServer()).toBe(false)
  })

  it.each<NonNullable<ApiRequest['method']>>(['POST', 'PUT', 'PATCH', 'DELETE'])(
    'never retries a %s: it may have reached the server already',
    async (method) => {
      const fetchMock = serverAnswering(502, 502)

      const result = apiFetch('/api/contacts/1', { method, body: { lastName: 'X' }, ifMatch: '"0"' })
      const failure = expect(result).rejects.toMatchObject({ status: 502 })
      await vi.advanceTimersByTimeAsync(TOTAL_WAIT_MS)

      await failure
      expect(fetchMock).toHaveBeenCalledTimes(1)
      expect(isWakingServer()).toBe(false)
    },
  )

  it('does not retry errors that asking again will not fix', async () => {
    for (const status of [400, 401, 404, 500]) {
      vi.restoreAllMocks()
      const fetchMock = serverAnswering(status, status)

      await expect(apiFetch('/api/me')).rejects.toBeInstanceOf(ApiError)
      expect(fetchMock).toHaveBeenCalledTimes(1)
    }
  })

  it('stops waiting when the request is cancelled', async () => {
    serverAnswering(502, 502, 502)
    const controller = new AbortController()

    const result = apiFetch('/api/me', { signal: controller.signal })
    const cancelled = expect(result).rejects.toMatchObject({ name: 'AbortError' })
    await vi.advanceTimersByTimeAsync(10)
    controller.abort()

    await cancelled
  })
})

describe('The status dot while the server wakes', () => {
  it('says "Waking up the demo server…", then "API connected"', async () => {
    // The first answer is a gateway timeout, as from Netlify while Render starts the server.
    serverAnswering(504)
    await renderAppAt('/pipeline')

    expect(await screen.findByText('Waking up the demo server…')).toBeInTheDocument()
    // The first pause is one second; then /api/me answers.
    expect(await screen.findByText('API connected', {}, { timeout: 3000 })).toBeInTheDocument()
    expect(screen.queryByText('Waking up the demo server…')).not.toBeInTheDocument()
  })
})
