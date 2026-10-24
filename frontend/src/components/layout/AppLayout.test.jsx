import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { demoUser, renderWithProviders } from '../../test/renderWithProviders'
import AppLayout from './AppLayout'

const routes = [{ path: '/', element: <AppLayout />, children: [{ path: 'calendar', element: <p>calendario</p> }] }]

describe('AppLayout (mobile-first)', () => {
  it("l'utente autenticato ha la barra inferiore con le sezioni principali", () => {
    const { container } = renderWithProviders(routes, { path: '/calendar', user: demoUser('PLAYER') })

    const bottom = screen.getByRole('navigation', { name: 'Navigazione principale' })
    const links = within(bottom).getAllByRole('link')
    expect(links.map((l) => l.textContent)).toEqual(['Home', 'Calendario', 'Disponibilità', 'Risultati', 'Pagamenti'])
    // Icone vere (SVG), non emoji.
    expect(bottom.querySelectorAll('svg')).toHaveLength(5)
    expect(within(bottom).getByRole('link', { name: /Calendario/ })).toHaveClass('active')
    expect(container.firstChild).toHaveClass('has-bottom-nav')
  })

  it('nel menu in alto le sezioni principali compaiono solo da desktop, le altre sempre', () => {
    renderWithProviders(routes, { path: '/calendar', user: demoUser('ADMIN') })

    const top = screen.getAllByRole('navigation').find((n) => n.classList.contains('app-navbar'))
    expect(within(top).getByRole('link', { name: 'Calendario' }).closest('li')).toHaveClass('d-none', 'd-lg-block')
    expect(within(top).getByRole('link', { name: 'Utenti' }).closest('li')).not.toHaveClass('d-none')
    expect(within(top).getByRole('link', { name: 'Profilo' }).closest('li')).not.toHaveClass('d-none')
  })

  it('il visitatore non ha la barra inferiore', () => {
    const { container } = renderWithProviders(routes, { path: '/' })

    expect(screen.queryByRole('navigation', { name: 'Navigazione principale' })).not.toBeInTheDocument()
    expect(container.firstChild).not.toHaveClass('has-bottom-nav')
  })
})
