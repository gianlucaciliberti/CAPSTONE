import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

function mockFetch(response) {
  globalThis.fetch = vi.fn().mockResolvedValue(response)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('api client', () => {
  it('restituisce il JSON della risposta e invia i cookie solo alla stessa origine', async () => {
    mockFetch({ ok: true, status: 200, json: async () => ({ timezone: 'Europe/Rome' }) })

    const data = await api.get('/public/info')

    expect(data).toEqual({ timezone: 'Europe/Rome' })
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/public/info',
      expect.objectContaining({ method: 'GET', credentials: 'same-origin' }),
    )
  })

  it('trasforma un ProblemDetail in ApiError con messaggio ed errori dei campi', async () => {
    mockFetch({
      ok: false,
      status: 400,
      json: async () => ({
        detail: 'Alcuni campi non sono validi.',
        code: 'VALIDATION_FAILED',
        fieldErrors: { email: 'Email non valida' },
      }),
    })

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
})
