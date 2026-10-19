import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { ROLE_LABELS, STATUS_LABELS, useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'

const FILTERS = [
  { status: 'PENDING', label: 'In attesa' },
  { status: 'ACTIVE', label: 'Attivi' },
  { status: 'SUSPENDED', label: 'Sospesi' },
  { status: 'REJECTED', label: 'Rifiutati' },
  { status: '', label: 'Tutti' },
]

const STATUS_BADGE = {
  PENDING: 'text-bg-warning',
  ACTIVE: 'text-bg-success',
  SUSPENDED: 'text-bg-secondary',
  REJECTED: 'text-bg-danger',
}

/** Azioni disponibili per ogni stato, con testi della conferma. */
const ACTIONS = {
  approve: { label: 'Approva', variant: 'success', verb: 'approvare', from: ['PENDING'] },
  reject: { label: 'Rifiuta', variant: 'outline-danger', verb: 'rifiutare', from: ['PENDING'] },
  suspend: {
    label: 'Sospendi',
    variant: 'outline-danger',
    verb: 'sospendere',
    from: ['ACTIVE'],
    note: "L'utente verrà disconnesso da tutti i dispositivi.",
  },
  reactivate: { label: 'Riattiva', variant: 'outline-success', verb: 'riattivare', from: ['SUSPENDED', 'REJECTED'] },
}

const PAGE_SIZE = 20

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth()
  const [filter, setFilter] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  // Operazione in attesa di conferma: { kind: 'status', action, user } oppure { kind: 'role', role, user }
  const [pending, setPending] = useState(null)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    const query = new URLSearchParams({ page, size: PAGE_SIZE })
    if (filter) query.set('status', filter)
    api
      .get(`/users?${query}`, { signal: controller.signal })
      .then((result) => {
        setData(result)
        setError(null)
      })
      .catch((err) => err.name !== 'AbortError' && setError(err))
    return () => controller.abort()
  }, [filter, page, reloadKey])

  const changeFilter = (status) => {
    setFilter(status)
    setPage(0)
    setData(null)
    setNotice(null)
  }

  const confirm = async () => {
    setSaving(true)
    setError(null)
    try {
      const { user } = pending
      if (pending.kind === 'role') {
        await api.put(`/users/${user.id}/role`, { role: pending.role })
        setNotice(`Ruolo di ${user.firstName} ${user.lastName} aggiornato a ${ROLE_LABELS[pending.role]}.`)
      } else {
        await api.post(`/users/${user.id}/${pending.action}`)
        setNotice(`Operazione "${ACTIONS[pending.action].label}" completata per ${user.firstName} ${user.lastName}.`)
      }
      setReloadKey((key) => key + 1)
    } catch (err) {
      setError(err)
      setNotice(null)
    } finally {
      setSaving(false)
      setPending(null)
    }
  }

  const dialog = pending && dialogText(pending)

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">Gestione utenti</h1>
        <div className="btn-group flex-wrap" role="group" aria-label="Filtra per stato">
          {FILTERS.map((f) => (
            <button key={f.label} type="button"
              className={`btn btn-sm ${filter === f.status ? 'btn-primary' : 'btn-outline-primary'}`}
              onClick={() => changeFilter(f.status)}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}
      <ErrorAlert error={error} />
      {!data && !error && <LoadingSpinner label="Caricamento utenti…" />}

      {data && (
        <div className="card shadow-sm border-0">
          <div className="table-responsive">
            <table className="table align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th>Nome</th>
                  <th>Email</th>
                  <th>Ruolo</th>
                  <th>Stato</th>
                  <th className="text-end">Azioni</th>
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan="5" className="text-center text-secondary py-4">
                      Nessun utente in questo elenco.
                    </td>
                  </tr>
                )}
                {data.content.map((u) => {
                  const isSelf = u.id === currentUser.id
                  return (
                    <tr key={u.id}>
                      <td>
                        {u.firstName} {u.lastName}
                        {isSelf && <span className="badge text-bg-light ms-1">tu</span>}
                      </td>
                      <td className="text-break">
                        {u.email}{' '}
                        {!u.emailVerified && (
                          <span className="badge text-bg-warning" title="L'utente non ha ancora confermato l'email">
                            email non verificata
                          </span>
                        )}
                      </td>
                      <td>
                        <select className="form-select form-select-sm" aria-label={`Ruolo di ${u.firstName}`}
                          value={u.role} onChange={(e) => setPending({ kind: 'role', role: e.target.value, user: u })}>
                          {Object.entries(ROLE_LABELS).map(([value, label]) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <span className={`badge ${STATUS_BADGE[u.status]}`}>{STATUS_LABELS[u.status]}</span>
                      </td>
                      <td className="text-end text-nowrap">
                        {!isSelf &&
                          Object.entries(ACTIONS)
                            .filter(([, a]) => a.from.includes(u.status))
                            .map(([action, a]) => {
                              const blocked = action === 'approve' && !u.emailVerified
                              return (
                                <button key={action} type="button" className={`btn btn-sm btn-${a.variant} ms-1`}
                                  disabled={blocked}
                                  title={blocked ? "Serve la conferma dell'email da parte dell'utente" : undefined}
                                  onClick={() => setPending({ kind: 'status', action, user: u })}>
                                  {a.label}
                                </button>
                              )
                            })}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {data.totalPages > 1 && (
            <div className="card-footer bg-white d-flex justify-content-between align-items-center">
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}>
                Precedente
              </button>
              <span className="small text-secondary">
                Pagina {data.page + 1} di {data.totalPages}
              </span>
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}>
                Successiva
              </button>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(pending)}
        title={dialog?.title}
        message={dialog?.message}
        confirmLabel={dialog?.confirmLabel}
        confirmVariant={dialog?.variant}
        loading={saving}
        onConfirm={confirm}
        onCancel={() => setPending(null)}
      />
    </>
  )
}

function dialogText(pending) {
  const name = `${pending.user.firstName} ${pending.user.lastName}`
  if (pending.kind === 'role') {
    return {
      title: 'Cambia ruolo',
      message: `Vuoi assegnare a ${name} il ruolo ${ROLE_LABELS[pending.role]}?`,
      confirmLabel: 'Cambia ruolo',
      variant: 'primary',
    }
  }
  const action = ACTIONS[pending.action]
  return {
    title: `${action.label} utente`,
    message: `Vuoi ${action.verb} ${name}? ${action.note ?? ''}`,
    confirmLabel: action.label,
    variant: action.variant.replace('outline-', ''),
  }
}
