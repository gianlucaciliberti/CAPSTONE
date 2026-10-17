import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HomePage from './HomePage'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('HomePage', () => {
  it('mostra il server online quando le API rispondono', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ timezone: 'Europe/Rome', serverTime: '2026-10-17T11:00:00+02:00' }),
    })

    render(<HomePage />)

    expect(await screen.findByText('Online')).toBeInTheDocument()
  })

  it('mostra un errore leggibile con possibilità di riprovare se il server non risponde', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    render(<HomePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Impossibile contattare il server')
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeInTheDocument()
  })
})
