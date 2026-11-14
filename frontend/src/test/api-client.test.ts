import { describe, expect, it, vi } from 'vitest'

import { apiFetch, setApiKey } from '@/api/client'
import { ApiError } from '@/api/errors'

import { jsonResponse } from './render'

function mockFetch(response: Response) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
}

function sentHeaders(fetchMock: ReturnType<typeof mockFetch>): Headers {
  const init = fetchMock.mock.calls[0]?.[1]
  return new Headers(init?.headers)
}

async function caught(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    if (error instanceof ApiError) {
      return error
    }
    throw error
  }
  throw new Error('Expected the request to fail')
}

describe('apiFetch', () => {
  it('sends the current API key and returns the data with the ETag', async () => {
    setApiKey('acme-rep-rita-key')
    const fetchMock = mockFetch(jsonResponse({ id: 7, name: 'Deal' }, { headers: { ETag: '"3"' } }))

    const response = await apiFetch<{ id: number }>('/api/opportunities/7')

    expect(fetchMock).toHaveBeenCalledWith('/api/opportunities/7', expect.objectContaining({ method: 'GET' }))
    expect(sentHeaders(fetchMock).get('X-Api-Key')).toBe('acme-rep-rita-key')
    expect(response).toEqual({ data: { id: 7, name: 'Deal' }, status: 200, etag: '"3"' })
  })

  it('sends a PATCH as a JSON Merge Patch with If-Match', async () => {
    setApiKey('acme-admin-key')
    const fetchMock = mockFetch(jsonResponse({ id: 7 }, { headers: { ETag: '"4"' } }))

    await apiFetch('/api/opportunities/7', {
      method: 'PATCH',
      body: { stage: 'NEGOTIATION' },
      ifMatch: '"3"',
    })

    const headers = sentHeaders(fetchMock)
    expect(headers.get('Content-Type')).toBe('application/merge-patch+json')
    expect(headers.get('If-Match')).toBe('"3"')
    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe('{"stage":"NEGOTIATION"}')
  })

  it('turns a 409 problem+json into an ApiError that lists the allowed moves', async () => {
    mockFetch(
      jsonResponse(
        {
          type: 'about:blank',
          title: 'Conflict',
          status: 409,
          detail: 'Cannot change stage from PROSPECTING to WON. Allowed from PROSPECTING: LOST, PROPOSAL',
          instance: '/api/opportunities/7',
          field: 'stage',
          from: 'PROSPECTING',
          to: 'WON',
          allowed: ['PROPOSAL', 'LOST'],
        },
        { status: 409 },
      ),
    )

    const error = await caught(apiFetch('/api/opportunities/7', { method: 'PATCH', body: { stage: 'WON' } }))

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(409)
    expect(error.title).toBe('Conflict')
    expect(error.field).toBe('stage')
    expect(error.from).toBe('PROSPECTING')
    expect(error.to).toBe('WON')
    expect(error.allowed).toEqual(['PROPOSAL', 'LOST'])
    expect(error.message).toContain('Cannot change stage')
  })

  it('keeps the field errors of a 400', async () => {
    mockFetch(
      jsonResponse(
        {
          title: 'Bad Request',
          status: 400,
          detail: 'Invalid fields: lastName',
          errors: { lastName: 'must not be blank' },
        },
        { status: 400 },
      ),
    )

    const error = await caught(apiFetch('/api/contacts', { method: 'POST', body: { firstName: 'A' } }))

    expect(error.status).toBe(400)
    expect(error.errors).toEqual({ lastName: 'must not be blank' })
    expect(error.allowed).toBeUndefined()
  })

  it('handles an error without a problem body, and a server that cannot be reached', async () => {
    // Statuses and methods that are never retried (retries are covered in server-waking.test.tsx).
    mockFetch(
      new Response('<html>Internal error</html>', { status: 500, statusText: 'Internal Server Error' }),
    )
    const crash = await caught(apiFetch('/api/me'))
    expect(crash.status).toBe(500)
    expect(crash.title).toBe('Internal Server Error')

    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    const offline = await caught(apiFetch('/api/contacts', { method: 'POST', body: { firstName: 'A' } }))
    expect(offline.status).toBe(0)
    expect(offline.title).toBe('Network error')
  })

  it('ignores problem members of the wrong type instead of trusting them', () => {
    const error = ApiError.fromProblem(409, { title: 42, allowed: 'PROPOSAL', errors: { a: 1, b: 'x' } })
    expect(error.title).toBe('Request failed')
    expect(error.allowed).toBeUndefined()
    expect(error.errors).toEqual({ b: 'x' })
  })
})
