import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { ScoresheetCard } from './ScoresheetPages'

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

    fireEvent.click((await screen.findAllByRole('button', { name: 'Modifica' }))[0])
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

    fireEvent.click((await screen.findAllByRole('button', { name: 'Elimina' }))[0])
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Elimina' }))

    expect(await screen.findByText('Nessun evento registrato.')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/matches/7/events/1', expect.objectContaining({ method: 'DELETE' }))
  })
})
