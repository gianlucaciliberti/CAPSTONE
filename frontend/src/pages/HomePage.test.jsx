import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import HomePage from './HomePage'

const routes = [{ path: '/', element: <HomePage /> }]

const match = (id, opponent, overrides = {}) => ({
  id, opponent, kickoffAt: '2099-11-07T15:30:00+01:00', venueName: 'Campo comunale', venueAddress: 'Via Roma 1',
  notes: null, status: 'SCHEDULED', teamGoals: null, opponentGoals: null, cancellationReason: null, version: 0, ...overrides,
})

const dashboard = (overrides = {}) => ({
  nextMatch: { match: match(7, 'Real Borgata'), myAvailability: 'PENDING', availabilityOpen: true, callupPublished: false,
    convocated: false },
  lastResult: match(5, 'Dinamo Bar', { status: 'COMPLETED', teamGoals: 3, opponentGoals: 1, kickoffAt: '2026-10-10T20:30:00+02:00' }),
  payments: { outstandingCents: 2500, overdueCount: 1 },
  staff: null,
  ...overrides,
})

const myAvailability = { status: 'PENDING', note: 'Arrivo alle 20:30', editable: true, lockReason: null,
  adminOverride: false }

const scoresheet = {
  teamGoals: 3, opponentGoals: 1, consistent: true, warnings: [],
  events: [
    { id: 1, eventType: 'GOAL', teamSide: 'TEAM', playerDisplayName: 'Paolo Bianchi', minute: 5 },
    { id: 2, eventType: 'GOAL', teamSide: 'TEAM', playerDisplayName: 'Paolo Bianchi', minute: 20 },
    { id: 3, eventType: 'OWN_GOAL', teamSide: 'OPPONENT', playerDisplayName: null, minute: 30 },
    { id: 4, eventType: 'GOAL', teamSide: 'OPPONENT', playerDisplayName: null, minute: 40 },
  ],
}

/** Risponde a /dashboard con il contenuto indicato e alle altre chiamate della home con elenchi vuoti. */
function mockApi(data) {
  globalThis.fetch = vi.fn((url, options) =>
    Promise.resolve(
      url.includes('/dashboard')
        ? jsonResponse(data)
        : url.includes('/availability/me')
          ? jsonResponse(options?.method === 'PUT' ? { ...myAvailability, ...JSON.parse(options.body) } : myAvailability)
          : url.includes('/scoresheet')
            ? jsonResponse(scoresheet)
            : url.includes('/callups/me')
              ? jsonResponse([])
              : jsonResponse({ content: [], page: 0, size: 3, totalElements: 0, totalPages: 0 }),
    ),
  )
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('HomePage', () => {
  it('mostra il server online ai visitatori quando le API rispondono', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({ timezone: 'Europe/Rome', serverTime: '2026-10-17T11:00:00+02:00' }),
    )

    renderWithProviders(routes)

    expect(await screen.findByText('Online')).toBeInTheDocument()
  })

  it('mostra un errore leggibile con possibilità di riprovare se il server non risponde', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    renderWithProviders(routes)

    expect(await screen.findByRole('alert')).toHaveTextContent('Impossibile contattare il server')
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeInTheDocument()
  })

  it('il giocatore vede prossima partita, disponibilità da dare, pagamenti e ultimo risultato', async () => {
    mockApi(dashboard())
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    expect(await screen.findByText('Ciao Mario!')).toBeInTheDocument()
    expect(await screen.findByText('vs Real Borgata')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'In attesa di risposta' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Convocazione non ancora pubblicata.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dettagli partita' })).toHaveAttribute('href', '/matches/7')
    expect(screen.getByText(/Da pagare/)).toHaveTextContent('Da pagare: 25,00 €')
    expect(screen.getByText('1 scaduta')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Noi 3 - 1 Dinamo Bar' })).toHaveAttribute('href', '/matches/5')
    expect(screen.getByText('Vittoria')).toBeInTheDocument()
    const scorers = await screen.findByRole('list', { name: 'Marcatori' })
    expect(scorers).toHaveTextContent('Paolo Bianchi ×2')
    expect(scorers).toHaveTextContent('Autogol avversario')
    expect(screen.queryByText('Da fare')).not.toBeInTheDocument()
    expect(screen.queryByText('Online')).not.toBeInTheDocument()
  })

  it('il giocatore risponde dalla home senza perdere la nota già scritta', async () => {
    mockApi(dashboard())
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    const available = await screen.findByRole('button', { name: 'Disponibile' })
    fireEvent.click(available)

    await waitFor(() => expect(available).toHaveAttribute('aria-pressed', 'true'))
    const [url, options] = globalThis.fetch.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(url).toContain('/matches/7/availability/me')
    expect(JSON.parse(options.body)).toEqual({ status: 'AVAILABLE', note: 'Arrivo alle 20:30' })
  })

  it('segnala la convocazione e gestisce l\'assenza di partite e pagamenti', async () => {
    mockApi(dashboard({
      nextMatch: { ...dashboard().nextMatch, availabilityOpen: false, callupPublished: true, convocated: true,
        myAvailability: 'AVAILABLE' },
      payments: { outstandingCents: 0, overdueCount: 0 },
      lastResult: null,
    }))
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    expect(await screen.findByText('Sei convocato per questa partita.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dettagli partita' })).toBeInTheDocument()
    expect(screen.getByText('Nessun pagamento in sospeso.')).toBeInTheDocument()
    expect(screen.getByText('Nessuna partita conclusa.')).toBeInTheDocument()
  })

  it("l'Admin vede le cose da fare con i collegamenti alle pagine giuste", async () => {
    mockApi(dashboard({
      nextMatch: null,
      staff: {
        matchesWithoutCallup: [match(8, 'Sporting Amici')],
        failedEmails: 2,
        resultsToRecord: [match(4, 'Atletico', { kickoffAt: '2026-10-15T20:00:00+02:00' })],
        pendingApprovals: 1,
        outstandingPaymentsCents: 4500,
      },
    }))
    renderWithProviders(routes, { user: demoUser('ADMIN') })

    const tasks = await screen.findByLabelText('Attività in sospeso')
    expect(within(tasks).getByRole('link', { name: /Convocazione da pubblicare: vs Sporting Amici/ }))
      .toHaveAttribute('href', '/matches/8/callup')
    expect(within(tasks).getByRole('link', { name: /Risultato da registrare: vs Atletico/ }))
      .toHaveAttribute('href', '/matches/4')
    expect(within(tasks).getByRole('link', { name: 'Registrazioni da approvare: 1' })).toHaveAttribute('href', '/admin/users')
    expect(within(tasks).getByRole('link', { name: 'Email non consegnate: 2' }))
      .toHaveAttribute('href', '/emails?status=FAILED')
    expect(within(tasks).getByRole('link', { name: /Da incassare: 45,00/ }))
      .toHaveAttribute('href', '/admin/payments?status=OPEN')
    expect(screen.getByText(/Nessuna partita in calendario/)).toBeInTheDocument()
  })

  it("l'Allenatore senza attività in sospeso vede un messaggio rassicurante", async () => {
    mockApi(dashboard({
      staff: { matchesWithoutCallup: [], failedEmails: 0, resultsToRecord: null, pendingApprovals: null,
        outstandingPaymentsCents: null },
    }))
    renderWithProviders(routes, { user: demoUser('COACH') })

    expect(await screen.findByText('Nessuna attività in sospeso.')).toBeInTheDocument()
  })
})
