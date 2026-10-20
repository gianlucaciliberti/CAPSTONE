import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { AvailabilityPage, MyAvailabilityCard, TeamAvailabilityCard } from './AvailabilityPages'

const mine = (overrides = {}) => ({
  matchId: 7,
  status: 'PENDING',
  note: null,
  adminOverride: false,
  updatedAt: null,
  editable: true,
  lockReason: null,
  ...overrides,
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('MyAvailabilityCard', () => {
  const routes = [{ path: '/', element: <MyAvailabilityCard matchId={7} /> }]

  it('salva la scelta con la nota e aggiorna lo stato selezionato', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(mine()))
      .mockResolvedValueOnce(jsonResponse(mine({ status: 'AVAILABLE', note: 'arrivo tardi' })))
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    fireEvent.change(await screen.findByLabelText(/Nota per l'allenatore/), { target: { value: 'arrivo tardi' } })
    fireEvent.click(screen.getByRole('button', { name: 'Disponibile' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Disponibile' })).toHaveAttribute('aria-pressed', 'true'))
    const [url, init] = globalThis.fetch.mock.calls[1]
    expect(url).toBe('/api/v1/matches/7/availability/me')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual({ status: 'AVAILABLE', note: 'arrivo tardi' })
  })

  it('a convocazioni pubblicate mostra il motivo e disattiva i pulsanti', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(mine({ status: 'AVAILABLE', editable: false, lockReason: 'Le convocazioni sono state pubblicate.' })),
    )
    renderWithProviders(routes, { user: demoUser('PLAYER') })

    expect(await screen.findByText('Le convocazioni sono state pubblicate.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Non disponibile' })).toBeDisabled()
    expect(screen.queryByLabelText(/Nota per l'allenatore/)).not.toBeInTheDocument()
  })
})

describe('TeamAvailabilityCard', () => {
  const routes = [{ path: '/', element: <TeamAvailabilityCard matchId={7} /> }]
  const team = {
    matchId: 7,
    editable: false,
    lockReason: 'Le convocazioni sono state pubblicate.',
    counts: { AVAILABLE: 1, UNAVAILABLE: 0, PENDING: 1 },
    players: [
      { userId: 2, firstName: 'Luca', lastName: 'Verdi', role: 'PLAYER', status: 'AVAILABLE', note: 'ok', adminOverride: false },
      { userId: 3, firstName: 'Marco', lastName: 'Neri', role: 'PLAYER', status: 'PENDING', note: null, adminOverride: false },
    ],
  }

  it("mostra conteggi e risposte; l'Allenatore non può modificarle", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(team))
    renderWithProviders(routes, { user: demoUser('COACH') })

    expect(await screen.findByText('Disponibile: 1')).toBeInTheDocument()
    expect(screen.getByText('In attesa di risposta: 1')).toBeInTheDocument()
    expect(screen.getByText('Verdi Luca')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it("l'Admin corregge una disponibilità dopo conferma", async () => {
    globalThis.fetch = vi.fn((url, init) =>
      Promise.resolve(
        init?.method === 'PUT' ? jsonResponse({ ...team.players[1], status: 'UNAVAILABLE', adminOverride: true }) : jsonResponse(team),
      ),
    )
    renderWithProviders(routes, { user: demoUser('ADMIN') })

    fireEvent.change(await screen.findByLabelText('Disponibilità di Marco Neri'), { target: { value: 'UNAVAILABLE' } })
    expect(screen.getByRole('dialog')).toHaveTextContent('Marco Neri')
    fireEvent.click(screen.getByRole('button', { name: 'Correggi' }))

    await waitFor(() =>
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/matches/7/availability/3', expect.objectContaining({ method: 'PUT' })),
    )
  })
})

describe('AvailabilityPage', () => {
  it('elenca le prossime partite con la propria risposta', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([
        {
          match: { id: 7, opponent: 'Real Borgata', kickoffAt: '2026-11-07T15:30:00+01:00', venueName: 'Campo' },
          availability: mine({ status: 'UNAVAILABLE' }),
        },
      ]),
    )
    renderWithProviders([{ path: '/', element: <AvailabilityPage /> }], { user: demoUser('PLAYER') })

    expect(await screen.findByRole('link', { name: 'vs Real Borgata' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Non disponibile' })).toHaveAttribute('aria-pressed', 'true')
  })
})
