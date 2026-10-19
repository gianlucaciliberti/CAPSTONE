import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../test/renderWithProviders'
import { ApiError, api, refreshSession, setAccessToken, setSessionExpiredHandler } from './client'

beforeEach(() => {
  setAccessToken(null)
  setSessionExpiredHandler(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('api client', () => {
  it('restituisce il JSON della risposta e invia i cookie solo alla stessa origine', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ timezone: 'Europe/Rome' }))

    const data = await api.get('/public/info')

    expect(data).toEqual({ timezone: 'Europe/Rome' })
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/public/info',
      expect.objectContaining({ method: 'GET', credentials: 'same-origin' }),
    )
  })

  it('trasforma un ProblemDetail in ApiError con messaggio ed errori dei campi', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(
        { detail: 'Alcuni campi non sono validi.', code: 'VALIDATION_FAILED', fieldErrors: { email: 'Email non valida' } },
        400,
      ),
    )

    const error = await api.post('/auth/register', { email: 'x' }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.fieldErrors.email).toBe('Email non valida')
  })

  it('segnala in italiano un errore di rete', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    const error = await api.get('/public/info').catch((e) => e)

    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.message).toMatch(/Impossibile contattare il server/)
  })

  it("aggiunge l'access token come Bearer", async () => {
    setAccessToken('token-1')
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({}))

    await api.get('/auth/me')

    expect(globalThis.fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer token-1')
  })

  it('con token scaduto rinnova la sessione una volta e ripete la richiesta', async () => {
    setAccessToken('scaduto')
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'UNAUTHENTICATED' }, 401))
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'nuovo', user: { id: 1 } }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))

    const data = await api.get('/users')

    expect(data).toEqual({ ok: true })
    const [, refreshCall, retryCall] = globalThis.fetch.mock.calls
    expect(refreshCall[0]).toBe('/api/v1/auth/refresh')
    expect(refreshCall[1].headers['X-Requested-With']).toBe('XMLHttpRequest')
    expect(retryCall[1].headers.Authorization).toBe('Bearer nuovo')
  })

  it('se il rinnovo fallisce avvisa che la sessione è terminata', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    setAccessToken('scaduto')
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHENTICATED' }, 401))

    await expect(api.get('/users')).rejects.toBeInstanceOf(ApiError)
    expect(expired).toHaveBeenCalledOnce()
  })

  it('rinnovi contemporanei condividono una sola richiesta (il refresh token è monouso)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ accessToken: 'nuovo', user: { id: 1 } }))

    const [a, b] = await Promise.all([refreshSession(), refreshSession()])

    expect(a).toBe(b)
    expect(globalThis.fetch).toHaveBeenCalledOnce()
  })
})
