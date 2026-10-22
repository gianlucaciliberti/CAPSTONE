import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import ProfilePage from './ProfilePage'

const routes = [{ path: '/', element: <ProfilePage /> }]

const fill = (current, next, confirm) => {
  fireEvent.change(screen.getByLabelText('Password attuale'), { target: { value: current } })
  fireEvent.change(screen.getByLabelText('Nuova password'), { target: { value: next } })
  fireEvent.change(screen.getByLabelText('Conferma nuova password'), { target: { value: confirm } })
  fireEvent.click(screen.getByRole('button', { name: 'Aggiorna password' }))
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ProfilePage', () => {
  it('mostra i dati dell\'utente', () => {
    globalThis.fetch = vi.fn()
    renderWithProviders(routes, { user: demoUser('COACH') })

    expect(screen.getByText('Mario Rossi')).toBeInTheDocument()
    expect(screen.getByText('mario@example.com')).toBeInTheDocument()
    expect(screen.getByText('verificata')).toBeInTheDocument()
  })

  it('controlla le regole della password prima di chiamare il server', async () => {
    globalThis.fetch = vi.fn()
    renderWithProviders(routes, { user: demoUser() })

    fill('', 'corta', 'diversa')

    expect(await screen.findByText('Inserisci la password attuale.')).toBeInTheDocument()
    expect(screen.getByText(/almeno 10 caratteri/)).toBeInTheDocument()
    expect(screen.getByText('Le password non coincidono.')).toBeInTheDocument()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('cambia la password e aggiorna la sessione del dispositivo', async () => {
    const session = { accessToken: 'nuovo', user: demoUser() }
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(session))
    const { auth } = renderWithProviders(routes, { user: demoUser() })

    fill('VecchiaPass2025', 'NuovaPass2026', 'NuovaPass2026')

    expect(await screen.findByRole('status')).toHaveTextContent('Password aggiornata')
    expect(auth.setSession).toHaveBeenCalledWith(session)
    const [url, init] = globalThis.fetch.mock.calls[0]
    expect(url).toBe('/api/v1/auth/change-password')
    expect(JSON.parse(init.body)).toEqual({ currentPassword: 'VecchiaPass2025', newPassword: 'NuovaPass2026' })
    expect(screen.getByLabelText('Password attuale')).toHaveValue('')
  })

  it('mostra l\'errore del server sul campo giusto', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({
      detail: 'Controlla i campi evidenziati.', code: 'VALIDATION_FAILED',
      fieldErrors: { currentPassword: 'Password attuale non corretta.' },
    }, 400))
    const { auth } = renderWithProviders(routes, { user: demoUser() })

    fill('Sbagliata2025', 'NuovaPass2026', 'NuovaPass2026')

    expect(await screen.findByText('Password attuale non corretta.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Controlla i campi evidenziati.'))
    expect(auth.setSession).not.toHaveBeenCalled()
  })
})
