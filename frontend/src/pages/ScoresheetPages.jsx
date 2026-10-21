import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatDateTime } from '../utils/dates'
import { useApiData } from '../utils/useApiData'

const EVENT_TYPES = {
  GOAL: { label: 'Gol', icon: '⚽' },
  OWN_GOAL: { label: 'Autogol', icon: '⚽' },
  YELLOW_CARD: { label: 'Ammonizione', icon: '🟨' },
  RED_CARD: { label: 'Espulsione', icon: '🟥' },
}

const SIDES = { TEAM: 'Noi', OPPONENT: 'Avversari' }

/** Descrizione leggibile di un evento, es. "45' ⚽ Autogol · Paolo Bianchi (noi)". */
function eventLabel(event) {
  const type = EVENT_TYPES[event.eventType]
  const who = event.playerDisplayName ?? (event.teamSide === 'OPPONENT' ? 'Giocatore avversario' : 'Giocatore')
  return `${type.label} · ${who}`
}

const OUTCOMES = {
  W: { label: 'V', title: 'Vittoria', badge: 'text-bg-success' },
  D: { label: 'N', title: 'Pareggio', badge: 'text-bg-warning' },
  L: { label: 'P', title: 'Sconfitta', badge: 'text-bg-danger' },
}

/** Badge V/N/P di una partita conclusa. */
export function ResultBadge({ match, className = '' }) {
  const o =
    match.teamGoals > match.opponentGoals ? OUTCOMES.W : match.teamGoals === match.opponentGoals ? OUTCOMES.D : OUTCOMES.L
  return (
    <span className={`badge ${o.badge} ${className}`} title={o.title}>
      {o.label}
    </span>
  )
}

/** Storico: bilancio della squadra, statistiche dei giocatori e risultati delle partite concluse. */
export function ResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Number(searchParams.get('page') ?? 0)
  const stats = useApiData('/matches/stats')
  const results = useApiData(`/matches/results?page=${page}&size=10`)
  const goTo = (target) => setSearchParams(target > 0 ? { page: target } : {})

  return (
    <>
      <h1 className="h4 mb-3">Risultati</h1>
      <ErrorAlert error={stats.error} onRetry={stats.reload} />
      {stats.loading && <LoadingSpinner label="Caricamento statistiche…" />}
      {stats.data && (
        <div className="row g-4 mb-4">
          <div className="col-lg-5">
            <TeamRecordCard stats={stats.data} />
          </div>
          <div className="col-lg-7">
            <PlayerStatsCard players={stats.data.players} />
          </div>
        </div>
      )}

      <h2 className="h5">Partite concluse</h2>
      <ErrorAlert error={results.error} onRetry={results.reload} />
      {results.loading && <LoadingSpinner label="Caricamento risultati…" />}
      {results.data && (
        <div className="card shadow-sm border-0">
          {results.data.content.length === 0 ? (
            <p className="text-secondary text-center py-4 mb-0">Nessuna partita conclusa.</p>
          ) : (
            <ul className="list-group list-group-flush" aria-label="Partite concluse">
              {results.data.content.map((m) => (
                <li key={m.id} className="list-group-item d-flex flex-wrap justify-content-between align-items-center column-gap-2">
                  <span>
                    <ResultBadge match={m} className="me-2" />
                    <Link to={`/matches/${m.id}`}>Noi {m.teamGoals} - {m.opponentGoals} {m.opponent}</Link>
                  </span>
                  <span className="small text-secondary">{formatDateTime(m.kickoffAt)}</span>
                </li>
              ))}
            </ul>
          )}
          {results.data.totalPages > 1 && (
            <div className="card-footer bg-white d-flex justify-content-between align-items-center">
              <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page === 0}
                onClick={() => goTo(page - 1)}>
                Precedente
              </button>
              <span className="small text-secondary">
                Pagina {results.data.page + 1} di {results.data.totalPages}
              </span>
              <button type="button" className="btn btn-sm btn-outline-secondary"
                disabled={page + 1 >= results.data.totalPages} onClick={() => goTo(page + 1)}>
                Successiva
              </button>
            </div>
          )}
        </div>
      )}
    </>
  )
}

function TeamRecordCard({ stats }) {
  const items = [
    ['Giocate', stats.played],
    ['Vittorie', stats.wins],
    ['Pareggi', stats.draws],
    ['Sconfitte', stats.losses],
    ['Gol fatti', stats.goalsFor],
    ['Gol subiti', stats.goalsAgainst],
  ]
  return (
    <div className="card shadow-sm border-0 h-100">
      <div className="card-body">
        <h2 className="h5">Bilancio della squadra</h2>
        <dl className="row row-cols-3 g-2 mb-0 text-center">
          {items.map(([label, value]) => (
            <div key={label} className="col">
              <dt className="small text-secondary fw-normal">{label}</dt>
              <dd className="h4 mb-0">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </div>
  )
}

/** Gol e cartellini dei nostri giocatori; la riga dell'utente collegato è evidenziata. */
function PlayerStatsCard({ players }) {
  const { user } = useAuth()
  return (
    <div className="card shadow-sm border-0 h-100">
      <div className="card-body">
        <h2 className="h5">Statistiche dei giocatori</h2>
        {players.length === 0 ? (
          <p className="text-secondary small mb-0">Nessun evento registrato nei tabellini.</p>
        ) : (
          <div className="table-responsive">
            <table className="table table-sm align-middle mb-0 small">
              <thead>
                <tr>
                  <th>Giocatore</th>
                  <th className="text-end">Gol</th>
                  <th className="text-end">Autogol</th>
                  <th className="text-end" title="Ammonizioni">🟨</th>
                  <th className="text-end" title="Espulsioni">🟥</th>
                </tr>
              </thead>
              <tbody>
                {players.map((p) => (
                  <tr key={p.playerId ?? `name:${p.name}`}
                    className={p.playerId !== null && p.playerId === user?.id ? 'table-success' : ''}>
                    <td>
                      {p.name}
                      {p.playerId === null && <span className="text-secondary"> (senza account)</span>}
                    </td>
                    <td className="text-end fw-semibold">{p.goals}</td>
                    <td className="text-end">{p.ownGoals}</td>
                    <td className="text-end">{p.yellowCards}</td>
                    <td className="text-end">{p.redCards}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * Risultato e tabellino nella pagina della partita. Tutti lo vedono; Allenatori e Admin vedono anche gli avvisi
 * di incoerenza; l'Admin registra il risultato e gestisce gli eventi. {@code onChanged} ricarica la partita
 * (stato e versione cambiano quando si registra il risultato).
 */
export function ScoresheetCard({ match, onChanged }) {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const { data, error, loading, reload, setData } = useApiData(`/matches/${match.id}/scoresheet`)
  const [editing, setEditing] = useState(null) // evento in modifica, oppure 'new'
  const [toDelete, setToDelete] = useState(null)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState(null)

  const apply = (scoresheet) => {
    setData(scoresheet)
    onChanged?.()
  }

  const deleteEvent = async () => {
    setSaving(true)
    setActionError(null)
    try {
      apply(await api.delete(`/matches/${match.id}/events/${toDelete.id}`))
    } catch (err) {
      setActionError(err)
    } finally {
      setSaving(false)
      setToDelete(null)
    }
  }

  if (loading) return <LoadingSpinner label="Caricamento tabellino…" />
  if (error) return <ErrorAlert error={error} onRetry={reload} />

  const hasResult = data.teamGoals !== null
  return (
    <div className="card shadow-sm border-0 mt-3">
      <div className="card-body">
        <h2 className="h5">Risultato e tabellino</h2>
        {hasResult ? (
          <p className="display-6 fw-bold mb-2" aria-label="Risultato">
            Noi {data.teamGoals} - {data.opponentGoals} {match.opponent}
          </p>
        ) : (
          <p className="text-secondary">Risultato non ancora registrato.</p>
        )}

        {hasRole('COACH') && !data.consistent && (
          <div className="alert alert-warning py-2 small" role="alert">
            <strong>Incoerenze nel tabellino:</strong>
            <ul className="mb-0">
              {data.warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          </div>
        )}
        <ErrorAlert error={actionError} />

        {isAdmin && <ResultForm match={match} scoresheet={data} onSaved={apply} />}

        <h3 className="h6 mt-3">Eventi</h3>
        {data.events.length === 0 && <p className="text-secondary small">Nessun evento registrato.</p>}
        <ul className="list-group list-group-flush mb-2">
          {data.events.map((e) =>
            editing?.id === e.id ? (
              <li key={e.id} className="list-group-item px-0">
                <EventForm matchId={match.id} event={e} onSaved={(s) => { apply(s); setEditing(null) }}
                  onCancel={() => setEditing(null)} />
              </li>
            ) : (
              <li key={e.id} className="list-group-item px-0 d-flex flex-wrap justify-content-between align-items-center gap-2">
                <span>
                  <span className="text-secondary me-2" style={{ minWidth: '2.5rem', display: 'inline-block' }}>
                    {e.minute !== null ? `${e.minute}'` : '—'}
                  </span>
                  <span aria-hidden="true">{EVENT_TYPES[e.eventType].icon} </span>
                  {eventLabel(e)}
                  <span className={`badge ms-2 ${e.teamSide === 'TEAM' ? 'text-bg-success' : 'text-bg-secondary'}`}>
                    {SIDES[e.teamSide]}
                  </span>
                  {e.notes && <span className="small text-secondary"> · {e.notes}</span>}
                </span>
                {isAdmin && (
                  <span className="d-flex gap-1 ms-auto">
                    <button type="button" className="btn btn-sm btn-link" onClick={() => setEditing(e)}>
                      Modifica
                    </button>
                    <button type="button" className="btn btn-sm btn-link text-danger" onClick={() => setToDelete(e)}>
                      Elimina
                    </button>
                  </span>
                )}
              </li>
            ),
          )}
        </ul>

        {isAdmin && editing === 'new' && (
          <EventForm matchId={match.id} onSaved={(s) => { apply(s); setEditing(null) }} onCancel={() => setEditing(null)} />
        )}
        {isAdmin && editing === null && (
          <button type="button" className="btn btn-sm btn-outline-primary" onClick={() => setEditing('new')}>
            Aggiungi evento
          </button>
        )}
      </div>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Elimina evento"
        confirmLabel="Elimina"
        confirmVariant="danger"
        loading={saving}
        onConfirm={deleteEvent}
        onCancel={() => setToDelete(null)}
        message={toDelete && `Eliminare l'evento "${eventLabel(toDelete)}"? L'operazione resta registrata nell'audit log.`}
      />
    </div>
  )
}

/** Risultato ufficiale (Admin). La versione evita di sovrascrivere modifiche concorrenti. */
function ResultForm({ match, scoresheet, onSaved }) {
  const [teamGoals, setTeamGoals] = useState(scoresheet.teamGoals ?? '')
  const [opponentGoals, setOpponentGoals] = useState(scoresheet.opponentGoals ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const valid = teamGoals !== '' && opponentGoals !== '' && Number(teamGoals) >= 0 && Number(opponentGoals) >= 0

  const save = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      onSaved(await api.put(`/matches/${match.id}/result`, {
        teamGoals: Number(teamGoals),
        opponentGoals: Number(opponentGoals),
        version: scoresheet.matchVersion,
      }))
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="row g-2 align-items-end mb-2" onSubmit={save} noValidate>
      <div className="col-auto">
        <label htmlFor="teamGoals" className="form-label small mb-1">
          Gol nostri
        </label>
        <input id="teamGoals" type="number" min="0" max="99" className="form-control form-control-sm"
          style={{ width: '5rem' }} value={teamGoals} onChange={(e) => setTeamGoals(e.target.value)} />
      </div>
      <div className="col-auto">
        <label htmlFor="opponentGoals" className="form-label small mb-1">
          Gol avversari
        </label>
        <input id="opponentGoals" type="number" min="0" max="99" className="form-control form-control-sm"
          style={{ width: '5rem' }} value={opponentGoals} onChange={(e) => setOpponentGoals(e.target.value)} />
      </div>
      <div className="col-auto">
        <button type="submit" className="btn btn-sm btn-primary" disabled={!valid || saving}>
          {scoresheet.teamGoals === null ? 'Registra risultato' : 'Aggiorna risultato'}
        </button>
      </div>
      {error && <div className="col-12 small text-danger">{error.message}</div>}
    </form>
  )
}

const EMPTY_EVENT = { eventType: 'GOAL', teamSide: 'TEAM', playerId: '', playerName: '', minute: '', notes: '' }

/**
 * Inserimento o modifica di un evento (Admin). Per i nostri giocatori si sceglie un utente attivo
 * oppure "altro" con nome libero (giocatore senza account); per gli avversari solo un nome facoltativo.
 */
function EventForm({ matchId, event, onSaved, onCancel }) {
  const { data: players } = useApiData('/users?status=ACTIVE&size=100')
  const [values, setValues] = useState(() =>
    event
      ? {
          eventType: event.eventType,
          teamSide: event.teamSide,
          playerId: event.playerId ?? (event.teamSide === 'TEAM' && event.playerDisplayName ? 'other' : ''),
          playerName: event.playerId ? '' : (event.playerDisplayName ?? ''),
          minute: event.minute ?? '',
          notes: event.notes ?? '',
        }
      : EMPTY_EVENT,
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }))
  const isTeam = values.teamSide === 'TEAM'
  const usesFreeName = !isTeam || values.playerId === 'other'

  const save = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    const body = {
      eventType: values.eventType,
      teamSide: values.teamSide,
      playerId: isTeam && values.playerId && values.playerId !== 'other' ? Number(values.playerId) : null,
      playerName: usesFreeName ? values.playerName : null,
      minute: values.minute === '' ? null : Number(values.minute),
      notes: values.notes,
      version: event?.version ?? null,
    }
    try {
      onSaved(event
        ? await api.put(`/matches/${matchId}/events/${event.id}`, body)
        : await api.post(`/matches/${matchId}/events`, body))
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="border rounded p-2 mb-2" onSubmit={save} noValidate aria-label="Evento del tabellino">
      <div className="row g-2">
        <div className="col-sm-3">
          <label htmlFor="eventType" className="form-label small mb-1">
            Tipo
          </label>
          <select id="eventType" className="form-select form-select-sm" value={values.eventType} onChange={set('eventType')}>
            {Object.entries(EVENT_TYPES).map(([value, t]) => (
              <option key={value} value={value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
        <div className="col-sm-3">
          <label htmlFor="teamSide" className="form-label small mb-1">
            Squadra del giocatore
          </label>
          <select id="teamSide" className="form-select form-select-sm" value={values.teamSide} onChange={set('teamSide')}>
            {Object.entries(SIDES).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        {isTeam && (
          <div className="col-sm-4">
            <label htmlFor="playerId" className="form-label small mb-1">
              Giocatore
            </label>
            <select id="playerId" className="form-select form-select-sm" value={values.playerId} onChange={set('playerId')}>
              <option value="">Seleziona…</option>
              {players?.content.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.lastName} {p.firstName}
                </option>
              ))}
              <option value="other">Altro (senza account)</option>
            </select>
          </div>
        )}
        {usesFreeName && (
          <div className="col-sm-4">
            <label htmlFor="playerName" className="form-label small mb-1">
              {isTeam ? 'Nome del giocatore' : 'Nome avversario (facoltativo)'}
            </label>
            <input id="playerName" className="form-control form-control-sm" maxLength={120} value={values.playerName}
              onChange={set('playerName')} />
          </div>
        )}
        <div className="col-sm-2">
          <label htmlFor="minute" className="form-label small mb-1">
            Minuto
          </label>
          <input id="minute" type="number" min="0" max="150" className="form-control form-control-sm"
            value={values.minute} onChange={set('minute')} />
        </div>
        <div className="col-12">
          <label htmlFor="eventNotes" className="form-label small mb-1">
            Note (facoltative)
          </label>
          <input id="eventNotes" className="form-control form-control-sm" maxLength={500} value={values.notes}
            onChange={set('notes')} />
        </div>
      </div>
      {values.eventType === 'OWN_GOAL' && (
        <p className="small text-secondary mt-1 mb-0">
          Autogol: indica la squadra di chi lo ha segnato. Un autogol dei nostri conta come gol per gli avversari.
        </p>
      )}
      {error && <div className="small text-danger mt-1">{error.message}</div>}
      <div className="d-flex gap-2 mt-2">
        <button type="submit" className="btn btn-sm btn-primary" disabled={saving}>
          {event ? 'Salva evento' : 'Aggiungi'}
        </button>
        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={onCancel}>
          Annulla
        </button>
      </div>
    </form>
  )
}
