import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, configureApi, queryString } from './client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiRequest', () => {
  beforeEach(() => {
    configureApi({ getToken: () => null, onUnauthorized: () => {} })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns the parsed body on success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ id: 'abc' })))

    await expect(apiRequest<{ id: string }>('/api/thing')).resolves.toEqual({ id: 'abc' })
  })

  it('sends a JSON content type by default', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/api/thing', { method: 'POST', body: '{}' })

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers['Content-Type']).toBe('application/json')
  })

  it('attaches the bearer token when one is available', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)
    configureApi({ getToken: () => 'token-123', onUnauthorized: () => {} })

    await apiRequest('/api/thing')

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-123')
  })

  it('omits the authorization header when there is no session', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/api/thing')

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it('returns undefined for 204 responses instead of failing to parse', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(apiRequest('/api/thing', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('throws an ApiError carrying the status and message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ message: 'Workspace not found' }, 404)),
    )

    await expect(apiRequest('/api/thing')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
      message: 'Workspace not found',
    })
  })

  it('exposes field errors from a validation failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ message: 'Validation failed', fieldErrors: { slug: 'must be lowercase' } }, 400),
      ),
    )

    const error = await apiRequest('/api/thing').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).fieldError('slug')).toBe('must be lowercase')
    expect((error as ApiError).fieldError('name')).toBeUndefined()
  })

  it('falls back to the status text when the error body is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>gateway error</html>', { status: 502 })),
    )

    await expect(apiRequest('/api/thing')).rejects.toMatchObject({ status: 502 })
  })

  it('notifies once when the session has expired', async () => {
    const onUnauthorized = vi.fn()
    configureApi({ getToken: () => 'stale', onUnauthorized })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: 'Unauthorized' }, 401)))

    await expect(apiRequest('/api/thing')).rejects.toBeInstanceOf(ApiError)

    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('does not treat a 403 as an expired session', async () => {
    const onUnauthorized = vi.fn()
    configureApi({ getToken: () => 'valid', onUnauthorized })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: 'Forbidden' }, 403)))

    await expect(apiRequest('/api/thing')).rejects.toBeInstanceOf(ApiError)

    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})

describe('queryString', () => {
  it('builds a query string from present values', () => {
    expect(queryString({ q: 'auth', page: 2 })).toBe('?q=auth&page=2')
  })

  it('omits undefined, null, and empty values', () => {
    expect(queryString({ q: 'auth', documentType: undefined, cursor: null, filter: '' })).toBe(
      '?q=auth',
    )
  })

  it('returns an empty string when nothing is set', () => {
    expect(queryString({ q: undefined })).toBe('')
  })

  it('encodes values that need it', () => {
    expect(queryString({ q: 'a b&c' })).toBe('?q=a+b%26c')
  })
})
