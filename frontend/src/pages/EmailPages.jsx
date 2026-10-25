import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import EmptyState from '../components/common/EmptyState'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatDateTime } from '../utils/dates'
import { useApiData } from '../utils/useApiData'

const STATUS = {
  PENDING: { label: 'In coda', badge: 'text-bg-warning' },
  SENDING: { label: 'In invio', badge: 'badge-quiet' },
  SENT: { label: 'Inviata', badge: 'text-bg-success' },
  FAILED: { label: 'Fallita', badge: 'text-bg-danger' },
  CANCELLED: { label: 'Annullata', badge: 'text-bg-secondary' },
}

const DELIVERY = {
  SENT: 'Consegnata',
  OPEN: 'Aperta',
  CLICK: 'Link aperto',
  BOUNCE: 'Respinta',
  BLOCKED: 'Bloccata',
  SPAM: 'Segnalata come spam',
  UNSUB: 'Disiscritto',
}

const TYPES = {
  CALLUP_PUBLISHED: 'Convocazione',
  CALLUP_UPDATED: 'Convocazione aggiornata',
  CALLUP_REMOVED: 'Rimozione dai convocati',
  CALLUP_WITHDRAWN: 'Convocazione ritirata',
  ACCOUNT_APPROVED: 'Account approvato',
  EMAIL_VERIFICATION: 'Verifica email',
  PASSWORD_RESET: 'Reset password',
  PAYMENT_REQUEST: 'Richiesta di pagamento',
  PAYMENT_REMINDER: 'Promemoria pagamento',
}

const FILTERS = [
  { status: '', label: 'Tutte' },
  { status: 'PENDING', label: 'In coda' },
  { status: 'SENT', label: 'Inviate' },
  { status: 'FAILED', label: 'Fallite' },
  { status: 'CANCELLED', label: 'Annullate' },
]

function StatusBadge({ status }) {
  return <span className={`badge ${STATUS[status].badge}`}>{STATUS[status].label}</span>
}

const SUMMARY_LABELS = { SENT: 'inviate', PENDING: 'in coda', FAILED: 'fallite' }

const RELATED_LABELS = { CALLUP: 'della convocazione', PAYMENT_REQUEST: 'della richiesta di pagamento' }

/** Riepilogo delle email collegate a un'entità (es. convocazione o richiesta di pagamento), con link al dettaglio. */
export function EmailSummary({ relatedType, relatedId, label }) {
  const { data } = useApiData(`/emails/summary?relatedType=${relatedType}&relatedId=${relatedId}`)
  if (!data || data.total === 0) return null
  const parts = ['SENT', 'PENDING', 'FAILED']
    .filter((s) => data.byStatus[s] > 0)
    .map((s) => `${data.byStatus[s]} ${SUMMARY_LABELS[s]}`)
  return (
    <p className="small mb-2">
      {label}: {parts.join(' · ')}{' '}
      <Link to={`/emails?relatedType=${relatedType}&relatedId=${relatedId}`}>dettagli</Link>
    </p>
  )
}

/** Stato degli invii email: Allenatori e Admin consultano, l'Admin ritenta o annulla gli invii falliti. */
export function EmailsPage() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const page = Number(searchParams.get('page') ?? 0)
  const relatedType = searchParams.get('relatedType')
  const relatedId = searchParams.get('relatedId')

  const query = new URLSearchParams({ page, size: 20 })
  if (status) query.set('status', status)
  if (relatedType && relatedId) {
    query.set('relatedType', relatedType)
    query.set('relatedId', relatedId)
  }
  const { data, error, loading, reload } = useApiData(`/emails?${query}`)
  const [pending, setPending] = useState(null) // { action: 'retry'|'cancel'|'retryAll', email? }
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)
  const [actionError, setActionError] = useState(null)

  const update = (changes) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(changes).forEach(([key, value]) => (value === null || value === '' ? next.delete(key) : next.set(key, value)))
    setSearchParams(next)
  }

  const confirm = async () => {
    setSaving(true)
    setActionError(null)
    try {
      if (pending.action === 'retryAll') {
        const result = await api.post('/emails/retry-failed')
        setNotice(`${result.retried} invii falliti rimessi in coda.`)
      } else {
        await api.post(`/emails/${pending.email.id}/${pending.action}`)
        setNotice(pending.action === 'retry' ? 'Invio rimesso in coda.' : 'Invio annullato.')
      }
      reload()
    } catch (err) {
      setActionError(err)
      setNotice(null)
    } finally {
      setSaving(false)
      setPending(null)
    }
  }

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">Stato invii email</h1>
        {isAdmin && (
          <button type="button" className="btn btn-sm btn-outline-primary" onClick={() => setPending({ action: 'retryAll' })}>
            Ritenta tutti i falliti
          </button>
        )}
      </div>

      {relatedType && (
        <div className="alert alert-info d-flex justify-content-between align-items-center py-2">
          <span className="small">
            Filtro: email {RELATED_LABELS[relatedType] ?? 'collegate'} #{relatedId}
          </span>
          <button type="button" className="btn btn-sm btn-link" onClick={() => update({ relatedType: null, relatedId: null, page: null })}>
            Mostra tutte
          </button>
        </div>
      )}

      <div className="filter-chips mb-3" role="group" aria-label="Filtra per stato">
        {FILTERS.map((f) => (
          <button key={f.label} type="button"
            className={`btn btn-sm ${status === f.status ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => update({ status: f.status, page: null })}>
            {f.label}
          </button>
        ))}
      </div>

      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}
      <ErrorAlert error={actionError} />
      <ErrorAlert error={error} onRetry={reload} />
      {loading && <LoadingSpinner label="Caricamento invii…" />}

      {data && (
        <div className="card shadow-sm border-0">
          <div className="table-responsive">
            <table className="table table-stack align-middle mb-0 small">
              <thead>
                <tr>
                  <th>Tipo e oggetto</th>
                  <th>Destinatario</th>
                  <th>Creata</th>
                  <th>Stato</th>
                  <th>Consegna</th>
                  {isAdmin && <th className="text-end">Azioni</th>}
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={isAdmin ? 6 : 5} className="stack-empty">
                      <EmptyState compact>Nessun invio in questo elenco.</EmptyState>
                    </td>
                  </tr>
                )}
                {data.content.map((e) => (
                  <tr key={e.id}>
                    <td className="stack-title">
                      <div>
                        <div className="fw-semibold">{TYPES[e.type] ?? e.type}</div>
                        <div className="text-secondary fw-normal">{e.subject}</div>
                      </div>
                    </td>
                    <td data-label="Destinatario">
                      <div>
                        {e.recipientName}
                        <div className="text-secondary text-break">{e.recipientEmail}</div>
                      </div>
                    </td>
                    <td className="text-md-nowrap" data-label="Creata">{formatDateTime(e.createdAt)}</td>
                    <td data-label="Stato">
                      <div>
                        <StatusBadge status={e.status} />
                        <div className="text-secondary">
                          tentativi {e.attempts}/{e.maxAttempts}
                          {e.sentAt && ` · inviata ${formatDateTime(e.sentAt)}`}
                        </div>
                        {e.lastError && <div className="text-danger text-break email-error" title={e.lastError}>{e.lastError}</div>}
                      </div>
                    </td>
                    <td data-label="Consegna">
                      <div>
                        {e.deliveryStatus ? DELIVERY[e.deliveryStatus] : <span className="text-secondary">—</span>}
                        {e.deliveryError && (
                          <div className="text-danger text-break email-error" title={e.deliveryError}>{e.deliveryError}</div>
                        )}
                      </div>
                    </td>
                    {isAdmin && (
                      <td className="stack-actions">
                        {e.status === 'FAILED' && (
                          <button type="button" className="btn btn-sm btn-outline-primary ms-1"
                            onClick={() => setPending({ action: 'retry', email: e })}>
                            Ritenta
                          </button>
                        )}
                        {(e.status === 'FAILED' || e.status === 'PENDING') && (
                          <button type="button" className="btn btn-sm btn-outline-secondary ms-1"
                            onClick={() => setPending({ action: 'cancel', email: e })}>
                            Annulla
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.totalPages > 1 && (
            <div className="card-footer d-flex justify-content-between align-items-center">
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page === 0}
                onClick={() => update({ page: page - 1 })}>
                Precedente
              </button>
              <span className="small text-secondary">
                Pagina {data.page + 1} di {data.totalPages}
              </span>
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page + 1 >= data.totalPages}
                onClick={() => update({ page: page + 1 })}>
                Successiva
              </button>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(pending)}
        title={pending?.action === 'cancel' ? 'Annulla invio' : 'Ritenta invio'}
        confirmLabel={pending?.action === 'cancel' ? 'Annulla invio' : 'Ritenta'}
        confirmVariant={pending?.action === 'cancel' ? 'secondary' : 'primary'}
        loading={saving}
        onConfirm={confirm}
        onCancel={() => setPending(null)}
        message={
          pending?.action === 'retryAll'
            ? 'Tutti gli invii falliti verranno rimessi in coda con un nuovo ciclo di tentativi.'
            : pending?.action === 'retry'
              ? `L'email "${pending.email.subject}" per ${pending.email.recipientName} verrà rimessa in coda.`
              : pending && `L'email "${pending.email.subject}" per ${pending.email.recipientName} non verrà più inviata.`
        }
      />
    </>
  )
}
