import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from './AuthPages'

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

describe('ForgotPasswordPage', () => {
  it("mostra sempre lo stesso messaggio del server, senza rivelare se l'email esiste", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({ message: "Se l'indirizzo è registrato riceverai un'email." }, 202))
    renderWithProviders([{ path: '/', element: <ForgotPasswordPage /> }])

    fireEvent.click(screen.getByRole('button', { name: 'Invia il link' }))
    expect(await screen.findByText("L'email è obbligatoria.")).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'nessuno@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Invia il link' }))

    expect(await screen.findByText("Se l'indirizzo è registrato riceverai un'email.")).toBeInTheDocument()
    expect(JSON.parse(globalThis.fetch.mock.calls[0][1].body)).toEqual({ email: 'nessuno@example.com' })
  })
})

describe('ResetPasswordPage', () => {
  const routes = [{ path: '/reset-password', element: <ResetPasswordPage /> }]

  it('senza token segnala il link incompleto', () => {
    renderWithProviders(routes, { path: '/reset-password' })
    expect(screen.getByText('Link non valido')).toBeInTheDocument()
  })

  it('invia token e nuova password, poi propone il login', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Password aggiornata.' }))
    renderWithProviders(routes, { path: '/reset-password?token=abc123' })

    fireEvent.change(screen.getByLabelText('Nuova password'), { target: { value: 'NuovaPass2026' } })
    fireEvent.change(screen.getByLabelText('Conferma nuova password'), { target: { value: 'NuovaPass2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salva la nuova password' }))

    expect(await screen.findByText('Password aggiornata.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Accedi' })).toHaveAttribute('href', '/login')
    expect(JSON.parse(globalThis.fetch.mock.calls[0][1].body)).toEqual({ token: 'abc123', newPassword: 'NuovaPass2026' })
  })

  it('con link scaduto propone di richiederne uno nuovo', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({ detail: 'Il link non è valido o è scaduto.', code: 'INVALID_TOKEN' }, 400))
    renderWithProviders(routes, { path: '/reset-password?token=scaduto' })

    fireEvent.change(screen.getByLabelText('Nuova password'), { target: { value: 'NuovaPass2026' } })
    fireEvent.change(screen.getByLabelText('Conferma nuova password'), { target: { value: 'NuovaPass2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salva la nuova password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Il link non è valido o è scaduto.')
    expect(screen.getAllByRole('link', { name: 'Richiedi un nuovo link' })[0]).toHaveAttribute('href', '/forgot-password')
  })
})

describe('VerifyEmailPage', () => {
  const routes = [{ path: '/verify-email', element: <VerifyEmailPage /> }]

  it("conferma l'email con il token del link", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Email confermata.' }))
    renderWithProviders(routes, { path: '/verify-email?token=tok-ok' })

    expect(await screen.findByText(/Email confermata\. Un amministratore approverà/)).toBeInTheDocument()
    expect(JSON.parse(globalThis.fetch.mock.calls[0][1].body)).toEqual({ token: 'tok-ok' })
  })

  it('con link non valido permette di chiederne uno nuovo', async () => {
    globalThis.fetch = vi.fn((url) => Promise.resolve(url.endsWith('/verify-email')
      ? jsonResponse({ detail: 'Il link non è valido o è scaduto.', code: 'INVALID_TOKEN' }, 400)
      : jsonResponse({ message: 'Se serve, riceverai un nuovo link.' }, 202)))
    renderWithProviders(routes, { path: '/verify-email?token=tok-ko' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Il link non è valido o è scaduto.')
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'giulia@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Invia un nuovo link' }))

    expect(await screen.findByText('Se serve, riceverai un nuovo link.')).toBeInTheDocument()
  })
})
