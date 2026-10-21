import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { EmailSummary, EmailsPage } from './EmailPages'

const email = (overrides = {}) => ({
  id: 5,
  type: 'CALLUP_PUBLISHED',
  recipientName: 'Paolo Bianchi',
  recipientEmail: 'paolo@example.com',
  subject: 'Convocazione: vs Real Borgata',
  status: 'FAILED',
  attempts: 5,
  maxAttempts: 5,
  nextAttemptAt: '2026-10-20T10:00:00+02:00',
  sentAt: null,
  createdAt: '2026-10-20T09:00:00+02:00',
  lastError: null,
  deliveryStatus: null,
  deliveryUpdatedAt: null,
  deliveryError: null,
  relatedEntityType: 'CALLUP',
  relatedEntityId: '3',
  ...overrides,
})

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

afterEach(() => {
  vi.restoreAllMocks()
})

describe('EmailsPage', () => {
  const routes = [{ path: '/emails', element: <EmailsPage /> }]

  it("l'allenatore vede lo stato degli invii ma non le azioni di gestione", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(page([email({ status: 'SENT', deliveryStatus: 'OPEN' })])))
    renderWithProviders(routes, { path: '/emails', user: demoUser('COACH') })

    expect(await screen.findByText('Convocazione: vs Real Borgata')).toBeInTheDocument()
    expect(screen.getByText('Inviata', { selector: '.badge' })).toBeInTheDocument()
    expect(screen.getByText('Aperta')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ritenta' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ritenta tutti i falliti' })).not.toBeInTheDocument()
  })

  it('filtra per stato e per convocazione tramite i parametri della richiesta', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(page([])))
    renderWithProviders(routes, { path: '/emails?relatedType=CALLUP&relatedId=3', user: demoUser('COACH') })

    expect(await screen.findByText('Filtro: email della convocazione #3')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Fallite' }))

    await waitFor(() =>
      expect(globalThis.fetch).toHaveBeenLastCalledWith(
        '/api/v1/emails?page=0&size=20&status=FAILED&relatedType=CALLUP&relatedId=3',
        expect.anything(),
      ),
    )
  })

  it("l'Admin ritenta un invio fallito dopo conferma", async () => {
    globalThis.fetch = vi.fn((url, init) =>
      Promise.resolve(
        init?.method === 'POST'
          ? jsonResponse(email({ status: 'PENDING', attempts: 0 }))
          : jsonResponse(page([email({ lastError: 'Mailjet ha risposto 500' })])),
      ),
    )
    renderWithProviders(routes, { path: '/emails', user: demoUser('ADMIN') })

    expect(await screen.findByText('Mailjet ha risposto 500')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Ritenta' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Ritenta' }))

    expect(await screen.findByText('Invio rimesso in coda.')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/emails/5/retry', expect.objectContaining({ method: 'POST' }))
  })
})

describe('EmailSummary', () => {
  it('riassume gli invii della convocazione con link al dettaglio', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({ total: 5, byStatus: { PENDING: 1, SENDING: 0, SENT: 3, FAILED: 1, CANCELLED: 0 } }),
    )
    renderWithProviders([{ path: '/', element: <EmailSummary relatedType="CALLUP" relatedId={3} label="Email ai convocati" /> }],
      { user: demoUser('COACH') })

    expect(await screen.findByText(/Email ai convocati: 3 inviate · 1 in coda · 1 fallite/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'dettagli' })).toHaveAttribute('href', '/emails?relatedType=CALLUP&relatedId=3')
  })
})
