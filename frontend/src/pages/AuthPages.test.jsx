import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { LoginPage, RegisterPage } from './AuthPages'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('LoginPage', () => {
  const routes = [
    { path: '/login', element: <LoginPage /> },
    { path: '/', element: <p>Home</p> },
  ]

  it('valida i campi prima di inviare', async () => {
    const login = vi.fn()
    renderWithProviders(routes, { path: '/login', login })

    fireEvent.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByText("L'email è obbligatoria.")).toBeInTheDocument()
    expect(screen.getByText('La password è obbligatoria.')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it("mostra l'errore del server e il link di reinvio per gli account in attesa", async () => {
    const login = vi.fn().mockRejectedValue(new ApiError(403, 'Il tuo account è in attesa di approvazione.', 'ACCOUNT_PENDING'))
    renderWithProviders(routes, { path: '/login', login })

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'mario@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Calcetto2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('in attesa di approvazione')
    expect(screen.getByRole('link', { name: 'Richiedi un nuovo link' })).toBeInTheDocument()
  })

  it('dopo il login porta alla home', async () => {
    const login = vi.fn().mockResolvedValue({ id: 1 })
    renderWithProviders(routes, { path: '/login', login })

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'mario@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Calcetto2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Accedi' }))

    expect(await screen.findByText('Home')).toBeInTheDocument()
    expect(login).toHaveBeenCalledWith('mario@example.com', 'Calcetto2026')
  })
})

describe('RegisterPage', () => {
  const routes = [{ path: '/register', element: <RegisterPage /> }]

  it('applica le stesse regole della password del backend e controlla la conferma', async () => {
    renderWithProviders(routes, { path: '/register' })

    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'corta' } })
    fireEvent.change(screen.getByLabelText('Conferma password'), { target: { value: 'diversa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crea account' }))

    expect(await screen.findByText(/almeno 10 caratteri/)).toBeInTheDocument()
    expect(screen.getByText('Le password non coincidono.')).toBeInTheDocument()
  })

  it('invia la registrazione senza il campo di conferma e mostra il messaggio del server', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Richiesta di registrazione ricevuta.' }, 202))
    renderWithProviders(routes, { path: '/register' })

    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Giulia' } })
    fireEvent.change(screen.getByLabelText('Cognome'), { target: { value: 'Neri' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'giulia@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Calcetto2026' } })
    fireEvent.change(screen.getByLabelText('Conferma password'), { target: { value: 'Calcetto2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crea account' }))

    expect(await screen.findByText('Richiesta di registrazione ricevuta.')).toBeInTheDocument()
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledOnce())
    const body = JSON.parse(globalThis.fetch.mock.calls[0][1].body)
    expect(body).toEqual({ firstName: 'Giulia', lastName: 'Neri', email: 'giulia@example.com', password: 'Calcetto2026' })
  })
})
