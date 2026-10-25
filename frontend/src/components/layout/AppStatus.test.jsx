import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppStatusBanners } from './AppStatus'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('AppStatusBanners', () => {
  it('avvisa quando la connessione manca e quando torna', () => {
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    render(<AppStatusBanners />)
    expect(screen.getByRole('status')).toHaveTextContent('Sei offline')

    online.mockReturnValue(true)
    act(() => window.dispatchEvent(new Event('online')))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
