import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import { FormField, SubmitButton } from '../components/common/Form'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { EmailSummary } from './EmailPages'
import { formatDateTime, formatPlainDate, teamDateKey } from '../utils/dates'
import { formatEuro, parseEuro } from '../utils/money'
import { useApiData } from '../utils/useApiData'
import { useForm } from '../utils/useForm'
import { requiredError } from '../utils/validation'

const REQUEST_STATUS = {
  OPEN: { label: 'Aperta', badge: 'text-bg-primary' },
  CLOSED: { label: 'Chiusa', badge: 'text-bg-success' },
  CANCELLED: { label: 'Annullata', badge: 'text-bg-secondary' },
}

const QUOTA_STATUS = {
  PENDING: { label: 'Da pagare', badge: 'text-bg-warning' },
  PAID: { label: 'Pagata', badge: 'text-bg-success' },
  CANCELLED: { label: 'Annullata', badge: 'text-bg-secondary' },
}

const METHODS = { CASH: 'Contanti', BANK_TRANSFER: 'Bonifico', OTHER: 'Altro' }

function Badge({ map, status }) {
  return <span className={`badge ${map[status].badge}`}>{map[status].label}</span>
}

function QuotaBadge({ payment }) {
  if (payment.overdue) return <span className="badge text-bg-danger">Scaduta</span>
  return <Badge map={QUOTA_STATUS} status={payment.status} />
}

// --- Giocatore ---

/** Le quote dell'utente; l'Admin trova qui anche il collegamento alla gestione. */
export function MyPaymentsPage() {
  const { hasRole } = useAuth()
  const { data, error, loading, reload } = useApiData('/payments/me')
  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">I miei pagamenti</h1>
        {hasRole('ADMIN') && (
          <Link className="btn btn-primary btn-sm" to="/admin/payments">
            Gestisci richieste
          </Link>
        )}
      </div>
      <ErrorAlert error={error} onRetry={reload} />
      {loading && <LoadingSpinner label="Caricamento pagamenti…" />}
      {data && (
        <>
          <div className={`alert ${data.outstandingCents > 0 ? 'alert-warning' : 'alert-success'}`} role="status">
            {data.outstandingCents > 0 ? (
              <>
                Totale da pagare: <strong>{formatEuro(data.outstandingCents)}</strong>
                {data.overdueCount > 0 && ` · ${data.overdueCount} quote scadute`}
              </>
            ) : (
              'Sei in regola: nessun pagamento in sospeso.'
            )}
          </div>
          <div className="card shadow-sm border-0">
            {data.payments.length === 0 ? (
              <p className="text-secondary text-center py-4 mb-0">Nessuna richiesta di pagamento.</p>
            ) : (
              <ul className="list-group list-group-flush" aria-label="Quote">
                {data.payments.map((p) => (
                  <li key={p.assignmentId} className="list-group-item">
                    <div className="d-flex justify-content-between align-items-start gap-2">
                      <div>
                        <div className="fw-semibold">{p.description}</div>
                        <div className="small text-secondary">
                          {p.dueDate && `Scadenza ${formatPlainDate(p.dueDate)}`}
                          {p.status === 'PAID' && ` · pagata il ${formatPlainDate(p.paidOn)} (${METHODS[p.paymentMethod]})`}
                        </div>
                        {p.notes && <div className="small" style={{ whiteSpace: 'pre-line' }}>{p.notes}</div>}
                      </div>
                      <div className="text-end text-nowrap">
                        <div className="fw-semibold">{formatEuro(p.amountCents)}</div>
                        <QuotaBadge payment={p} />
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <p className="small text-secondary mt-2">
            I pagamenti si effettuano di persona o con bonifico: l'amministratore registra la conferma.
          </p>
        </>
      )}
    </>
  )
}

// --- Admin: elenco ---

const FILTERS = [
  { status: '', label: 'Tutte' },
  { status: 'OPEN', label: 'Aperte' },
  { status: 'CLOSED', label: 'Chiuse' },
  { status: 'CANCELLED', label: 'Annullate' },
]

export function PaymentsAdminPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const page = Number(searchParams.get('page') ?? 0)
  const query = new URLSearchParams({ page, size: 20 })
  if (status) query.set('status', status)
  const { data, error, loading, reload } = useApiData(`/payments?${query}`)

  const update = (changes) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(changes).forEach(([key, value]) => (value === null || value === '' ? next.delete(key) : next.set(key, value)))
    setSearchParams(next)
  }

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">Richieste di pagamento</h1>
        <Link className="btn btn-primary btn-sm" to="/admin/payments/new">
          Nuova richiesta
        </Link>
      </div>
      <div className="btn-group flex-wrap mb-3" role="group" aria-label="Filtra per stato">
        {FILTERS.map((f) => (
          <button key={f.label} type="button"
            className={`btn btn-sm ${status === f.status ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => update({ status: f.status, page: null })}>
            {f.label}
          </button>
        ))}
      </div>
      <ErrorAlert error={error} onRetry={reload} />
      {loading && <LoadingSpinner label="Caricamento richieste…" />}
      {data && (
        <div className="card shadow-sm border-0">
          {data.content.length === 0 ? (
            <p className="text-secondary text-center py-4 mb-0">Nessuna richiesta in questo elenco.</p>
          ) : (
            <div className="list-group list-group-flush">
              {data.content.map((r) => (
                <Link key={r.id} to={`/admin/payments/${r.id}`} className="list-group-item list-group-item-action">
                  <div className="d-flex justify-content-between align-items-start gap-2">
                    <div>
                      <div className="fw-semibold">{r.description}</div>
                      <div className="small text-secondary">
                        {formatEuro(r.amountCents)} a testa
                        {r.dueDate && ` · scadenza ${formatPlainDate(r.dueDate)}`} · pagate {r.paidCount}/
                        {r.paidCount + r.pendingCount}
                      </div>
                    </div>
                    <div className="text-end">
                      <Badge map={REQUEST_STATUS} status={r.status} />
                      {r.outstandingCents > 0 && (
                        <div className="small text-secondary">da incassare {formatEuro(r.outstandingCents)}</div>
                      )}
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          )}
          {data.totalPages > 1 && (
            <div className="card-footer bg-white d-flex justify-content-between align-items-center">
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
    </>
  )
}

// --- Admin: creazione e modifica ---

/** Scelta dei destinatari tra gli utenti attivi, con "seleziona tutti". */
function RecipientPicker({ users, selected, onChange, error }) {
  const allSelected = users.length > 0 && users.every((u) => selected.includes(u.id))
  const toggle = (id) => onChange(selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id])
  return (
    <fieldset className="mb-3">
      <legend className="form-label fs-6">Destinatari</legend>
      <div className="form-check mb-1">
        <input id="recipients-all" type="checkbox" className="form-check-input" checked={allSelected}
          onChange={() => onChange(allSelected ? [] : users.map((u) => u.id))} />
        <label htmlFor="recipients-all" className="form-check-label fw-semibold">
          Seleziona tutti ({users.length})
        </label>
      </div>
      <div className="row row-cols-sm-2 g-0">
        {users.map((u) => (
          <div key={u.id} className="form-check col">
            <input id={`recipient-${u.id}`} type="checkbox" className="form-check-input" checked={selected.includes(u.id)}
              onChange={() => toggle(u.id)} />
            <label htmlFor={`recipient-${u.id}`} className="form-check-label">
              {u.lastName} {u.firstName}
            </label>
          </div>
        ))}
      </div>
      {error && <div className="small text-danger">{error}</div>}
    </fieldset>
  )
}

/** Creazione (/admin/payments/new) o modifica dei dettagli (/admin/payments/:id/edit). */
export function PaymentFormPage() {
  const { id } = useParams()
  const { data: existing, error, loading } = useApiData(id ? `/payments/${id}` : null)
  if (id && loading) return <LoadingSpinner label="Caricamento richiesta…" />
  if (id && error) return <ErrorAlert error={error} />
  return <PaymentForm key={existing?.request.id ?? 'new'} existing={existing?.request} />
}

function PaymentForm({ existing }) {
  const navigate = useNavigate()
  const { data: users } = useApiData(existing ? null : '/users?status=ACTIVE&size=100')
  const [recipients, setRecipients] = useState([])
  const form = useForm({
    description: existing?.description ?? '',
    amount: '',
    dueDate: existing?.dueDate ?? '',
    notes: existing?.notes ?? '',
  })

  const onSubmit = (event) =>
    form.submit(
      event,
      (v) => ({
        description: requiredError(v.description, 'La descrizione è obbligatoria.'),
        amount: !existing && !(parseEuro(v.amount) > 0) ? 'Inserisci un importo valido, es. 15 o 12,50.' : null,
        recipients: !existing && recipients.length === 0 ? 'Indica almeno un destinatario.' : null,
      }),
      async (v) => {
        const common = { description: v.description, dueDate: v.dueDate || null, notes: v.notes }
        const saved = existing
          ? await api.put(`/payments/${existing.id}`, { ...common, version: existing.version })
          : await api.post('/payments', { ...common, amountCents: parseEuro(v.amount), userIds: recipients })
        navigate(`/admin/payments/${saved.request.id}`)
      },
    )

  return (
    <div className="row justify-content-center">
      <div className="col-lg-8">
        <div className="card shadow-sm border-0">
          <div className="card-body p-4">
            <h1 className="h4 mb-3">{existing ? 'Modifica richiesta' : 'Nuova richiesta di pagamento'}</h1>
            <ErrorAlert error={form.error} />
            <form onSubmit={onSubmit} noValidate>
              <FormField id="description" label="Descrizione" value={form.values.description} onChange={form.onChange}
                error={form.fieldErrors.description} maxLength={200} hint="Es. Quota campo ottobre" />
              <div className="row g-2">
                {!existing && (
                  <div className="col-sm-6">
                    <FormField id="amount" label="Importo a testa (€)" inputMode="decimal" value={form.values.amount}
                      onChange={form.onChange} error={form.fieldErrors.amount ?? form.fieldErrors.amountCents} />
                  </div>
                )}
                <div className="col-sm-6">
                  <FormField id="dueDate" label="Scadenza (facoltativa)" type="date" value={form.values.dueDate}
                    onChange={form.onChange} error={form.fieldErrors.dueDate} />
                </div>
              </div>
              {existing && (
                <p className="small text-secondary">
                  Importo: {formatEuro(existing.amountCents)} a testa (non modificabile dopo la creazione).
                </p>
              )}
              <FormField id="notes" label="Note (facoltative)" multiline rows={2} value={form.values.notes}
                onChange={form.onChange} error={form.fieldErrors.notes} maxLength={1000}
                hint="Visibili ai destinatari, es. IBAN o modalità di pagamento." />
              {!existing && (
                users ? (
                  <RecipientPicker users={users.content} selected={recipients} onChange={setRecipients}
                    error={form.fieldErrors.recipients ?? form.fieldErrors.userIds} />
                ) : (
                  <LoadingSpinner label="Caricamento utenti…" />
                )
              )}
              <div className="d-flex gap-2">
                <SubmitButton loading={form.loading} loadingLabel="Salvataggio…" className="btn btn-primary">
                  {existing ? 'Salva modifiche' : 'Crea richiesta'}
                </SubmitButton>
                <Link className="btn btn-outline-secondary" to={existing ? `/admin/payments/${existing.id}` : '/admin/payments'}>
                  Annulla
                </Link>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}

// --- Admin: dettaglio ---

export function PaymentDetailPage() {
  const { id } = useParams()
  const { data, error, loading, reload, setData } = useApiData(`/payments/${id}`)
  const [confirming, setConfirming] = useState(null) // quota di cui si registra il pagamento
  const [pending, setPending] = useState(null) // { action: 'waive'|'reopen'|'cancel', assignment? }
  const [reason, setReason] = useState('')
  const [adding, setAdding] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [emailsKey, setEmailsKey] = useState(0) // cambia dopo ogni operazione: il riepilogo email si ricarica

  const remind = async () => {
    setSaving(true)
    setActionError(null)
    try {
      const { queued } = await api.post(`/payments/${id}/remind`)
      setNotice(queued > 0 ? `Promemoria accodati: ${queued}.` : 'Promemoria già inviati oggi a tutti i destinatari.')
      setEmailsKey((k) => k + 1)
    } catch (err) {
      setActionError(err)
    } finally {
      setSaving(false)
    }
  }

  const run = async (call) => {
    setSaving(true)
    setActionError(null)
    setNotice(null)
    try {
      setData(await call())
      setEmailsKey((k) => k + 1)
      return true
    } catch (err) {
      setActionError(err)
      return false
    } finally {
      setSaving(false)
      setPending(null)
    }
  }

  const confirmPending = () =>
    run(() =>
      pending.action === 'cancel'
        ? api.post(`/payments/${id}/cancel`, { reason, version: data.request.version })
        : api.post(`/payments/${id}/assignments/${pending.assignment.id}/${pending.action}`, {
            version: pending.assignment.version,
          }),
    )

  if (loading) return <LoadingSpinner label="Caricamento richiesta…" />
  if (error) return <ErrorAlert error={error} onRetry={reload} />

  const { request, assignments } = data
  const editable = request.status !== 'CANCELLED'
  return (
    <>
      <p className="mb-2">
        <Link to="/admin/payments">‹ Richieste di pagamento</Link>
      </p>
      <ErrorAlert error={actionError} />
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}
      <div className="card shadow-sm border-0 mb-3">
        <div className="card-body">
          <div className="d-flex flex-wrap justify-content-between align-items-start gap-2">
            <div>
              <h1 className="h4 mb-1">{request.description}</h1>
              <p className="text-secondary mb-0">
                {formatEuro(request.amountCents)} a testa
                {request.dueDate && ` · scadenza ${formatPlainDate(request.dueDate)}`} · creata il{' '}
                {formatDateTime(request.createdAt)}
              </p>
            </div>
            <Badge map={REQUEST_STATUS} status={request.status} />
          </div>
          {request.status === 'CANCELLED' && (
            <div className="alert alert-secondary mt-3 mb-0">
              Richiesta annullata{request.cancellationReason ? `: ${request.cancellationReason}` : '.'}
            </div>
          )}
          {request.notes && <p className="mt-2 mb-0" style={{ whiteSpace: 'pre-line' }}>{request.notes}</p>}
          <dl className="row row-cols-3 g-2 mt-2 mb-0 text-center">
            <div className="col">
              <dt className="small text-secondary fw-normal">Pagate</dt>
              <dd className="h5 mb-0">{request.paidCount}/{request.paidCount + request.pendingCount}</dd>
            </div>
            <div className="col">
              <dt className="small text-secondary fw-normal">Incassato</dt>
              <dd className="h5 mb-0">{formatEuro(request.collectedCents)}</dd>
            </div>
            <div className="col">
              <dt className="small text-secondary fw-normal">Da incassare</dt>
              <dd className="h5 mb-0">{formatEuro(request.outstandingCents)}</dd>
            </div>
          </dl>
          <div className="mt-3">
            <EmailSummary key={emailsKey} relatedType="PAYMENT_REQUEST" relatedId={request.id}
              label="Email ai destinatari" />
          </div>
          {editable && (
            <div className="d-flex flex-wrap gap-2 mt-3">
              <Link className="btn btn-outline-primary btn-sm" to={`/admin/payments/${request.id}/edit`}>
                Modifica
              </Link>
              <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => setAdding((v) => !v)}>
                Aggiungi destinatari
              </button>
              {request.status === 'OPEN' && (
                <button type="button" className="btn btn-outline-primary btn-sm" disabled={saving} onClick={remind}>
                  Invia promemoria
                </button>
              )}
              {request.status === 'OPEN' && (
                <button type="button" className="btn btn-outline-danger btn-sm" onClick={() => setPending({ action: 'cancel' })}>
                  Annulla richiesta
                </button>
              )}
            </div>
          )}
          {adding && editable && (
            <AddRecipients assigned={assignments.map((a) => a.userId)} saving={saving}
              onAdd={async (ids) => {
                if (await run(() => api.post(`/payments/${id}/assignments`, { userIds: ids }))) setAdding(false)
              }} />
          )}
        </div>
      </div>

      <div className="card shadow-sm border-0">
        <ul className="list-group list-group-flush" aria-label="Quote">
          {assignments.map((a) => (
            <li key={a.id} className="list-group-item">
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                <div>
                  <span className="fw-semibold">{a.userName}</span> <Badge map={QUOTA_STATUS} status={a.status} />
                  {a.status === 'PAID' && (
                    <div className="small text-secondary">
                      Pagata il {formatPlainDate(a.paidOn)} · {METHODS[a.paymentMethod]}
                      {a.confirmedBy && ` · registrata da ${a.confirmedBy}`}
                      {a.adminNote && ` · ${a.adminNote}`}
                    </div>
                  )}
                </div>
                {editable && (
                  <span className="text-nowrap">
                    {a.status === 'PENDING' && (
                      <>
                        <button type="button" className="btn btn-sm btn-success" onClick={() => setConfirming(a)}>
                          Registra pagamento
                        </button>
                        <button type="button" className="btn btn-sm btn-link"
                          onClick={() => setPending({ action: 'waive', assignment: a })}>
                          Esonera
                        </button>
                      </>
                    )}
                    {a.status !== 'PENDING' && (
                      <button type="button" className="btn btn-sm btn-link"
                        onClick={() => setPending({ action: 'reopen', assignment: a })}>
                        Riporta a da pagare
                      </button>
                    )}
                  </span>
                )}
              </div>
              {confirming?.id === a.id && (
                <ConfirmPaymentForm saving={saving} onCancel={() => setConfirming(null)}
                  onSubmit={async (body) => {
                    const ok = await run(() =>
                      api.post(`/payments/${id}/assignments/${a.id}/confirm`, { ...body, version: a.version }))
                    if (ok) setConfirming(null)
                  }} />
              )}
            </li>
          ))}
        </ul>
      </div>

      <ConfirmDialog
        open={Boolean(pending)}
        title={{ cancel: 'Annulla richiesta', waive: 'Esonera', reopen: 'Riporta a da pagare' }[pending?.action]}
        confirmLabel={{ cancel: 'Annulla richiesta', waive: 'Esonera', reopen: 'Riporta a da pagare' }[pending?.action]}
        confirmVariant={pending?.action === 'cancel' ? 'danger' : 'primary'}
        loading={saving}
        onConfirm={confirmPending}
        onCancel={() => setPending(null)}
        message={
          pending?.action === 'cancel' ? (
            <>
              <p>Le quote ancora da pagare verranno annullate; quelle già pagate restano registrate.</p>
              <label htmlFor="cancel-reason" className="form-label">
                Motivo (facoltativo)
              </label>
              <textarea id="cancel-reason" className="form-control" rows="2" maxLength={500} value={reason}
                onChange={(e) => setReason(e.target.value)} />
            </>
          ) : pending?.action === 'waive' ? (
            `${pending.assignment.userName} non dovrà pagare questa quota.`
          ) : (
            pending && `La quota di ${pending.assignment.userName} tornerà da pagare. L'operazione resta nell'audit log.`
          )
        }
      />
    </>
  )
}

/** Registrazione manuale di un pagamento ricevuto: data (non futura), metodo e nota interna. */
function ConfirmPaymentForm({ saving, onSubmit, onCancel }) {
  const [today] = useState(() => teamDateKey(new Date()))
  const [values, setValues] = useState({ paidOn: today, method: 'CASH', note: '' })
  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }))
  return (
    <form className="row g-2 align-items-end border rounded p-2 mt-2" aria-label="Registra pagamento" noValidate
      onSubmit={(e) => { e.preventDefault(); onSubmit(values) }}>
      <div className="col-sm-3">
        <label htmlFor="paidOn" className="form-label small mb-1">
          Data
        </label>
        <input id="paidOn" type="date" max={today} className="form-control form-control-sm" value={values.paidOn}
          onChange={set('paidOn')} />
      </div>
      <div className="col-sm-3">
        <label htmlFor="method" className="form-label small mb-1">
          Metodo
        </label>
        <select id="method" className="form-select form-select-sm" value={values.method} onChange={set('method')}>
          {Object.entries(METHODS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <div className="col-sm-6">
        <label htmlFor="note" className="form-label small mb-1">
          Nota interna (facoltativa)
        </label>
        <input id="note" className="form-control form-control-sm" maxLength={500} value={values.note} onChange={set('note')} />
      </div>
      <div className="col-12 d-flex gap-2">
        <button type="submit" className="btn btn-sm btn-success" disabled={saving || !values.paidOn}>
          Conferma pagamento
        </button>
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={onCancel}>
          Annulla
        </button>
      </div>
    </form>
  )
}

/** Utenti attivi non ancora tra i destinatari. */
function AddRecipients({ assigned, saving, onAdd }) {
  const { data, error, reload } = useApiData('/users?status=ACTIVE&size=100')
  const [selected, setSelected] = useState([])
  if (error) return <ErrorAlert error={error} onRetry={reload} />
  if (!data) return <LoadingSpinner label="Caricamento utenti…" />
  const available = data.content.filter((u) => !assigned.includes(u.id))
  if (available.length === 0) return <p className="small text-secondary mt-3 mb-0">Tutti gli utenti attivi sono già destinatari.</p>
  return (
    <div className="border rounded p-2 mt-3">
      <RecipientPicker users={available} selected={selected} onChange={setSelected} />
      <button type="button" className="btn btn-sm btn-primary" disabled={saving || selected.length === 0}
        onClick={() => onAdd(selected)}>
        Aggiungi {selected.length > 0 ? selected.length : ''}
      </button>
    </div>
  )
}
