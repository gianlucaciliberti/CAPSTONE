import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { demoUser, jsonResponse, renderWithProviders } from '../test/renderWithProviders'
import { parseEuro } from '../utils/money'
import { MyPaymentsPage, PaymentDetailPage, PaymentFormPage } from './PaymentPages'

const request = (overrides = {}) => ({
  id: 5,
  description: 'Campo ottobre',
  amountCents: 1500,
  currency: 'EUR',
  dueDate: '2026-10-31',
  notes: null,
  status: 'OPEN',
  createdAt: '2026-10-01T10:00:00+02:00',
  cancellationReason: null,
  pendingCount: 1,
  paidCount: 1,
  cancelledCount: 0,
  collectedCents: 1500,
  outstandingCents: 1500,
  version: 2,
  ...overrides,
})

const detail = (overrides = {}) => ({
  request: request(overrides.request),
  assignments: overrides.assignments ?? [
    { id: 11, userId: 2, userName: 'Paolo Bianchi', amountCents: 1500, status: 'PENDING', paidOn: null, paymentMethod: null,
      adminNote: null, confirmedAt: null, confirmedBy: null, version: 0 },
    { id: 12, userId: 3, userName: 'Luca Verdi', amountCents: 1500, status: 'PAID', paidOn: '2026-10-05', paymentMethod: 'CASH',
      adminNote: 'al campo', confirmedAt: '2026-10-05T21:00:00+02:00', confirmedBy: 'Anna Admin', version: 1 },
  ],
})

const activeUsers = {
  content: [
    { id: 2, firstName: 'Paolo', lastName: 'Bianchi' },
    { id: 3, firstName: 'Luca', lastName: 'Verdi' },
    { id: 4, firstName: 'Marco', lastName: 'Neri' },
  ],
  page: 0, size: 100, totalElements: 3, totalPages: 1,
}

const emailSummary = { total: 2, byStatus: { PENDING: 0, SENDING: 0, SENT: 2, FAILED: 0, CANCELLED: 0 } }

afterEach(() => {
  vi.restoreAllMocks()
})

const lastCall = (method) => globalThis.fetch.mock.calls.findLast(([, init]) => init?.method === method)

describe('parseEuro', () => {
  it('converte gli importi in centesimi senza virgola mobile', () => {
    expect(parseEuro('15')).toBe(1500)
    expect(parseEuro('12,5')).toBe(1250)
    expect(parseEuro('0.07')).toBe(7)
    expect(parseEuro('1,234')).toBeNull()
    expect(parseEuro('abc')).toBeNull()
  })
})

describe('MyPaymentsPage', () => {
  it('mostra totale da pagare, quote scadute e pagate', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse({
      outstandingCents: 2000,
      overdueCount: 1,
      payments: [
        { assignmentId: 1, requestId: 5, description: 'Quota scaduta', notes: 'IBAN IT00', dueDate: '2026-10-01',
          amountCents: 2000, status: 'PENDING', paidOn: null, paymentMethod: null, overdue: true },
        { assignmentId: 2, requestId: 4, description: 'Campo settembre', notes: null, dueDate: null,
          amountCents: 1500, status: 'PAID', paidOn: '2026-09-20', paymentMethod: 'BANK_TRANSFER', overdue: false },
      ],
    })))
    renderWithProviders([{ path: '/', element: <MyPaymentsPage /> }], { user: demoUser('PLAYER') })

    expect(await screen.findByText(/Totale da pagare/)).toHaveTextContent('Totale da pagare: 20,00 € · 1 quota scaduta')
    const items = within(screen.getByRole('list', { name: 'Quote' })).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Scaduta')
    expect(items[0]).toHaveTextContent('IBAN IT00')
    expect(items[1]).toHaveTextContent('pagata il 20 set 2026 (Bonifico)')
    expect(screen.queryByRole('link', { name: 'Gestisci richieste' })).not.toBeInTheDocument()
  })
})

describe('PaymentFormPage', () => {
  it("l'Admin crea una richiesta con importo in centesimi e tutti i destinatari", async () => {
    globalThis.fetch = vi.fn((url, init) =>
      Promise.resolve(jsonResponse(init?.method === 'POST' ? detail() : activeUsers, init?.method === 'POST' ? 201 : 200)))
    const { router } = renderWithProviders(
      [{ path: '/admin/payments/new', element: <PaymentFormPage /> }, { path: '/admin/payments/:id', element: <p>dettaglio</p> }],
      { path: '/admin/payments/new', user: demoUser('ADMIN') },
    )

    fireEvent.click(screen.getByRole('button', { name: 'Crea richiesta' }))
    expect(await screen.findByText('Inserisci un importo valido, es. 15 o 12,50.')).toBeInTheDocument()
    expect(screen.getByText('Indica almeno un destinatario.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Descrizione'), { target: { value: 'Campo ottobre' } })
    fireEvent.change(screen.getByLabelText('Importo a testa (€)'), { target: { value: '12,50' } })
    fireEvent.click(await screen.findByLabelText('Seleziona tutti (3)'))
    fireEvent.click(screen.getByLabelText('Neri Marco'))
    fireEvent.click(screen.getByRole('button', { name: 'Crea richiesta' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/payments/5'))
    expect(JSON.parse(lastCall('POST')[1].body)).toEqual({
      description: 'Campo ottobre', dueDate: null, notes: '', amountCents: 1250, userIds: [2, 3],
    })
  })
})

describe('PaymentDetailPage', () => {
  const renderDetail = (write = detail()) => {
    globalThis.fetch = vi.fn((url, init) => {
      if (url.endsWith('/remind')) return Promise.resolve(jsonResponse({ queued: 1 }))
      if (init?.method === 'POST') return Promise.resolve(jsonResponse(write))
      if (url.includes('/emails/summary')) return Promise.resolve(jsonResponse(emailSummary))
      return Promise.resolve(jsonResponse(url.includes('/users?') ? activeUsers : detail()))
    })
    return renderWithProviders([{ path: '/admin/payments/:id', element: <PaymentDetailPage /> }],
      { path: '/admin/payments/5', user: demoUser('ADMIN') })
  }

  it('mostra totali, quote con chi ha registrato il pagamento e riepilogo delle email', async () => {
    renderDetail()
    expect(await screen.findByText('Incassato')).toBeInTheDocument()
    expect(screen.getByText('1/2')).toBeInTheDocument()
    expect(screen.getByText(/registrata da Anna Admin · al campo/)).toBeInTheDocument()
    expect(await screen.findByText(/Email ai destinatari: 2 inviate/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'dettagli' }))
      .toHaveAttribute('href', '/emails?relatedType=PAYMENT_REQUEST&relatedId=5')
  })

  it('invia promemoria a chi deve ancora pagare', async () => {
    renderDetail()
    fireEvent.click(await screen.findByRole('button', { name: 'Invia promemoria' }))
    expect(await screen.findByText('Promemoria accodati: 1.')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/payments/5/remind', expect.objectContaining({ method: 'POST' }))
  })

  it('registra un pagamento con data, metodo e versione della quota', async () => {
    renderDetail(detail({ request: { status: 'CLOSED', pendingCount: 0, paidCount: 2 },
      assignments: detail().assignments.map((a) => ({ ...a, status: 'PAID', paidOn: '2026-10-05', paymentMethod: 'CASH' })) }))

    fireEvent.click(await screen.findByRole('button', { name: 'Registra pagamento' }))
    const form = screen.getByRole('form', { name: 'Registra pagamento' })
    fireEvent.change(within(form).getByLabelText('Data'), { target: { value: '2026-10-10' } })
    fireEvent.change(within(form).getByLabelText('Metodo'), { target: { value: 'BANK_TRANSFER' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Conferma pagamento' }))

    expect(await screen.findByText('Chiusa')).toBeInTheDocument()
    const [url, init] = lastCall('POST')
    expect(url).toBe('/api/v1/payments/5/assignments/11/confirm')
    expect(JSON.parse(init.body)).toEqual({ paidOn: '2026-10-10', method: 'BANK_TRANSFER', note: '', version: 0 })
  })

  it('esonera e riapre le quote dopo conferma', async () => {
    renderDetail()
    fireEvent.click(await screen.findByRole('button', { name: 'Esonera' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Esonera' }))
    await waitFor(() => expect(lastCall('POST')[0]).toBe('/api/v1/payments/5/assignments/11/waive'))

    fireEvent.click(screen.getByRole('button', { name: 'Riporta a da pagare' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Riporta a da pagare' }))
    await waitFor(() => expect(lastCall('POST')[0]).toBe('/api/v1/payments/5/assignments/12/reopen'))
    expect(JSON.parse(lastCall('POST')[1].body)).toEqual({ version: 1 })
  })

  it('aggiunge solo utenti non ancora destinatari', async () => {
    renderDetail()
    fireEvent.click(await screen.findByRole('button', { name: 'Aggiungi destinatari' }))
    expect(await screen.findByLabelText('Seleziona tutti (1)')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Neri Marco'))
    fireEvent.click(screen.getByRole('button', { name: 'Aggiungi 1' }))

    await waitFor(() => expect(lastCall('POST')[0]).toBe('/api/v1/payments/5/assignments'))
    expect(JSON.parse(lastCall('POST')[1].body)).toEqual({ userIds: [4] })
  })

  it('una richiesta annullata non mostra azioni', async () => {
    globalThis.fetch = vi.fn((url) => Promise.resolve(jsonResponse(url.includes('/emails/summary') ? emailSummary : detail({
      request: { status: 'CANCELLED', cancellationReason: 'Partita annullata' } }))))
    renderWithProviders([{ path: '/admin/payments/:id', element: <PaymentDetailPage /> }],
      { path: '/admin/payments/5', user: demoUser('ADMIN') })

    expect(await screen.findByText('Richiesta annullata: Partita annullata')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registra pagamento' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Modifica' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Invia promemoria' })).not.toBeInTheDocument()
  })
})
