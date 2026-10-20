import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { CalendarPage, MatchDetailPage, MatchFormPage } from './MatchPages'

const match = (overrides = {}) => ({
  id: 7,
  opponent: 'Real Borgata',
  kickoffAt: '2026-11-07T15:30:00+01:00',
  venueName: 'Campo comunale',
  venueAddress: 'Via Roma 1',
  notes: null,
  status: 'SCHEDULED',
  teamGoals: null,
  opponentGoals: null,
  cancellationReason: null,
  version: 3,
  ...overrides,
})

const myAvailability = { matchId: 7, status: 'PENDING', note: null, adminOverride: false, updatedAt: null, editable: true }
const teamAvailability = { matchId: 7, editable: true, counts: { AVAILABLE: 0, UNAVAILABLE: 0, PENDING: 0 }, players: [] }

/** Risponde in base all'URL; `matchResponses` viene consumato in ordine per GET/POST sulla partita. */
function mockDetailFetch(matchResponses) {
  const queue = [...matchResponses]
  globalThis.fetch = vi.fn((url) => {
    if (url.endsWith('/callup')) return Promise.resolve(jsonResponse({ detail: 'Convocazione non ancora disponibile.' }, 404))
    if (url.endsWith('/availability/me')) return Promise.resolve(jsonResponse(myAvailability))
    if (url.endsWith('/availability')) return Promise.resolve(jsonResponse(teamAvailability))
    return Promise.resolve(jsonResponse(queue.length > 1 ? queue.shift() : queue[0]))
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('CalendarPage', () => {
  const routes = [
    { path: '/calendar', element: <CalendarPage /> },
    { path: '/matches/:id', element: <p>Dettaglio</p> },
  ]

  it('chiede le partite del mese indicato e le mostra nel giorno giusto', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([match(), match({ id: 8, opponent: 'Annullati FC', status: 'CANCELLED' })]))

    renderWithProviders(routes, { path: '/calendar?month=2026-11', user: demoUser('PLAYER') })

    expect(await screen.findByRole('heading', { name: 'Novembre 2026' })).toBeInTheDocument()
    expect((await screen.findAllByText(/Real Borgata/)).length).toBeGreaterThan(0)
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/matches?month=2026-11')
    expect(screen.queryByRole('link', { name: 'Nuova partita' })).not.toBeInTheDocument()
  })

  it("passa al mese successivo e mostra il pulsante di creazione solo all'Admin", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([]))

    renderWithProviders(routes, { path: '/calendar?month=2026-12', user: demoUser('ADMIN') })
    expect(await screen.findByRole('link', { name: 'Nuova partita' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Mese successivo' }))

    expect(await screen.findByRole('heading', { name: 'Gennaio 2027' })).toBeInTheDocument()
    await waitFor(() => expect(globalThis.fetch).toHaveBeenLastCalledWith('/api/v1/matches?month=2027-01', expect.anything()))
  })
})

describe('MatchDetailPage', () => {
  const routes = [{ path: '/matches/:id', element: <MatchDetailPage /> }]

  it("mostra dettagli e disponibilità; gestione e riepilogo squadra solo ai ruoli previsti", async () => {
    mockDetailFetch([match({ notes: 'Portare la maglia bianca' })])

    const { unmount } = renderWithProviders(routes, { path: '/matches/7', user: demoUser('PLAYER') })
    expect(await screen.findByRole('heading', { name: 'vs Real Borgata' })).toBeInTheDocument()
    expect(screen.getByText('Portare la maglia bianca')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'La tua disponibilità' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Disponibilità della squadra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Annulla partita' })).not.toBeInTheDocument()
    unmount()

    renderWithProviders(routes, { path: '/matches/7', user: demoUser('ADMIN') })
    expect(await screen.findByRole('button', { name: 'Annulla partita' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Disponibilità della squadra' })).toBeInTheDocument()
  })

  it('annulla la partita dopo la conferma inviando motivo e versione', async () => {
    const cancelled = match({ status: 'CANCELLED', cancellationReason: 'Pioggia' })
    mockDetailFetch([match(), cancelled, cancelled])
    renderWithProviders(routes, { path: '/matches/7', user: demoUser('ADMIN') })

    fireEvent.click(await screen.findByRole('button', { name: 'Annulla partita' }))
    fireEvent.change(screen.getByLabelText('Motivo (facoltativo)'), { target: { value: 'Pioggia' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Annulla partita' }).at(-1))

    expect(await screen.findByText('Partita annullata: Pioggia')).toBeInTheDocument()
    const cancelCall = globalThis.fetch.mock.calls.find(([url]) => url === '/api/v1/matches/7/cancel')
    expect(JSON.parse(cancelCall[1].body)).toEqual({ reason: 'Pioggia', version: 3 })
  })
})

describe('MatchFormPage', () => {
  const routes = [
    { path: '/matches/new', element: <MatchFormPage /> },
    { path: '/matches/:id/edit', element: <MatchFormPage /> },
    { path: '/matches/:id', element: <p>Dettaglio salvato</p> },
  ]

  it('valida i campi obbligatori', async () => {
    globalThis.fetch = vi.fn()
    renderWithProviders(routes, { path: '/matches/new', user: demoUser('ADMIN') })

    fireEvent.click(screen.getByRole('button', { name: 'Crea partita' }))

    expect(await screen.findByText("L'avversario è obbligatorio.")).toBeInTheDocument()
    expect(screen.getByText('Data e ora sono obbligatorie.')).toBeInTheDocument()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it("in modifica precompila l'ora italiana e invia la versione", async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(match()))
      .mockResolvedValueOnce(jsonResponse(match({ opponent: 'Nuovo avversario', version: 4 })))
    renderWithProviders(routes, { path: '/matches/7/edit', user: demoUser('ADMIN') })

    const kickoff = await screen.findByLabelText('Data e ora (ora italiana)')
    expect(kickoff).toHaveValue('2026-11-07T15:30')
    fireEvent.change(screen.getByLabelText('Avversario'), { target: { value: 'Nuovo avversario' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salva modifiche' }))

    expect(await screen.findByText('Dettaglio salvato')).toBeInTheDocument()
    const [url, init] = globalThis.fetch.mock.calls[1]
    expect(url).toBe('/api/v1/matches/7')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toMatchObject({ opponent: 'Nuovo avversario', kickoff: '2026-11-07T15:30', version: 3 })
  })
})
