import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'
import { AuthContext, roleIncludes } from '../auth/authContext'

/**
 * Renderizza un insieme di rotte con un contesto di autenticazione finto.
 * `user` null = utente anonimo. Restituisce anche le funzioni finte di login/logout per le verifiche.
 */
export function renderWithProviders(routes, { path = '/', user = null, status, login, logout } = {}) {
  const auth = {
    status: status ?? (user ? 'authenticated' : 'anonymous'),
    user,
    login: login ?? vi.fn(),
    logout: logout ?? vi.fn(),
    setSession: vi.fn(),
    hasRole: (role) => user !== null && roleIncludes(user.role, role),
  }
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const result = render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return { ...result, router, auth }
}

export const demoUser = (role = 'PLAYER') => ({
  id: 1,
  firstName: 'Mario',
  lastName: 'Rossi',
  email: 'mario@example.com',
  role,
  status: 'ACTIVE',
  emailVerified: true,
})

export function jsonResponse(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}
