import { Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ConfirmDialog from '../components/common/ConfirmDialog'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatDateTime } from '../utils/dates'
import { useApiData } from '../utils/useApiData'

/** Tipi di evento; {@code mark} è il segno disegnato in CSS accanto all'evento (pallone, pallone vuoto, cartellino). */
const EVENT_TYPES = {
  GOAL: { label: 'Gol', mark: 'goal' },
  OWN_GOAL: { label: 'Autogol', mark: 'own-goal' },
  YELLOW_CARD: { label: 'Ammonizione', mark: 'yellow' },
  RED_CARD: { label: 'Espulsione', mark: 'red' },
}

const SIDES = { TEAM: 'Noi', OPPONENT: 'Avversari' }

/** Il calcetto della squadra si gioca in due tempi da 25 minuti: oltre il 25' l'evento è nel secondo tempo. */
const HALF_LENGTH = 25

/** Descrizione leggibile di un evento, es. "Autogol · Paolo Bianchi". */
function eventLabel(event) {
  const type = EVENT_TYPES[event.eventType]
  const who = event.playerDisplayName ?? (event.teamSide === 'OPPONENT' ? 'Giocatore avversario' : 'Giocatore')
  return `${type.label} · ${who}`
}

const OUTCOMES = {
  W: { label: 'V', title: 'Vittoria', badge: 'text-bg-success', tone: 'win' },
  D: { label: 'N', title: 'Pareggio', badge: 'text-bg-warning', tone: 'draw' },
  L: { label: 'P', title: 'Sconfitta', badge: 'text-bg-danger', tone: 'loss' },
}

function matchOutcome(match) {
  if (match.teamGoals > match.opponentGoals) return OUTCOMES.W
  return match.teamGoals === match.opponentGoals ? OUTCOMES.D : OUTCOMES.L
}

/** Esito per esteso ("Vittoria"), colorato come sul tabellone. */
export function OutcomeLabel({ match }) {
  const o = matchOutcome(match)
  return <span className={`scoreboard-outcome is-${o.tone}`}>{o.title}</span>
}

/**
 * Punteggio come sul tabellone: nomi sopra, gol a grande scala, una riga a fare da trattino. Solo la parte visiva:
 * chi lo usa fornisce il contenitore (link o riquadro) e il testo per i lettori di schermo.
 */
export function ScoreboardScore({ teamGoals, opponentGoals, opponent }) {
  return (
    <>
      <span className="scoreboard-side">Noi</span>
      <span className="scoreboard-side is-opponent">{opponent}</span>
      <span className="scoreboard-goals">{teamGoals}</span>
      <span className="scoreboard-dash" aria-hidden="true" />
      <span className="scoreboard-goals is-opponent">{opponentGoals}</span>
    </>
  )
}

/** Badge V/N/P di una partita conclusa. */
export function ResultBadge({ match, className = '' }) {
  const o = matchOutcome(match)
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
        <div className="row g-3 mb-4">
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
                <li key={m.id} className="list-group-item result-row">
                  <ResultBadge match={m} className="result-outcome" />
                  <Link to={`/matches/${m.id}`} className="result-link"
                    aria-label={`Noi ${m.teamGoals} - ${m.opponentGoals} ${m.opponent}`}>
                    <span className="result-score">
                      {m.teamGoals}–{m.opponentGoals}
                    </span>
                    <span className="result-opponent">{m.opponent}</span>
                  </Link>
                  <span className="result-date">{formatDateTime(m.kickoffAt)}</span>
                </li>
              ))}
            </ul>
          )}
          {results.data.totalPages > 1 && (
            <div className="card-footer d-flex justify-content-between align-items-center">
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

/**
 * Bilancio come la riga di una classifica di campionato: sigle in alto (G V N P GF GS), numeri grandi sotto.
 * Sotto, una tacca per ogni partita giocata, raggruppate per esito nei colori del tabellone (niente stili inline:
 * la larghezza viene dal numero di tacche).
 */
function TeamRecordCard({ stats }) {
  const items = [
    ['G', 'Giocate', stats.played],
    ['V', 'Vittorie', stats.wins],
    ['N', 'Pareggi', stats.draws],
    ['P', 'Sconfitte', stats.losses],
    ['GF', 'Gol fatti', stats.goalsFor],
    ['GS', 'Gol subiti', stats.goalsAgainst],
  ]
  const ticks = [
    ...Array(stats.wins).fill('win'),
    ...Array(stats.draws).fill('draw'),
    ...Array(stats.losses).fill('loss'),
  ]
  return (
    <section className="pitch-panel record-panel h-100" aria-labelledby="record-title">
      <h2 id="record-title" className="h5">Bilancio della squadra</h2>
      <dl className="record-row">
        {items.map(([short, label, value]) => (
          <div key={short} className="record-cell">
            <dt>
              <span aria-hidden="true">{short}</span>
              <span className="visually-hidden">{label}</span>
            </dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {ticks.length > 0 && (
        <div className="record-bar" aria-hidden="true">
          {ticks.map((tone, i) => (
            <span key={i} className={`is-${tone}`} />
          ))}
        </div>
      )}
    </section>
  )
}

/**
 * Classifica marcatori: il server la ordina per gol. Posizione a pari merito (1, 1, 3) quando i gol sono uguali;
 * il primo con almeno un gol è il capocannoniere, la riga dell'utente collegato è evidenziata.
 */
function PlayerStatsCard({ players }) {
  const { user } = useAuth()
  const ranks = players.map((p) => players.findIndex((q) => q.goals === p.goals) + 1)
  return (
    <section className="scorers-panel h-100" aria-labelledby="player-stats-title">
      <h2 id="player-stats-title" className="h5">Classifica marcatori</h2>
      {players.length === 0 ? (
        <p className="text-secondary small mb-0">Nessun evento registrato nei tabellini.</p>
      ) : (
        <div className="table-responsive">
          <table className="table align-middle mb-0 scorers-table">
            <thead>
              <tr>
                <th scope="col" className="text-end">
                  <span aria-hidden="true">#</span>
                  <span className="visually-hidden">Posizione</span>
                </th>
                <th scope="col">Giocatore</th>
                <th scope="col" className="text-end">Gol</th>
                <th scope="col" className="text-end">Autogol</th>
                <th scope="col" className="text-end">
                  <span className="event-mark is-yellow" aria-hidden="true" />
                  <span className="visually-hidden">Ammonizioni</span>
                </th>
                <th scope="col" className="text-end">
                  <span className="event-mark is-red" aria-hidden="true" />
                  <span className="visually-hidden">Espulsioni</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {players.map((p, i) => {
                const isMe = p.playerId !== null && p.playerId === user?.id
                const isTop = ranks[i] === 1 && p.goals > 0
                return (
                  <tr key={p.playerId ?? `name:${p.name}`}
                    className={`${isMe ? 'is-me' : ''} ${isTop ? 'is-top' : ''}`.trim() || undefined}>
                    <td className="text-end scorers-rank">{ranks[i]}</td>
                    <td>
                      {p.name}
                      {p.playerId === null && <span className="text-secondary small"> (senza account)</span>}
                      {isMe && <span className="visually-hidden"> (tu)</span>}
                    </td>
                    <td className="text-end scorers-goals">{p.goals}</td>
                    <td className="text-end">{p.ownGoals}</td>
                    <td className="text-end">{p.yellowCards}</td>
                    <td className="text-end">{p.redCards}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
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
  const onSaved = (s) => {
    apply(s)
    setEditing(null)
  }
  return (
    <section className="scoresheet mt-3" aria-labelledby="scoresheet-title">
      <div>
        <h2 id="scoresheet-title" className="h5">Risultato e tabellino</h2>
        {hasResult ? (
          <div aria-label="Risultato">
            <span className="visually-hidden">
              Noi {data.teamGoals} - {data.opponentGoals} {match.opponent}
            </span>
            <div className="scoreboard-score" aria-hidden="true">
              <ScoreboardScore teamGoals={data.teamGoals} opponentGoals={data.opponentGoals} opponent={match.opponent} />
            </div>
          </div>
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

        <h3 className="h6 mt-4">Eventi</h3>
        {data.events.length === 0 ? (
          <p className="text-secondary small">Nessun evento registrato.</p>
        ) : (
          <MatchPitch events={data.events} opponent={match.opponent} renderEvent={(e) =>
            editing?.id === e.id ? (
              <EventForm matchId={match.id} event={e} onSaved={onSaved} onCancel={() => setEditing(null)} />
            ) : null}
            renderActions={isAdmin ? (e) => (
              <>
                <button type="button" className="btn btn-sm pitch-event-action" onClick={() => setEditing(e)}
                  aria-label={`Modifica: ${eventLabel(e)}`} title="Modifica">
                  <Pencil size={16} aria-hidden="true" />
                </button>
                <button type="button" className="btn btn-sm pitch-event-action" onClick={() => setToDelete(e)}
                  aria-label={`Elimina: ${eventLabel(e)}`} title="Elimina">
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </>
            ) : null} />
        )}

        {isAdmin && editing === 'new' && (
          <EventForm matchId={match.id} onSaved={onSaved} onCancel={() => setEditing(null)} />
        )}
        {isAdmin && editing === null && (
          <button type="button" className="btn btn-sm btn-outline-primary mt-3" onClick={() => setEditing('new')}>
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
    </section>
  )
}

/**
 * Il tabellino disegnato come il campo. Il tempo scorre dall'alto in basso; la linea di metà campo, con il cerchio
 * di centrocampo, è l'intervallo; la metà sinistra è nostra, la destra degli avversari. Gli eventi senza minuto
 * chiudono l'elenco. {@code renderEvent} sostituisce un evento (modulo di modifica), {@code renderActions} aggiunge
 * i comandi dell'Admin.
 */
function MatchPitch({ events, opponent, renderEvent, renderActions }) {
  const timed = events.filter((e) => e.minute !== null).toSorted((a, b) => a.minute - b.minute)
  const firstHalf = timed.filter((e) => e.minute <= HALF_LENGTH)
  const secondHalf = timed.filter((e) => e.minute > HALF_LENGTH)
  const untimed = events.filter((e) => e.minute === null)

  const row = (e) => {
    const replacement = renderEvent(e)
    if (replacement) {
      return (
        <li key={e.id} className="pitch-event-edit">
          {replacement}
        </li>
      )
    }
    const side = e.teamSide === 'TEAM' ? 'team' : 'opponent'
    return (
      <li key={e.id} className={`pitch-event is-${side}`}>
        <span className="pitch-event-minute">{e.minute !== null ? `${e.minute}'` : '—'}</span>
        <span className="pitch-event-body">
          <span className={`event-mark is-${EVENT_TYPES[e.eventType].mark}`} aria-hidden="true" />
          <span className="pitch-event-text">
            {eventLabel(e)}
            <span className="visually-hidden"> ({SIDES[e.teamSide]})</span>
            {e.notes && <span className="pitch-event-notes">{e.notes}</span>}
            {renderActions && <span className="pitch-event-actions">{renderActions(e)}</span>}
          </span>
        </span>
      </li>
    )
  }

  return (
    <div className="match-pitch">
      <div className="match-pitch-sides" aria-hidden="true">
        <span>Noi</span>
        <span>{opponent}</span>
      </div>
      <ol className="match-pitch-events" aria-label="Eventi della partita">
        {firstHalf.map(row)}
        {timed.length > 0 && (
          <li className="match-pitch-halfway">
            <span>Intervallo</span>
          </li>
        )}
        {secondHalf.map(row)}
        {untimed.length > 0 && (
          <li className="match-pitch-untimed">
            <span>Senza minuto</span>
          </li>
        )}
        {untimed.map(row)}
      </ol>
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
