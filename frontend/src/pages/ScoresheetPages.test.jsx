import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { ResultsPage, ScoresheetCard } from './ScoresheetPages'

const match = { id: 7, opponent: 'Real Borgata', kickoffAt: '2026-10-01T20:00:00+02:00', status: 'COMPLETED' }

const scoresheet = (overrides = {}) => ({
  matchId: 7,
  status: 'COMPLETED',
  teamGoals: 3,
  opponentGoals: 1,
  matchVersion: 4,
  events: [
    { id: 1, eventType: 'GOAL', teamSide: 'TEAM', playerId: 2, playerDisplayName: 'Paolo Bianchi', minute: 12, notes: null, version: 0 },
    { id: 2, eventType: 'OWN_GOAL', teamSide: 'OPPONENT', playerId: null, playerDisplayName: 'N. 5', minute: 40, notes: null, version: 0 },
    { id: 3, eventType: 'YELLOW_CARD', teamSide: 'OPPONENT', playerId: null, playerDisplayName: null, minute: null, notes: 'proteste', version: 0 },
  ],
  computedTeamGoals: 2,
  computedOpponentGoals: 0,
  consistent: false,
  warnings: ['Gol della squadra: risultato 3, nel tabellino 2.'],
  ...overrides,
})

const players = { content: [{ id: 2, firstName: 'Paolo', lastName: 'Bianchi' }], page: 0, size: 100, totalElements: 1, totalPages: 1 }

function mockApi(responses = {}) {
  globalThis.fetch = vi.fn((url, init) => {
    const method = init?.method ?? 'GET'
    if (method !== 'GET') return Promise.resolve(jsonResponse(responses.write ?? scoresheet()))
    if (url.includes('/users?')) return Promise.resolve(jsonResponse(players))
    return Promise.resolve(jsonResponse(responses.read ?? scoresheet()))
  })
}

const routes = (onChanged = vi.fn()) => [{ path: '/', element: <ScoresheetCard match={match} onChanged={onChanged} /> }]

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ScoresheetCard', () => {
  it('mostra risultato ed eventi al giocatore, senza avvisi né strumenti di modifica', async () => {
    mockApi()
    renderWithProviders(routes(), { user: demoUser('PLAYER') })

    expect(await screen.findByLabelText('Risultato')).toHaveTextContent('Noi 3 - 1 Real Borgata')
    expect(screen.getByText(/Gol · Paolo Bianchi/)).toBeInTheDocument()
    expect(screen.getByText(/Autogol · N\. 5/)).toBeInTheDocument()
    expect(screen.getByText(/Ammonizione · Giocatore avversario/)).toBeInTheDocument()
    // Due tempi da 25': il gol al 12' prima dell'intervallo, l'autogol al 40' dopo, l'evento senza minuto in fondo.
    const items = within(screen.getByRole('list', { name: 'Eventi della partita' })).getAllByRole('listitem')
    expect(items.map((li) => li.textContent)).toEqual([
      "12'Gol · Paolo Bianchi (Noi)",
      'Intervallo',
      "40'Autogol · N. 5 (Avversari)",
      'Senza minuto',
      '—Ammonizione · Giocatore avversario (Avversari)proteste',
    ])
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aggiungi evento' })).not.toBeInTheDocument()
  })

  it("segnala le incoerenze all'allenatore", async () => {
    mockApi()
    renderWithProviders(routes(), { user: demoUser('COACH') })

    expect(await screen.findByRole('alert')).toHaveTextContent('Gol della squadra: risultato 3, nel tabellino 2.')
  })

  it("l'Admin aggiorna il risultato inviando la versione della partita", async () => {
    const onChanged = vi.fn()
    mockApi({ write: scoresheet({ teamGoals: 2, matchVersion: 5, consistent: true, warnings: [] }) })
    renderWithProviders(routes(onChanged), { user: demoUser('ADMIN') })

    fireEvent.change(await screen.findByLabelText('Gol nostri'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Aggiorna risultato' }))

    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    const [url, init] = globalThis.fetch.mock.calls.find(([, i]) => i?.method === 'PUT')
    expect(url).toBe('/api/v1/matches/7/result')
    expect(JSON.parse(init.body)).toEqual({ teamGoals: 2, opponentGoals: 1, version: 4 })
    expect(screen.getByLabelText('Risultato')).toHaveTextContent('Noi 2 - 1')
  })

  it("l'Admin aggiunge un gol avversario senza collegarlo a un account", async () => {
    mockApi()
    renderWithProviders(routes(), { user: demoUser('ADMIN') })

    fireEvent.click(await screen.findByRole('button', { name: 'Aggiungi evento' }))
    const form = screen.getByRole('form', { name: 'Evento del tabellino' })
    fireEvent.change(within(form).getByLabelText('Squadra del giocatore'), { target: { value: 'OPPONENT' } })
    expect(within(form).queryByLabelText('Giocatore')).not.toBeInTheDocument()
    fireEvent.change(within(form).getByLabelText('Nome avversario (facoltativo)'), { target: { value: 'Rossi' } })
    fireEvent.change(within(form).getByLabelText('Minuto'), { target: { value: '77' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Aggiungi' }))

    await waitFor(() => expect(globalThis.fetch.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(true))
    const [url, init] = globalThis.fetch.mock.calls.find(([, i]) => i?.method === 'POST')
    expect(url).toBe('/api/v1/matches/7/events')
    expect(JSON.parse(init.body)).toMatchObject({
      eventType: 'GOAL', teamSide: 'OPPONENT', playerId: null, playerName: 'Rossi', minute: 77, version: null,
    })
  })

  it("l'Admin sceglie un giocatore registrato e modifica un evento esistente con la sua versione", async () => {
    mockApi()
    renderWithProviders(routes(), { user: demoUser('ADMIN') })

    fireEvent.click(await screen.findByRole('button', { name: 'Modifica: Gol · Paolo Bianchi' }))
    const form = screen.getByRole('form', { name: 'Evento del tabellino' })
    await waitFor(() => expect(within(form).getByLabelText('Giocatore')).toHaveValue('2'))
    fireEvent.change(within(form).getByLabelText('Minuto'), { target: { value: '15' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Salva evento' }))

    await waitFor(() => expect(globalThis.fetch.mock.calls.some(([, i]) => i?.method === 'PUT')).toBe(true))
    const [url, init] = globalThis.fetch.mock.calls.find(([, i]) => i?.method === 'PUT')
    expect(url).toBe('/api/v1/matches/7/events/1')
    expect(JSON.parse(init.body)).toMatchObject({ playerId: 2, playerName: null, minute: 15, version: 0 })
  })

  it("l'Admin elimina un evento dopo conferma", async () => {
    mockApi({ write: scoresheet({ events: [] }) })
    renderWithProviders(routes(), { user: demoUser('ADMIN') })

    fireEvent.click(await screen.findByRole('button', { name: 'Elimina: Gol · Paolo Bianchi' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Elimina' }))

    expect(await screen.findByText('Nessun evento registrato.')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/matches/7/events/1', expect.objectContaining({ method: 'DELETE' }))
  })
})

describe('ResultsPage', () => {
  const stats = {
    played: 3, wins: 1, draws: 1, losses: 1, goalsFor: 5, goalsAgainst: 4,
    players: [
      { playerId: 1, name: 'Mario Rossi', goals: 2, ownGoals: 0, yellowCards: 1, redCards: 0 },
      { playerId: null, name: 'Gianni', goals: 1, ownGoals: 1, yellowCards: 0, redCards: 0 },
    ],
  }
  const results = (overrides = {}) => ({
    content: [
      { id: 9, opponent: 'Real Borgata', kickoffAt: '2026-10-10T20:30:00+02:00', status: 'COMPLETED', teamGoals: 3, opponentGoals: 1 },
      { id: 8, opponent: 'Dinamo Bar', kickoffAt: '2026-10-03T20:30:00+02:00', status: 'COMPLETED', teamGoals: 0, opponentGoals: 2 },
    ],
    page: 0, size: 10, totalElements: 2, totalPages: 1,
    ...overrides,
  })

  function mockResults(page = results()) {
    globalThis.fetch = vi.fn((url) => Promise.resolve(jsonResponse(url.includes('/stats') ? stats : page)))
  }

  it('mostra bilancio, statistiche dei giocatori e risultati con collegamento alla partita', async () => {
    mockResults()
    renderWithProviders([{ path: '/', element: <ResultsPage /> }], { user: demoUser('PLAYER') })

    expect(await screen.findByText('Vittorie')).toBeInTheDocument()
    expect(screen.getByText('Gol fatti').nextElementSibling).toHaveTextContent('5')
    const me = screen.getByRole('row', { name: /Mario Rossi/ })
    expect(me).toHaveClass('table-success')
    expect(within(me).getAllByRole('cell')[1]).toHaveTextContent('2')
    const guest = screen.getByRole('row', { name: /Gianni/ })
    expect(guest).toHaveTextContent('(senza account)')
    expect(guest).not.toHaveClass('table-success')

    const list = await screen.findByRole('list', { name: 'Partite concluse' })
    expect(within(list).getByRole('link', { name: 'Noi 3 - 1 Real Borgata' })).toHaveAttribute('href', '/matches/9')
    expect(within(list).getByTitle('Sconfitta')).toHaveTextContent('P')
  })

  it('pagina i risultati', async () => {
    mockResults(results({ totalPages: 2, totalElements: 12 }))
    renderWithProviders([{ path: '/', element: <ResultsPage /> }], { user: demoUser('PLAYER') })

    fireEvent.click(await screen.findByRole('button', { name: 'Successiva' }))
    await waitFor(() =>
      expect(globalThis.fetch.mock.calls.some(([url]) => url === '/api/v1/matches/results?page=1&size=10')).toBe(true))
  })

  it('segnala quando non ci sono partite concluse', async () => {
    globalThis.fetch = vi.fn((url) => Promise.resolve(jsonResponse(url.includes('/stats')
      ? { ...stats, played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, players: [] }
      : results({ content: [], totalElements: 0, totalPages: 0 }))))
    renderWithProviders([{ path: '/', element: <ResultsPage /> }], { user: demoUser('PLAYER') })

    expect(await screen.findByText('Nessuna partita conclusa.')).toBeInTheDocument()
    expect(screen.getByText('Nessun evento registrato nei tabellini.')).toBeInTheDocument()
  })
})
