import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import AdminUsersPage from './AdminUsersPage'

const routes = [{ path: '/', element: <AdminUsersPage /> }]

const user = (id, firstName, overrides = {}) => ({
  id, firstName, lastName: 'Test', email: `${firstName.toLowerCase()}@example.com`, role: 'PLAYER',
  status: 'PENDING', emailVerified: true, ...overrides,
})

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

/** GET restituisce gli utenti indicati; le altre chiamate rispondono 200. */
function mockApi(users) {
  globalThis.fetch = vi.fn((url, init) =>
    Promise.resolve(jsonResponse(init?.method && init.method !== 'GET' ? {} : page(users))))
}

const lastWrite = () => globalThis.fetch.mock.calls.findLast(([, init]) => init?.method && init.method !== 'GET')

afterEach(() => {
  vi.restoreAllMocks()
})

describe('AdminUsersPage', () => {
  it('parte dalle registrazioni in attesa e cambia filtro', async () => {
    mockApi([user(5, 'Simone')])
    renderWithProviders(routes, { user: demoUser('ADMIN') })

    expect(await screen.findByText(/Simone Test/)).toBeInTheDocument()
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/users?page=0&size=20&status=PENDING')

    fireEvent.click(screen.getByRole('button', { name: 'Attivi' }))
    await waitFor(() =>
      expect(globalThis.fetch.mock.calls.some(([url]) => url === '/api/v1/users?page=0&size=20&status=ACTIVE')).toBe(true))
  })

  it("approva dopo conferma e non permette di approvare chi non ha verificato l'email", async () => {
    mockApi([user(5, 'Simone'), user(6, 'Nicola', { emailVerified: false })])
    renderWithProviders(routes, { user: demoUser('ADMIN') })

    const nicola = (await screen.findByText(/Nicola Test/)).closest('tr')
    expect(within(nicola).getByRole('button', { name: 'Approva' })).toBeDisabled()
    expect(within(nicola).getByText('email non verificata')).toBeInTheDocument()

    const simone = screen.getByText(/Simone Test/).closest('tr')
    fireEvent.click(within(simone).getByRole('button', { name: 'Approva' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Approva' }))

    expect(await screen.findByRole('status')).toHaveTextContent('"Approva" completata per Simone Test')
    expect(lastWrite()[0]).toBe('/api/v1/users/5/approve')
  })

  it('cambia ruolo dopo conferma; nessuna azione sul proprio account', async () => {
    mockApi([user(1, 'Mario', { status: 'ACTIVE', role: 'ADMIN' }), user(7, 'Luca', { status: 'ACTIVE' })])
    renderWithProviders(routes, { user: demoUser('ADMIN') })

    const me = (await screen.findByText(/Mario Test/)).closest('tr')
    expect(within(me).getByText('tu')).toBeInTheDocument()
    expect(within(me).queryByRole('button', { name: 'Sospendi' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Ruolo di Luca'), { target: { value: 'COACH' } })
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cambia ruolo' }))

    await waitFor(() => expect(lastWrite()[0]).toBe('/api/v1/users/7/role'))
    expect(JSON.parse(lastWrite()[1].body)).toEqual({ role: 'COACH' })
  })
})
