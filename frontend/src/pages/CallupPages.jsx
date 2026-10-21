import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import { SubmitButton } from '../components/common/Form'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatDateTime, formatTime, toLocalInputValue } from '../utils/dates'
import { useApiData } from '../utils/useApiData'
import { EmailSummary } from './EmailPages'

const CALLUP_STATUS = {
  DRAFT: { label: 'Bozza', badge: 'text-bg-warning' },
  PUBLISHED: { label: 'Pubblicata', badge: 'text-bg-success' },
  WITHDRAWN: { label: 'Ritirata', badge: 'text-bg-secondary' },
}

const ACTION_LABELS = { PUBLISHED: 'Pubblicata', UPDATED: 'Modificata', WITHDRAWN: 'Ritirata' }

function CallupStatusBadge({ status }) {
  return <span className={`badge ${CALLUP_STATUS[status].badge}`}>{CALLUP_STATUS[status].label}</span>
}

const fullName = (p) => `${p.firstName} ${p.lastName}`

function WarningList({ warnings }) {
  return (
    <ul className="mb-0">
      {warnings.map((w) => (
        <li key={w.userId}>
          {fullName(w)}: {w.availability === 'UNAVAILABLE' ? 'non disponibile' : 'nessuna risposta'}
        </li>
      ))}
    </ul>
  )
}

/** Riquadro della convocazione nel dettaglio partita. */
export function CallupCard({ match }) {
  const { hasRole } = useAuth()
  const staff = hasRole('COACH')
  const { data: callup, error, loading, reload } = useApiData(`/matches/${match.id}/callup`)
  const [dialog, setDialog] = useState(null) // 'publish' | 'withdraw'
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState(null)
  const [showHistory, setShowHistory] = useState(false)
  // Letto una volta al montaggio: il render resta puro.
  const [now] = useState(() => Date.now())

  const notFound = error?.status === 404
  const canManage = staff && match.status === 'SCHEDULED' && new Date(match.kickoffAt).getTime() > now

  const runAction = async () => {
    setSaving(true)
    setActionError(null)
    try {
      if (dialog === 'publish') {
        await api.post(`/matches/${match.id}/callup/publish`, {
          version: callup.version,
          confirmWarnings: callup.warnings.length > 0,
        })
      } else {
        await api.post(`/matches/${match.id}/callup/withdraw`, { version: callup.version, reason })
      }
      reload()
    } catch (err) {
      setActionError(err)
    } finally {
      setSaving(false)
      setDialog(null)
    }
  }

  if (loading) return <LoadingSpinner label="Caricamento convocazione…" />
  if (error && !notFound) return <ErrorAlert error={error} onRetry={reload} />

  return (
    <div className="card shadow-sm border-0 mt-3">
      <div className="card-body">
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
          <h2 className="h5 mb-0">Convocazione</h2>
          {callup && <CallupStatusBadge status={callup.status} />}
        </div>
        <ErrorAlert error={actionError} />

        {notFound && (
          <p className="text-secondary mb-2">
            {staff ? 'Nessuna convocazione preparata.' : 'Convocazione non ancora pubblicata.'}
          </p>
        )}

        {callup && (
          <>
            {callup.status === 'WITHDRAWN' && (
              <div className="alert alert-secondary py-2">
                Convocazione ritirata{callup.withdrawalReason ? `: ${callup.withdrawalReason}` : '.'}
              </div>
            )}
            {callup.convocated && callup.status === 'PUBLISHED' && (
              <div className="alert alert-success py-2">Sei convocato per questa partita.</div>
            )}
            <dl className="row small mb-2">
              {callup.meetingAt && (
                <>
                  <dt className="col-sm-3">Ritrovo</dt>
                  <dd className="col-sm-9">ore {formatTime(callup.meetingAt)}</dd>
                </>
              )}
              {callup.notes && (
                <>
                  <dt className="col-sm-3">Note</dt>
                  <dd className="col-sm-9" style={{ whiteSpace: 'pre-line' }}>
                    {callup.notes}
                  </dd>
                </>
              )}
              {callup.publishedAt && (
                <>
                  <dt className="col-sm-3">Pubblicata</dt>
                  <dd className="col-sm-9">
                    {formatDateTime(callup.publishedAt)} da {callup.publishedBy} (revisione {callup.revision})
                  </dd>
                </>
              )}
            </dl>
            <h3 className="h6">Convocati ({callup.players.length})</h3>
            <ul className="list-inline mb-2">
              {callup.players.map((p) => (
                <li key={p.userId} className="list-inline-item mb-1">
                  <span className="badge text-bg-light border">
                    {fullName(p)}
                    {staff && p.availability !== 'AVAILABLE' && (
                      <span className={p.availability === 'UNAVAILABLE' ? 'text-danger' : 'text-secondary'}> ●</span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
            {staff && callup.revision > 0 && <EmailSummary relatedType="CALLUP" relatedId={callup.id} label="Email ai convocati" />}
            {staff && callup.warnings.length > 0 && (
              <div className="alert alert-warning py-2 small">
                Convocati senza disponibilità confermata:
                <WarningList warnings={callup.warnings} />
              </div>
            )}
          </>
        )}

        {canManage && (
          <div className="d-flex flex-wrap gap-2">
            <Link className="btn btn-sm btn-outline-primary" to={`/matches/${match.id}/callup`}>
              {callup ? 'Modifica convocazione' : 'Prepara convocazione'}
            </Link>
            {callup?.status === 'DRAFT' && (
              <button type="button" className="btn btn-sm btn-success" onClick={() => setDialog('publish')}
                disabled={callup.players.length === 0}>
                Pubblica
              </button>
            )}
            {callup?.status === 'PUBLISHED' && (
              <button type="button" className="btn btn-sm btn-outline-danger" onClick={() => setDialog('withdraw')}>
                Ritira
              </button>
            )}
          </div>
        )}

        {staff && callup && callup.revision > 0 && (
          <div className="mt-3">
            <button type="button" className="btn btn-link btn-sm p-0" onClick={() => setShowHistory((v) => !v)}>
              {showHistory ? 'Nascondi storico' : 'Mostra storico delle revisioni'}
            </button>
            {showHistory && <RevisionHistory matchId={match.id} />}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={dialog === 'publish'}
        title="Pubblica convocazione"
        confirmLabel="Pubblica"
        confirmVariant="success"
        loading={saving}
        onConfirm={runAction}
        onCancel={() => setDialog(null)}
        message={
          callup && (
            <>
              <p>
                La convocazione con {callup.players.length} convocati diventerà visibile a tutti e le disponibilità
                verranno bloccate. Ogni convocato riceverà un'email con i dettagli.
              </p>
              {callup.warnings.length > 0 && (
                <div className="alert alert-warning mb-0">
                  <strong>Attenzione:</strong> stai convocando anche
                  <WarningList warnings={callup.warnings} />
                </div>
              )}
            </>
          )
        }
      />
      <ConfirmDialog
        open={dialog === 'withdraw'}
        title="Ritira convocazione"
        confirmLabel="Ritira"
        confirmVariant="danger"
        loading={saving}
        onConfirm={runAction}
        onCancel={() => setDialog(null)}
        message={
          <>
            <p>
              La convocazione resterà nello storico come ritirata e le disponibilità torneranno modificabili. I
              convocati riceveranno un'email di avviso.
            </p>
            <label htmlFor="withdraw-reason" className="form-label">
              Motivo (facoltativo)
            </label>
            <textarea id="withdraw-reason" className="form-control" rows="2" maxLength={500} value={reason}
              onChange={(e) => setReason(e.target.value)} />
          </>
        }
      />
    </div>
  )
}

function RevisionHistory({ matchId }) {
  const { data, error, loading } = useApiData(`/matches/${matchId}/callup/revisions`)
  if (loading) return <LoadingSpinner />
  if (error) return <ErrorAlert error={error} />
  return (
    <ol className="list-unstyled small mt-2 mb-0" reversed>
      {data.map((r) => (
        <li key={r.revision} className="border-start ps-2 mb-2">
          <strong>
            Revisione {r.revision} · {ACTION_LABELS[r.action]}
          </strong>{' '}
          <span className="text-secondary">
            {formatDateTime(r.createdAt)} da {r.author}
          </span>
          {r.changeSummary && <div>{r.changeSummary}</div>}
        </li>
      ))}
    </ol>
  )
}

/** Editor della convocazione (Allenatore e Admin): scelta dei convocati in base alle disponibilità. */
export function CallupEditorPage() {
  const { id } = useParams()
  const match = useApiData(`/matches/${id}`)
  const team = useApiData(`/matches/${id}/availability`)
  const callup = useApiData(`/matches/${id}/callup`)

  if (match.loading || team.loading || callup.loading) return <LoadingSpinner label="Caricamento…" />
  const loadError = match.error || team.error || (callup.error?.status !== 404 ? callup.error : null)
  if (loadError) return <ErrorAlert error={loadError} />
  return <CallupEditor match={match.data} team={team.data} existing={callup.data} />
}

const GROUPS = [
  { status: 'AVAILABLE', title: 'Disponibili' },
  { status: 'PENDING', title: 'In attesa di risposta' },
  { status: 'UNAVAILABLE', title: 'Non disponibili' },
]

function CallupEditor({ match, team, existing }) {
  const navigate = useNavigate()
  const [selected, setSelected] = useState(() => new Set(existing?.players.map((p) => p.userId) ?? []))
  const [meetingAt, setMeetingAt] = useState(existing?.meetingAt ? toLocalInputValue(existing.meetingAt) : '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const published = existing?.status === 'PUBLISHED'
  const warnings = team.players.filter((p) => selected.has(p.userId) && p.status !== 'AVAILABLE')
    .map((p) => ({ ...p, availability: p.status }))

  const toggle = (userId) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(userId)) next.delete(userId)
      else next.add(userId)
      return next
    })

  const selectAllAvailable = () =>
    setSelected((current) => new Set([...current, ...team.players.filter((p) => p.status === 'AVAILABLE').map((p) => p.userId)]))

  const save = async (confirmWarnings) => {
    setSaving(true)
    setError(null)
    try {
      await api.put(`/matches/${match.id}/callup`, {
        meetingAt: meetingAt || null,
        notes,
        playerIds: [...selected],
        version: existing?.version ?? null,
        confirmWarnings,
      })
      navigate(`/matches/${match.id}`)
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
      setConfirmOpen(false)
    }
  }

  const onSubmit = (event) => {
    event.preventDefault()
    // Su una convocazione pubblicata gli avvisi vanno confermati prima del salvataggio.
    if (published && warnings.length > 0) setConfirmOpen(true)
    else save(false)
  }

  return (
    <>
      <p className="mb-2">
        <Link to={`/matches/${match.id}`}>‹ Torna alla partita</Link>
      </p>
      <div className="card shadow-sm border-0">
        <div className="card-body p-4">
          <h1 className="h4 mb-1">Convocazione vs {match.opponent}</h1>
          <p className="text-secondary">
            {formatDateTime(match.kickoffAt)} · {match.venueName}
            {existing && (
              <>
                {' '}
                · <CallupStatusBadge status={existing.status} />
              </>
            )}
          </p>
          {published && (
            <div className="alert alert-info py-2 small">
              La convocazione è già pubblicata: le modifiche saranno visibili subito e registrate come nuova revisione.
              Riceveranno un'email solo i nuovi convocati, quelli rimossi e, se cambiano ritrovo o note, gli altri
              convocati.
            </div>
          )}
          <ErrorAlert error={error} />
          <form onSubmit={onSubmit} noValidate>
            <div className="d-flex justify-content-between align-items-center mb-2">
              <h2 className="h6 mb-0">Convocati: {selected.size}</h2>
              <button type="button" className="btn btn-sm btn-outline-success" onClick={selectAllAvailable}>
                Seleziona tutti i disponibili
              </button>
            </div>
            <div className="row g-3 mb-3">
              {GROUPS.map((group) => {
                const players = team.players.filter((p) => p.status === group.status)
                return (
                  <div key={group.status} className="col-md-4">
                    <div className="border rounded p-2 h-100">
                      <div className="small fw-semibold mb-1">
                        {group.title} ({players.length})
                      </div>
                      {players.length === 0 && <div className="small text-secondary">Nessuno</div>}
                      {players.map((p) => (
                        <div key={p.userId} className="form-check">
                          <input id={`player-${p.userId}`} type="checkbox" className="form-check-input"
                            checked={selected.has(p.userId)} onChange={() => toggle(p.userId)} />
                          <label htmlFor={`player-${p.userId}`} className="form-check-label">
                            {p.lastName} {p.firstName}
                          </label>
                          {p.note && <div className="small text-secondary">{p.note}</div>}
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>
            {warnings.length > 0 && (
              <div className="alert alert-warning py-2 small">
                Stai convocando persone senza disponibilità confermata: ti verrà chiesta una conferma.
              </div>
            )}
            <div className="row g-2">
              <div className="col-md-4 mb-3">
                <label htmlFor="meetingAt" className="form-label">
                  Ritrovo (facoltativo, ora italiana)
                </label>
                <input id="meetingAt" type="datetime-local" className="form-control" value={meetingAt}
                  onChange={(e) => setMeetingAt(e.target.value)} />
              </div>
              <div className="col-md-8 mb-3">
                <label htmlFor="callup-notes" className="form-label">
                  Note per i convocati (facoltative)
                </label>
                <textarea id="callup-notes" className="form-control" rows="2" maxLength={2000} value={notes}
                  onChange={(e) => setNotes(e.target.value)} />
              </div>
            </div>
            <div className="d-flex gap-2">
              <SubmitButton loading={saving} loadingLabel="Salvataggio…" className="btn btn-primary">
                {published ? 'Salva modifiche' : 'Salva bozza'}
              </SubmitButton>
              <Link className="btn btn-outline-secondary" to={`/matches/${match.id}`}>
                Annulla
              </Link>
            </div>
            {!published && (
              <p className="small text-secondary mt-2 mb-0">
                La bozza è visibile solo ad allenatori e amministratori; la pubblicazione si fa dalla pagina della
                partita.
              </p>
            )}
          </form>
        </div>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="Conferma convocati"
        confirmLabel="Salva comunque"
        confirmVariant="warning"
        loading={saving}
        onConfirm={() => save(true)}
        onCancel={() => setConfirmOpen(false)}
        message={
          <>
            <p>Stai convocando anche:</p>
            <WarningList warnings={warnings} />
          </>
        }
      />
    </>
  )
}

/** Riquadro della home con le convocazioni pubblicate dell'utente. */
export function MyCallupsCard() {
  const { data, error, loading, reload } = useApiData('/callups/me')
  return (
    <div className="card shadow-sm border-0 mb-4">
      <div className="card-body">
        <h2 className="h5">Le tue convocazioni</h2>
        <ErrorAlert error={error} onRetry={reload} />
        {loading && <LoadingSpinner />}
        {data?.length === 0 && <p className="text-secondary mb-0">Nessuna convocazione per le prossime partite.</p>}
        {data?.length > 0 && (
          <div className="list-group list-group-flush">
            {data.map((c) => (
              <Link key={c.match.id} to={`/matches/${c.match.id}`} className="list-group-item list-group-item-action">
                <div className="fw-semibold">vs {c.match.opponent}</div>
                <div className="small text-secondary">
                  {formatDateTime(c.match.kickoffAt)}
                  {c.meetingAt && ` · ritrovo ore ${formatTime(c.meetingAt)}`}
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

