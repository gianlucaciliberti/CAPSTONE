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
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ serverTime: '2026-10-17T11:00:00+02:00' }))

    const { unmount } = renderWithProviders(routes, { user: demoUser('PLAYER') })
    expect(await screen.findByText('Ciao Mario!')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Gestione utenti' })).not.toBeInTheDocument()
    unmount()

    renderWithProviders(routes, { user: demoUser('ADMIN') })
    expect(await screen.findByRole('link', { name: 'Gestione utenti' })).toBeInTheDocument()
  })
})
