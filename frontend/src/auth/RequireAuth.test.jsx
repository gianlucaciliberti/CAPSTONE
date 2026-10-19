import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { demoUser, renderWithProviders } from '../test/renderWithProviders'
import { RequireAnonymous, RequireAuth } from './RequireAuth'

const routes = [
  { path: '/login', element: <p>Pagina di login</p> },
  { path: '/', element: <p>Home</p> },
  { element: <RequireAuth />, children: [{ path: '/profile', element: <p>Profilo privato</p> }] },
  { element: <RequireAuth role="ADMIN" />, children: [{ path: '/admin/users', element: <p>Area admin</p> }] },
  { element: <RequireAnonymous />, children: [{ path: '/register', element: <p>Registrazione</p> }] },
]

describe('RequireAuth', () => {
  it("rimanda al login l'utente anonimo", () => {
    renderWithProviders(routes, { path: '/profile' })
    expect(screen.getByText('Pagina di login')).toBeInTheDocument()
  })

  it("mostra la pagina all'utente autenticato", () => {
    renderWithProviders(routes, { path: '/profile', user: demoUser('PLAYER') })
    expect(screen.getByText('Profilo privato')).toBeInTheDocument()
  })

  it('blocca le pagine riservate a un ruolo superiore', () => {
    renderWithProviders(routes, { path: '/admin/users', user: demoUser('COACH') })
    expect(screen.queryByText('Area admin')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Non hai i permessi')
  })

  it("consente l'area admin all'Admin", () => {
    renderWithProviders(routes, { path: '/admin/users', user: demoUser('ADMIN') })
    expect(screen.getByText('Area admin')).toBeInTheDocument()
  })

  it('attende il ripristino della sessione prima di decidere', () => {
    renderWithProviders(routes, { path: '/profile', status: 'loading' })
    expect(screen.getByRole('status')).toHaveTextContent('Verifica della sessione')
  })

  it("le pagine per anonimi rimandano alla home l'utente già autenticato", () => {
    renderWithProviders(routes, { path: '/register', user: demoUser('PLAYER') })
    expect(screen.getByText('Home')).toBeInTheDocument()
  })
})
