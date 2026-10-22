import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../test/renderWithProviders'
import AuthProvider from './AuthProvider'
import { useAuth } from './authContext'

const session = {
  accessToken: 'token-1',
  expiresAt: '2026-10-22T10:15:00Z',
  user: { id: 3, firstName: 'Carlo', lastName: 'Coach', email: 'coach@example.com', role: 'COACH' },
}

/** Mostra lo stato del contesto e offre i pulsanti di login/logout, come farebbero le pagine reali. */
function Probe() {
  const { status, user, hasRole, login, logout } = useAuth()
  return (
    <>
      <p data-testid="status">{status}</p>
      <p data-testid="user">{user ? `${user.firstName} ${user.role}` : '-'}</p>
      <p data-testid="coach">{String(hasRole('COACH'))}</p>
      <p data-testid="admin">{String(hasRole('ADMIN'))}</p>
      <button type="button" onClick={() => login('coach@example.com', 'Calcetto2026').catch(() => {})}>login</button>
      <button type="button" onClick={() => logout().catch(() => {})}>logout</button>
    </>
  )
}

const renderProvider = () =>
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )

afterEach(() => {
  vi.restoreAllMocks()
})

describe('AuthProvider', () => {
  it('ripristina la sessione dal cookie del refresh token all\'avvio', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(session))
    renderProvider()

    expect(screen.getByTestId('status')).toHaveTextContent('loading')
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
    expect(screen.getByTestId('user')).toHaveTextContent('Carlo COACH')
    // Ruoli cumulativi: l'Allenatore include il Giocatore, ma non l'Admin.
    expect(screen.getByTestId('coach')).toHaveTextContent('true')
    expect(screen.getByTestId('admin')).toHaveTextContent('false')
    const [url, init] = globalThis.fetch.mock.calls[0]
    expect(url).toBe('/api/v1/auth/refresh')
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest')
  })

  it('senza sessione valida resta anonimo; il login autentica senza salvare token nello storage', async () => {
    globalThis.fetch = vi.fn((url) =>
      Promise.resolve(url.endsWith('/auth/refresh')
        ? jsonResponse({ detail: 'Sessione scaduta', code: 'UNAUTHENTICATED' }, 401)
        : jsonResponse(session)),
    )
    renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))

    fireEvent.click(screen.getByRole('button', { name: 'login' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
    expect(JSON.parse(globalThis.fetch.mock.calls.find(([u]) => u.endsWith('/auth/login'))[1].body))
      .toEqual({ email: 'coach@example.com', password: 'Calcetto2026' })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('il logout chiude la sessione anche se il server non risponde', async () => {
    globalThis.fetch = vi.fn((url) =>
      url.endsWith('/auth/logout') ? Promise.reject(new TypeError('Failed to fetch')) : Promise.resolve(jsonResponse(session)),
    )
    renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'logout' }))
    })

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))
    expect(screen.getByTestId('user')).toHaveTextContent('-')
  })
})
