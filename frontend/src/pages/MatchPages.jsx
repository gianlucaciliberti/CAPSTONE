import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import { FormField, SubmitButton } from '../components/common/Form'
import LoadingSpinner from '../components/common/LoadingSpinner'
import {
  addMonths,
  buildMonthGrid,
  currentTeamMonth,
  formatDateTime,
  formatLongDate,
  formatMonthTitle,
  formatTime,
  monthKey,
  parseMonth,
  teamDateKey,
  toLocalInputValue,
  WEEKDAYS,
} from '../utils/dates'
import { useForm } from '../utils/useForm'
import { requiredError } from '../utils/validation'

const STATUS = {
  SCHEDULED: { label: 'Programmata', badge: 'text-bg-primary' },
  COMPLETED: { label: 'Conclusa', badge: 'text-bg-secondary' },
  CANCELLED: { label: 'Annullata', badge: 'text-bg-danger' },
}

function StatusBadge({ status }) {
  return <span className={`badge ${STATUS[status].badge}`}>{STATUS[status].label}</span>
}

/** Carica dati da un endpoint, con stato di caricamento ed errore; `reload` ripete la richiesta. */
function useApiData(path) {
  const [state, setState] = useState({ data: null, error: null, path: null })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!path) return undefined
    const controller = new AbortController()
    api
      .get(path, { signal: controller.signal })
      .then((data) => setState({ data, error: null, path }))
      .catch((error) => error.name !== 'AbortError' && setState({ data: null, error, path }))
    return () => controller.abort()
  }, [path, attempt])

  // Finché non arriva la risposta per il percorso attuale si mostra il caricamento (es. cambio mese).
  const current = state.path === path
  return {
    data: current ? state.data : null,
    error: current ? state.error : null,
    loading: !current,
    reload: () => setAttempt((a) => a + 1),
  }
}

/** Link a Google Maps per l'indirizzo del campo (nuova scheda, senza inviare il Referer). */
function mapsUrl(match) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${match.venueName}, ${match.venueAddress}`)}`
}

export function CalendarPage() {
  const { hasRole } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const month = parseMonth(searchParams.get('month')) ?? parseMonth(currentTeamMonth())
  const key = monthKey(month)
  const { data, error, loading, reload } = useApiData(`/matches?month=${key}`)

  const goTo = (target) => setSearchParams({ month: monthKey(target) })
  const byDay = Object.groupBy(data ?? [], (m) => teamDateKey(m.kickoffAt))
  // Calcolato una sola volta al montaggio: il render resta puro.
  const [todayKey] = useState(() => teamDateKey(new Date()))

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h1 className="h4 mb-0">Calendario</h1>
        {hasRole('ADMIN') && (
          <Link className="btn btn-primary btn-sm" to="/matches/new">
            Nuova partita
          </Link>
        )}
      </div>

      <div className="d-flex justify-content-between align-items-center mb-3">
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => goTo(addMonths(month, -1))}
          aria-label="Mese precedente">
          ‹ Precedente
        </button>
        <h2 className="h5 mb-0">{formatMonthTitle(month)}</h2>
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => goTo(addMonths(month, 1))}
          aria-label="Mese successivo">
          Successivo ›
        </button>
      </div>

      <ErrorAlert error={error} onRetry={reload} />
      {loading && <LoadingSpinner label="Caricamento calendario…" />}

      {data && (
        <>
          {/* Griglia mensile su schermi medi e grandi */}
          <div className="card shadow-sm border-0 d-none d-md-block">
            <table className="table table-bordered calendar-grid mb-0">
              <thead>
                <tr>
                  {WEEKDAYS.map((d) => (
                    <th key={d} className="text-center small text-secondary">
                      {d}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {buildMonthGrid(month).map((week) => (
                  <tr key={week[0].key}>
                    {week.map((day) => (
                      <td key={day.key}
                        className={`${day.inMonth ? '' : 'calendar-out'} ${day.key === todayKey ? 'calendar-today' : ''}`}>
                        <div className="small fw-semibold">{day.day}</div>
                        {(byDay[day.key] ?? []).map((m) => (
                          <Link key={m.id} to={`/matches/${m.id}`}
                            className={`calendar-match d-block small text-truncate ${m.status === 'CANCELLED' ? 'cancelled' : ''}`}
                            title={`${formatTime(m.kickoffAt)} · ${m.opponent}`}>
                            {formatTime(m.kickoffAt)} {m.opponent}
                          </Link>
                        ))}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Elenco su smartphone */}
          <div className="d-md-none">
            {data.length === 0 && <p className="text-secondary">Nessuna partita in questo mese.</p>}
            <div className="list-group">
              {data.map((m) => (
                <MatchListItem key={m.id} match={m} />
              ))}
            </div>
          </div>
          {data.length === 0 && <p className="text-secondary d-none d-md-block mt-3">Nessuna partita in questo mese.</p>}
        </>
      )}
    </>
  )
}

function MatchListItem({ match }) {
  return (
    <Link to={`/matches/${match.id}`} className="list-group-item list-group-item-action">
      <div className="d-flex justify-content-between align-items-center gap-2">
        <div>
          <div className={`fw-semibold ${match.status === 'CANCELLED' ? 'text-decoration-line-through' : ''}`}>
            vs {match.opponent}
          </div>
          <div className="small text-secondary">
            {formatDateTime(match.kickoffAt)} · {match.venueName}
          </div>
        </div>
        <StatusBadge status={match.status} />
      </div>
    </Link>
  )
}

/** Riquadro della home con le prossime partite. */
export function UpcomingMatchesCard() {
  const { data, error, loading, reload } = useApiData('/matches/upcoming?size=3')

  return (
    <div className="card shadow-sm border-0 mb-4">
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-center mb-2">
          <h2 className="h5 mb-0">Prossime partite</h2>
          <Link to="/calendar" className="small">
            Calendario completo
          </Link>
        </div>
        <ErrorAlert error={error} onRetry={reload} />
        {loading && <LoadingSpinner />}
        {data?.content.length === 0 && <p className="text-secondary mb-0">Nessuna partita in programma.</p>}
        {data?.content.length > 0 && (
          <div className="list-group list-group-flush">
            {data.content.map((m) => (
              <MatchListItem key={m.id} match={m} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export function MatchDetailPage() {
  const { id } = useParams()
  const { hasRole } = useAuth()
  const { data: match, error, loading, reload } = useApiData(`/matches/${id}`)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState(null)

  const cancelMatch = async () => {
    setSaving(true)
    setActionError(null)
    try {
      await api.post(`/matches/${id}/cancel`, { reason, version: match.version })
      setCancelOpen(false)
      reload()
    } catch (err) {
      setActionError(err)
      setCancelOpen(false)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingSpinner label="Caricamento partita…" />
  if (error) return <ErrorAlert error={error} onRetry={reload} />

  const isAdmin = hasRole('ADMIN')
  return (
    <>
      <p className="mb-2">
        <Link to={`/calendar?month=${teamDateKey(match.kickoffAt).slice(0, 7)}`}>‹ Calendario</Link>
      </p>
      <ErrorAlert error={actionError} />
      <div className="card shadow-sm border-0">
        <div className="card-body">
          <div className="d-flex flex-wrap justify-content-between align-items-start gap-2">
            <div>
              <h1 className="h3 mb-1">vs {match.opponent}</h1>
              <p className="text-secondary mb-0 text-capitalize">
                {formatLongDate(match.kickoffAt)} · ore {formatTime(match.kickoffAt)}
              </p>
            </div>
            <StatusBadge status={match.status} />
          </div>

          {match.status === 'CANCELLED' && (
            <div className="alert alert-danger mt-3 mb-0">
              Partita annullata{match.cancellationReason ? `: ${match.cancellationReason}` : '.'}
            </div>
          )}
          {match.teamGoals !== null && (
            <p className="display-6 fw-bold mt-3 mb-0">
              {match.teamGoals} - {match.opponentGoals}
            </p>
          )}

          <dl className="row mt-3 mb-0">
            <dt className="col-sm-3">Luogo</dt>
            <dd className="col-sm-9">
              {match.venueName}
              <br />
              <span className="text-secondary">{match.venueAddress}</span>{' '}
              <a href={mapsUrl(match)} target="_blank" rel="noopener noreferrer" className="small">
                Apri in Maps
              </a>
            </dd>
            {match.notes && (
              <>
                <dt className="col-sm-3">Note</dt>
                <dd className="col-sm-9" style={{ whiteSpace: 'pre-line' }}>
                  {match.notes}
                </dd>
              </>
            )}
          </dl>

          {isAdmin && match.status !== 'CANCELLED' && (
            <div className="d-flex gap-2 mt-3">
              <Link className="btn btn-outline-primary btn-sm" to={`/matches/${match.id}/edit`}>
                Modifica
              </Link>
              {match.status === 'SCHEDULED' && (
                <button type="button" className="btn btn-outline-danger btn-sm" onClick={() => setCancelOpen(true)}>
                  Annulla partita
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={cancelOpen}
        title="Annulla partita"
        confirmLabel="Annulla partita"
        confirmVariant="danger"
        loading={saving}
        onConfirm={cancelMatch}
        onCancel={() => setCancelOpen(false)}
        message={
          <>
            <p>La partita resterà nello storico come annullata. Vuoi procedere?</p>
            <label htmlFor="cancel-reason" className="form-label">
              Motivo (facoltativo)
            </label>
            <textarea id="cancel-reason" className="form-control" rows="2" maxLength={500} value={reason}
              onChange={(e) => setReason(e.target.value)} />
          </>
        }
      />
    </>
  )
}

const EMPTY_MATCH = { opponent: '', kickoff: '', venueName: '', venueAddress: '', notes: '' }

/** Creazione (/matches/new) o modifica (/matches/:id/edit) di una partita: solo Admin. */
export function MatchFormPage() {
  const { id } = useParams()
  const { data: existing, error: loadError, loading } = useApiData(id ? `/matches/${id}` : null)
  if (id && loading) return <LoadingSpinner label="Caricamento partita…" />
  if (id && loadError) return <ErrorAlert error={loadError} />
  return <MatchForm key={existing?.id ?? 'new'} existing={existing} />
}

function MatchForm({ existing }) {
  const navigate = useNavigate()
  const form = useForm(
    existing
      ? {
          opponent: existing.opponent,
          kickoff: toLocalInputValue(existing.kickoffAt),
          venueName: existing.venueName,
          venueAddress: existing.venueAddress,
          notes: existing.notes ?? '',
        }
      : EMPTY_MATCH,
  )

  const onSubmit = (event) =>
    form.submit(
      event,
      (v) => ({
        opponent: requiredError(v.opponent, "L'avversario è obbligatorio."),
        kickoff: requiredError(v.kickoff, 'Data e ora sono obbligatorie.'),
        venueName: requiredError(v.venueName, 'Il luogo è obbligatorio.'),
        venueAddress: requiredError(v.venueAddress, "L'indirizzo è obbligatorio."),
      }),
      async (v) => {
        // L'ora inserita è quella italiana: il server la converte rispettando l'ora legale.
        const body = { ...v, version: existing?.version ?? null }
        const saved = existing ? await api.put(`/matches/${existing.id}`, body) : await api.post('/matches', body)
        navigate(`/matches/${saved.id}`)
      },
    )

  return (
    <div className="row justify-content-center">
      <div className="col-lg-8">
        <div className="card shadow-sm border-0">
          <div className="card-body p-4">
            <h1 className="h4 mb-3">{existing ? 'Modifica partita' : 'Nuova partita'}</h1>
            <ErrorAlert error={form.error} />
            {form.error?.code === 'CONCURRENT_MODIFICATION' && (
              <p className="small">
                <button type="button" className="btn btn-link btn-sm p-0" onClick={() => window.location.reload()}>
                  Ricarica i dati aggiornati
                </button>
              </p>
            )}
            <form onSubmit={onSubmit} noValidate>
              <FormField id="opponent" label="Avversario" value={form.values.opponent} onChange={form.onChange}
                error={form.fieldErrors.opponent} maxLength={120} />
              <FormField id="kickoff" label="Data e ora (ora italiana)" type="datetime-local"
                value={form.values.kickoff} onChange={form.onChange} error={form.fieldErrors.kickoff} />
              <div className="row g-2">
                <div className="col-md-5">
                  <FormField id="venueName" label="Luogo" value={form.values.venueName} onChange={form.onChange}
                    error={form.fieldErrors.venueName} maxLength={150} />
                </div>
                <div className="col-md-7">
                  <FormField id="venueAddress" label="Indirizzo" value={form.values.venueAddress}
                    onChange={form.onChange} error={form.fieldErrors.venueAddress} maxLength={255} />
                </div>
              </div>
              <FormField id="notes" label="Note (facoltative)" multiline rows={3} value={form.values.notes}
                onChange={form.onChange} error={form.fieldErrors.notes} maxLength={2000} />
              <div className="d-flex gap-2">
                <SubmitButton loading={form.loading} loadingLabel="Salvataggio…" className="btn btn-primary">
                  {existing ? 'Salva modifiche' : 'Crea partita'}
                </SubmitButton>
                <Link className="btn btn-outline-secondary" to={existing ? `/matches/${existing.id}` : '/calendar'}>
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
