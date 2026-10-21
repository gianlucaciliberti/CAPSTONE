import { act, fireEvent, render, screen } from '@testing-library/react'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppStatusBanners, InstallAppCard } from './components/layout/AppStatus'
import { captureInstallPrompt, notifyUpdate } from './pwa'

/** Carica public/sw.js con un "self" finto e restituisce la funzione che sceglie la strategia di cache. */
function loadRouteFor() {
  const source = readFileSync(resolve(__dirname, '../public/sw.js'), 'utf8')
  const fakeSelf = { location: { origin: 'https://squadra.example' }, addEventListener: () => {} }
  return new Function('self', `${source}\nreturn routeFor`)(fakeSelf)
}

const request = (path, { method = 'GET', mode = 'cors', origin = 'https://squadra.example' } = {}) => ({
  url: `${origin}${path}`,
  method,
  mode,
})

afterEach(() => {
  act(() => notifyUpdate(null))
  vi.restoreAllMocks()
})

describe('service worker', () => {
  const routeFor = loadRouteFor()

  it('non mette mai in cache le API, nemmeno in GET', () => {
    expect(routeFor(request('/api/v1/dashboard'))).toBe('network')
    expect(routeFor(request('/api/v1/auth/refresh', { method: 'POST' }))).toBe('network')
  })

  it('usa la cache solo per i file con hash e le icone; navigazione network-first', () => {
    expect(routeFor(request('/assets/index-abc123.js'))).toBe('cache-first')
    expect(routeFor(request('/icons/icon-192.png'))).toBe('cache-first')
    expect(routeFor(request('/matches/7', { mode: 'navigate' }))).toBe('navigate')
    expect(routeFor(request('/manifest.webmanifest'))).toBe('network')
    expect(routeFor(request('/assets/x.js', { origin: 'https://cdn.example' }))).toBe('network')
  })

  it('contiene i segnaposto sostituiti dal plugin di build', () => {
    const source = readFileSync(resolve(__dirname, '../public/sw.js'), 'utf8')
    expect(source).toContain("'__BUILD_VERSION__'")
    expect(source).toContain('[] /* __PRECACHE__ */')
  })
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

  it('propone la nuova versione e la attiva al clic su Aggiorna', () => {
    const apply = vi.fn()
    render(<AppStatusBanners />)
    act(() => notifyUpdate(apply))

    fireEvent.click(screen.getByRole('button', { name: 'Aggiorna' }))
    expect(apply).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Aggiorna' })).toBeDisabled()
  })
})

describe('InstallAppCard', () => {
  it('senza prompt del browser mostra le istruzioni per Safari', () => {
    render(<InstallAppCard />)
    expect(screen.getByText(/Aggiungi alla schermata Home/)).toBeInTheDocument()
  })

  it('con il prompt del browser installa con un clic', async () => {
    const prompt = vi.fn()
    const event = { preventDefault: vi.fn(), prompt, userChoice: Promise.resolve({ outcome: 'accepted' }) }
    render(<InstallAppCard />)
    act(() => captureInstallPrompt(event))

    fireEvent.click(screen.getByRole('button', { name: "Installa l'app" }))
    expect(await screen.findByText("Stai usando l'app installata.")).toBeInTheDocument()
    expect(event.preventDefault).toHaveBeenCalled()
    expect(prompt).toHaveBeenCalled()
  })
})
