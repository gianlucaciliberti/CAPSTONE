import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import HomePage from './HomePage'

const routes = [{ path: '/', element: <HomePage /> }]

afterEach(() => {
  vi.restoreAllMocks()
})

describe('HomePage', () => {
  it('mostra il server online quando le API rispondono', async () => {
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

  it("saluta l'utente autenticato e mostra il collegamento alla gestione utenti solo all'Admin", async () => {
    globalThis.fetch = vi.fn((url) =>
      Promise.resolve(
        url.includes('/callups/me')
          ? jsonResponse([])
          : url.includes('/matches/upcoming')
          ? jsonResponse({ content: [], page: 0, size: 3, totalElements: 0, totalPages: 0 })
          : url.includes('/payments/me')
          ? jsonResponse({ outstandingCents: 2500, overdueCount: 1, payments: [] })
          : jsonResponse({ serverTime: '2026-10-17T11:00:00+02:00' }),
      ),
    )

    const { unmount } = renderWithProviders(routes, { user: demoUser('PLAYER') })
    expect(await screen.findByText('Ciao Mario!')).toBeInTheDocument()
    expect(await screen.findByText(/Da pagare/)).toHaveTextContent('Da pagare: 25,00 €')
    expect(screen.getByText('1 scadute')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Gestione utenti' })).not.toBeInTheDocument()
    unmount()

    renderWithProviders(routes, { user: demoUser('ADMIN') })
    expect(await screen.findByRole('link', { name: 'Gestione utenti' })).toBeInTheDocument()
  })
})
