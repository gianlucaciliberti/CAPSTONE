import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { ROLE_LABELS, useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatDateTime } from '../utils/dates'
import { useApiData } from '../utils/useApiData'

const AVAILABILITY = {
  AVAILABLE: { label: 'Disponibile', short: 'Sì', variant: 'success' },
  UNAVAILABLE: { label: 'Non disponibile', short: 'No', variant: 'danger' },
  PENDING: { label: 'In attesa di risposta', short: '?', variant: 'secondary' },
}

export function AvailabilityBadge({ status }) {
  const a = AVAILABILITY[status]
  return <span className={`badge text-bg-${a.variant}`}>{a.label}</span>
}

/**
 * Pulsanti per scegliere la propria disponibilità. Salva subito al clic (PUT .../availability/me)
 * e restituisce la risposta del server con `onSaved`.
 */
function AvailabilityChooser({ matchId, value, note, editable, onSaved, compact = false }) {
  const [saving, setSaving] = useState(null)
  const [error, setError] = useState(null)

  const choose = async (status) => {
    setSaving(status)
    setError(null)
    try {
      onSaved(await api.put(`/matches/${matchId}/availability/me`, { status, note }))
    } catch (err) {
      setError(err)
    } finally {
      setSaving(null)
    }
  }

  return (
    <>
      <div className="btn-group flex-wrap" role="group" aria-label="La tua disponibilità">
        {Object.entries(AVAILABILITY).map(([status, a]) => (
          <button key={status} type="button" disabled={!editable || saving !== null} aria-pressed={value === status}
            className={`btn btn-sm ${value === status ? `btn-${a.variant}` : `btn-outline-${a.variant}`}`}
            onClick={() => choose(status)}>
            {saving === status && <span className="spinner-border spinner-border-sm me-1" aria-hidden="true" />}
            {compact ? a.short : a.label}
          </button>
        ))}
      </div>
      {error && <div className="small text-danger mt-1">{error.message}</div>}
    </>
  )
}

/** Riquadro "La tua disponibilità" nel dettaglio partita. */
export function MyAvailabilityCard({ matchId }) {
  const { data, error, loading, reload, setData } = useApiData(`/matches/${matchId}/availability/me`)
  const [note, setNote] = useState(null)

  if (loading) return <LoadingSpinner />
  if (error) return <ErrorAlert error={error} onRetry={reload} />
  const currentNote = note ?? data.note ?? ''

  return (
    <div className="card shadow-sm border-0 mt-3">
      <div className="card-body">
        <h2 className="h5">La tua disponibilità</h2>
        {!data.editable && <div className="alert alert-info py-2 small">{data.lockReason}</div>}
        {data.adminOverride && (
          <p className="small text-secondary">Ultima modifica effettuata da un amministratore.</p>
        )}
        <AvailabilityChooser matchId={matchId} value={data.status} note={currentNote} editable={data.editable}
          onSaved={(saved) => {
            setData(saved)
            setNote(null)
          }} />
        {data.editable && (
          <div className="mt-2">
            <label htmlFor="availability-note" className="form-label small mb-1">
              Nota per l'allenatore (facoltativa, salvata con la scelta)
            </label>
            <input id="availability-note" className="form-control form-control-sm" maxLength={500}
              value={currentNote} onChange={(e) => setNote(e.target.value)} placeholder="Es. arrivo alle 20:30" />
          </div>
        )}
        {!data.editable && data.note && <p className="small mt-2 mb-0">Nota: {data.note}</p>}
      </div>
    </div>
  )
}

/** Riepilogo della squadra (Allenatore e Admin); l'Admin può correggere le singole disponibilità. */
export function TeamAvailabilityCard({ matchId }) {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const { data, error, loading, reload } = useApiData(`/matches/${matchId}/availability`)
  const [pending, setPending] = useState(null)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState(null)

  const confirmOverride = async () => {
    setSaving(true)
    setActionError(null)
    try {
      await api.put(`/matches/${matchId}/availability/${pending.player.userId}`, {
        status: pending.status,
        note: pending.player.note,
      })
      reload()
    } catch (err) {
      setActionError(err)
    } finally {
      setSaving(false)
      setPending(null)
    }
  }

  if (loading) return <LoadingSpinner label="Caricamento disponibilità squadra…" />
  if (error) return <ErrorAlert error={error} onRetry={reload} />

  return (
    <div className="card shadow-sm border-0 mt-3">
      <div className="card-body">
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
          <h2 className="h5 mb-0">Disponibilità della squadra</h2>
          <div className="d-flex gap-2 small">
            {Object.entries(AVAILABILITY).map(([status, a]) => (
              <span key={status} className={`badge text-bg-${a.variant}`}>
                {a.label}: {data.counts[status]}
              </span>
            ))}
          </div>
        </div>
        <ErrorAlert error={actionError} />
        <div className="table-responsive">
          <table className="table table-sm align-middle mb-0">
            <thead>
              <tr>
                <th>Nome</th>
                <th>Ruolo</th>
                <th>Disponibilità</th>
                <th>Nota</th>
              </tr>
            </thead>
            <tbody>
              {data.players.map((p) => (
                <tr key={p.userId}>
                  <td>
                    {p.lastName} {p.firstName}
                  </td>
                  <td className="small text-secondary">{ROLE_LABELS[p.role]}</td>
                  <td>
                    {isAdmin ? (
                      <select className="form-select form-select-sm" value={p.status}
                        aria-label={`Disponibilità di ${p.firstName} ${p.lastName}`}
                        onChange={(e) => setPending({ player: p, status: e.target.value })}>
                        {Object.entries(AVAILABILITY).map(([status, a]) => (
                          <option key={status} value={status}>
                            {a.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <AvailabilityBadge status={p.status} />
                    )}
                    {p.adminOverride && <div className="small text-secondary">corretta da Admin</div>}
                  </td>
                  <td className="small">{p.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <ConfirmDialog
        open={Boolean(pending)}
        title="Correzione amministrativa"
        confirmLabel="Correggi"
        loading={saving}
        onConfirm={confirmOverride}
        onCancel={() => setPending(null)}
        message={
          pending && (
            <p className="mb-0">
              Vuoi impostare <strong>{pending.player.firstName} {pending.player.lastName}</strong> come{' '}
              <strong>{AVAILABILITY[pending.status].label.toLowerCase()}</strong>? La correzione viene registrata
              nell'audit log{data.editable ? '' : ' ed è possibile anche a convocazioni pubblicate'}.
            </p>
          )
        }
      />
    </div>
  )
}

/** Pagina "Disponibilità": le prossime partite con la propria risposta, modificabile al volo. */
export function AvailabilityPage() {
  const { data, error, loading, reload, setData } = useApiData('/availability/me/upcoming?limit=20')

  const updateOne = (matchId, availability) =>
    setData(data.map((item) => (item.match.id === matchId ? { ...item, availability } : item)))

  return (
    <>
      <h1 className="h4 mb-1">Le mie disponibilità</h1>
      <p className="text-secondary">
        Rispondi per le prossime partite. Puoi cambiare idea fino alla pubblicazione delle convocazioni.
      </p>
      <ErrorAlert error={error} onRetry={reload} />
      {loading && <LoadingSpinner label="Caricamento partite…" />}
      {data?.length === 0 && <p className="text-secondary">Nessuna partita in programma.</p>}
      {data?.length > 0 && (
        <div className="list-group shadow-sm">
          {data.map(({ match, availability }) => (
            <div key={match.id} className="list-group-item">
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                <div>
                  <Link to={`/matches/${match.id}`} className="fw-semibold">
                    vs {match.opponent}
                  </Link>
                  <div className="small text-secondary">
                    {formatDateTime(match.kickoffAt)} · {match.venueName}
                  </div>
                  {!availability.editable && <div className="small text-info-emphasis">{availability.lockReason}</div>}
                </div>
                <AvailabilityChooser matchId={match.id} value={availability.status} note={availability.note}
                  editable={availability.editable} onSaved={(saved) => updateOne(match.id, saved)} />
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  )
}
