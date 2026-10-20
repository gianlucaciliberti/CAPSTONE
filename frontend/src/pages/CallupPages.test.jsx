import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { CallupCard, CallupEditorPage } from './CallupPages'

const futureKickoff = new Date(Date.now() + 5 * 86400000).toISOString()
const match = { id: 7, opponent: 'Real Borgata', kickoffAt: futureKickoff, venueName: 'Campo', status: 'SCHEDULED' }

const player = (userId, firstName, lastName, availability) => ({ userId, firstName, lastName, role: 'PLAYER', availability })

const draft = (overrides = {}) => ({
  id: 1,
  matchId: 7,
  status: 'DRAFT',
  meetingAt: null,
  notes: 'Maglia bianca',
  revision: 0,
  publishedAt: null,
  publishedBy: null,
  withdrawnAt: null,
  withdrawalReason: null,
  version: 4,
  convocated: false,
  players: [player(2, 'Luca', 'Verdi', 'AVAILABLE'), player(3, 'Marco', 'Neri', 'UNAVAILABLE')],
  warnings: [player(3, 'Marco', 'Neri', 'UNAVAILABLE')],
  ...overrides,
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('CallupCard', () => {
  const routes = [{ path: '/', element: <CallupCard match={match} /> }]

  it('per il giocatore indica che la convocazione non è ancora pubblicata', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ detail: 'Non disponibile' }, 404))
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    expect(await screen.findByText('Convocazione non ancora pubblicata.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /convocazione/i })).not.toBeInTheDocument()
  })

  it('mostra al giocatore convocato la conferma e i dettagli', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(draft({ status: 'PUBLISHED', convocated: true, revision: 1, publishedAt: futureKickoff,
        publishedBy: 'Carlo Coach', warnings: [], players: [player(1, 'Mario', 'Rossi', null)] })),
    )
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    expect(await screen.findByText('Sei convocato per questa partita.')).toBeInTheDocument()
    expect(screen.getByText('Maglia bianca')).toBeInTheDocument()
  })

  it("l'allenatore pubblica la bozza confermando gli avvisi sui convocati", async () => {
    globalThis.fetch = vi.fn((url, init) =>
      Promise.resolve(jsonResponse(init?.method === 'POST' ? draft({ status: 'PUBLISHED', revision: 1 }) : draft())),
    )
    renderWithProviders(routes, { user: demoUser('COACH') })

    fireEvent.click(await screen.findByRole('button', { name: 'Pubblica' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/Marco Neri: non disponibile/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Pubblica' }))

    await waitFor(() => {
      const call = globalThis.fetch.mock.calls.find(([url]) => url === '/api/v1/matches/7/callup/publish')
      expect(JSON.parse(call[1].body)).toEqual({ version: 4, confirmWarnings: true })
    })
  })
})

describe('CallupEditorPage', () => {
  const team = {
    matchId: 7,
    editable: true,
    counts: { AVAILABLE: 2, UNAVAILABLE: 1, PENDING: 0 },
    players: [
      { userId: 2, firstName: 'Luca', lastName: 'Verdi', role: 'PLAYER', status: 'AVAILABLE', note: null },
      { userId: 4, firstName: 'Paolo', lastName: 'Bianchi', role: 'PLAYER', status: 'AVAILABLE', note: 'arrivo tardi' },
      { userId: 3, firstName: 'Marco', lastName: 'Neri', role: 'PLAYER', status: 'UNAVAILABLE', note: null },
    ],
  }
  const routes = [
    { path: '/matches/:id/callup', element: <CallupEditorPage /> },
    { path: '/matches/:id', element: <p>Dettaglio partita</p> },
  ]

  function mockEditorFetch(existing) {
    globalThis.fetch = vi.fn((url, init) => {
      if (init?.method === 'PUT') return Promise.resolve(jsonResponse(existing ?? draft()))
      if (url.endsWith('/availability')) return Promise.resolve(jsonResponse(team))
      if (url.endsWith('/callup')) {
        return Promise.resolve(existing ? jsonResponse(existing) : jsonResponse({ detail: 'Non trovata' }, 404))
      }
      return Promise.resolve(jsonResponse(match))
    })
  }

  it('crea una nuova bozza con i disponibili selezionati', async () => {
    mockEditorFetch(null)
    renderWithProviders(routes, { path: '/matches/7/callup', user: demoUser('COACH') })

    fireEvent.click(await screen.findByRole('button', { name: 'Seleziona tutti i disponibili' }))
    expect(screen.getByText('Convocati: 2')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Salva bozza' }))

    expect(await screen.findByText('Dettaglio partita')).toBeInTheDocument()
    const put = globalThis.fetch.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(put[1].body)).toMatchObject({ playerIds: [2, 4], version: null, confirmWarnings: false })
  })

  it('su una convocazione pubblicata chiede conferma prima di aggiungere un indisponibile', async () => {
    mockEditorFetch(draft({ status: 'PUBLISHED', revision: 1, players: [player(2, 'Luca', 'Verdi', 'AVAILABLE')], warnings: [] }))
    renderWithProviders(routes, { path: '/matches/7/callup', user: demoUser('COACH') })

    fireEvent.click(await screen.findByLabelText('Neri Marco'))
    fireEvent.click(screen.getByRole('button', { name: 'Salva modifiche' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Marco Neri: non disponibile/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Salva comunque' }))

    expect(await screen.findByText('Dettaglio partita')).toBeInTheDocument()
    const put = globalThis.fetch.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(put[1].body)).toMatchObject({ playerIds: [2, 3], version: 4, confirmWarnings: true })
  })
})
