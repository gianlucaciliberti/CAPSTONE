import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { capitalize, formatDateTime, formatDaysFromToday, formatLongDate, formatTime } from '../utils/dates'
import { formatEuro } from '../utils/money'
import { useApiData } from '../utils/useApiData'
import { AvailabilityBadge, QuickAvailability } from './AvailabilityPages'
import { MyCallupsCard } from './CallupPages'
import { UpcomingMatchesCard } from './MatchPages'
import { OutcomeLabel, ScoreboardScore } from './ScoresheetPages'

const romeFormatter = new Intl.DateTimeFormat('it-IT', {
  dateStyle: 'full',
  timeStyle: 'short',
  timeZone: 'Europe/Rome',
})

export default function HomePage() {
  const { status, user } = useAuth()

  if (status === 'authenticated') {
    return (
      <>
        <h1 className="home-greeting">Ciao {user.firstName}!</h1>
        <Dashboard />
      </>
    )
  }

  return (
    <>
      <section className="hero-pitch rounded-4 p-4 p-md-5 mb-4 text-white">
        <h1 className="display-6 fw-bold mb-2">Benvenuto nel gestionale della squadra</h1>
        <p className="lead mb-0">Calendario, disponibilità, convocazioni, risultati e pagamenti in un unico posto.</p>
        {status === 'anonymous' && (
          <div className="d-flex gap-2 mt-3">
            <Link className="btn btn-light" to="/login">
              Accedi
            </Link>
            <Link className="btn btn-outline-light" to="/register">
              Registrati
            </Link>
          </div>
        )}
      </section>
      <ServiceStatusCard />
    </>
  )
}

/**
 * Home dell'utente autenticato: una sola chiamata a /dashboard più gli elenchi di partite e convocazioni.
 * Su smartphone l'ordine è quello delle domande del giocatore: quando si gioca, com'è andata, cosa devo pagare.
 */
function Dashboard() {
  const { data, error, loading, reload } = useApiData('/dashboard')

  return (
    <>
      <ErrorAlert error={error} onRetry={reload} />
      {loading && <LoadingSpinner label="Caricamento della dashboard…" />}
      {data && (
        <div className="row g-3 mb-3">
          <div className="col-lg-7">
            <NextMatchCard next={data.nextMatch} />
          </div>
          <div className="col-lg-5 d-flex flex-column gap-3">
            <LastResultCard match={data.lastResult} />
            <PaymentsCard payments={data.payments} />
          </div>
          {data.staff && (
            <div className="col-12">
              <StaffTasksCard tasks={data.staff} />
            </div>
          )}
        </div>
      )}
      <div className="row g-3">
        <div className="col-lg-6">
          <UpcomingMatchesCard />
        </div>
        <div className="col-lg-6">
          <MyCallupsCard />
        </div>
      </div>
    </>
  )
}

function NextMatchCard({ next }) {
  // Letto una volta al montaggio: il render resta puro.
  const [now] = useState(() => new Date())
  if (!next) {
    return (
      <section className="pitch-panel h-100" aria-labelledby="next-match-title">
        <h2 id="next-match-title" className="pitch-opponent">Prossima partita</h2>
        <p className="pitch-meta mb-0">Nessuna partita in calendario: l'Admin la aggiungerà a breve.</p>
      </section>
    )
  }
  const { match } = next
  return (
    <section className="pitch-panel h-100" aria-labelledby="next-match-title">
      <h2 id="next-match-title" className="pitch-opponent">
        <span className="visually-hidden">Prossima partita: </span>
        <span>vs {match.opponent}</span>
      </h2>
      <p className="pitch-when">
        {capitalize(formatDaysFromToday(match.kickoffAt, now))} · ore {formatTime(match.kickoffAt)}
      </p>
      <p className="pitch-meta">
        {capitalize(formatLongDate(match.kickoffAt))}
        <br />
        {match.venueName}
      </p>

      <p className={`pitch-callup${next.convocated ? ' is-convocated' : ''}`}>
        {next.callupPublished
          ? next.convocated
            ? 'Sei convocato per questa partita.'
            : 'Non sei tra i convocati per questa partita.'
          : 'Convocazione non ancora pubblicata.'}
      </p>

      {next.availabilityOpen ? (
        <QuickAvailability matchId={match.id} />
      ) : (
        <p className="d-flex flex-wrap align-items-center gap-2 mb-0">
          <span className="small">La tua disponibilità:</span>
          <AvailabilityBadge status={next.myAvailability} />
        </p>
      )}
      <Link className="pitch-link" to={`/matches/${match.id}`}>
        Dettagli partita
      </Link>
    </section>
  )
}

function PaymentsCard({ payments }) {
  const due = payments.outstandingCents > 0
  return (
    <section className="home-strip" aria-labelledby="payments-title">
      <div>
        <h2 id="payments-title" className="h5 mb-1">I tuoi pagamenti</h2>
        {due ? (
          <p className="mb-0">
            Da pagare: <strong>{formatEuro(payments.outstandingCents)}</strong>
            {payments.overdueCount > 0 && <span className="badge text-bg-danger ms-2">{payments.overdueCount} scadute</span>}
          </p>
        ) : (
          <p className="text-secondary mb-0">Nessun pagamento in sospeso.</p>
        )}
      </div>
      <Link to="/payments" className={`btn btn-sm ${due ? 'btn-primary' : 'btn-outline-primary'}`}>
        Dettagli
      </Link>
    </section>
  )
}

/** Il risultato come sul tabellone del campo: i due punteggi a grande scala e, sotto, i nostri marcatori. */
function LastResultCard({ match }) {
  return (
    <section className="scoreboard" aria-labelledby="last-result-title">
      <div className="d-flex justify-content-between align-items-baseline gap-2">
        <h2 id="last-result-title" className="h5 mb-0">Ultimo risultato</h2>
        <Link to="/results" className="small">
          Tutti i risultati
        </Link>
      </div>
      {match ? (
        <>
          <Link to={`/matches/${match.id}`} className="scoreboard-score"
            aria-label={`Noi ${match.teamGoals} - ${match.opponentGoals} ${match.opponent}`}>
            <ScoreboardScore teamGoals={match.teamGoals} opponentGoals={match.opponentGoals} opponent={match.opponent} />
          </Link>
          <p className="small mb-0">
            <OutcomeLabel match={match} />
            <span className="text-secondary"> · {formatDateTime(match.kickoffAt)}</span>
          </p>
          <Scorers matchId={match.id} />
        </>
      ) : (
        <p className="text-secondary mt-2 mb-0">Nessuna partita conclusa.</p>
      )}
    </section>
  )
}

/**
 * Nostri marcatori dell'ultima partita, dal tabellino: nome e numero di gol, più gli autogol avversari.
 * Informazione di contorno: se il tabellino non arriva il riquadro resta senza elenco.
 */
function Scorers({ matchId }) {
  const { data } = useApiData(`/matches/${matchId}/scoresheet`)
  if (!data) return null

  const goals = new Map()
  let ownGoals = 0
  for (const e of data.events) {
    if (e.eventType === 'GOAL' && e.teamSide === 'TEAM') {
      const name = e.playerDisplayName ?? 'Giocatore'
      goals.set(name, (goals.get(name) ?? 0) + 1)
    } else if (e.eventType === 'OWN_GOAL' && e.teamSide === 'OPPONENT') {
      ownGoals += 1
    }
  }
  if (goals.size === 0 && ownGoals === 0) return null

  return (
    <ul className="scorers" aria-label="Marcatori">
      <li className="scorers-label" aria-hidden="true">Gol</li>
      {[...goals].map(([name, count]) => (
        <li key={name}>
          {name}
          {count > 1 && <span className="scorers-count"> ×{count}</span>}
        </li>
      ))}
      {ownGoals > 0 && (
        <li>
          Autogol avversario
          {ownGoals > 1 && <span className="scorers-count"> ×{ownGoals}</span>}
        </li>
      )}
    </ul>
  )
}

/** Cose da fare per Allenatori e Admin; i campi solo Admin arrivano null per gli Allenatori. */
function StaffTasksCard({ tasks }) {
  const items = [
    ...tasks.matchesWithoutCallup.map((m) => ({
      key: `callup-${m.id}`,
      to: `/matches/${m.id}/callup`,
      text: `Convocazione da pubblicare: vs ${m.opponent}`,
      detail: formatDateTime(m.kickoffAt),
    })),
    ...(tasks.resultsToRecord ?? []).map((m) => ({
      key: `result-${m.id}`,
      to: `/matches/${m.id}`,
      text: `Risultato da registrare: vs ${m.opponent}`,
      detail: formatDateTime(m.kickoffAt),
    })),
  ]
  if (tasks.pendingApprovals > 0) {
    items.push({ key: 'users', to: '/admin/users', text: `Registrazioni da approvare: ${tasks.pendingApprovals}` })
  }
  if (tasks.failedEmails > 0) {
    items.push({ key: 'emails', to: '/emails?status=FAILED', text: `Email non consegnate: ${tasks.failedEmails}` })
  }
  if (tasks.outstandingPaymentsCents > 0) {
    items.push({
      key: 'payments',
      to: '/admin/payments?status=OPEN',
      text: `Da incassare: ${formatEuro(tasks.outstandingPaymentsCents)}`,
    })
  }

  return (
    <div className="card shadow-sm border-0">
      <div className="card-body">
        <h2 className="h5">Da fare</h2>
        {items.length === 0 ? (
          <p className="text-secondary mb-0">Nessuna attività in sospeso.</p>
        ) : (
          <div className="list-group list-group-flush" aria-label="Attività in sospeso">
            {items.map((item) => (
              <Link key={item.key} to={item.to}
                className="list-group-item list-group-item-action d-flex flex-wrap justify-content-between gap-2">
                <span>{item.text}</span>
                {item.detail && <span className="small text-secondary">{item.detail}</span>}
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/** Stato del collegamento al server, per i visitatori non autenticati. */
function ServiceStatusCard() {
  const [info, setInfo] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    api
      .get('/public/info', { signal: controller.signal })
      .then(setInfo)
      .catch((err) => {
        if (err.name !== 'AbortError') setError(err)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [attempt])

  const retry = () => {
    setLoading(true)
    setError(null)
    setAttempt((value) => value + 1)
  }

  return (
    <div className="card shadow-sm border-0">
      <div className="card-body">
        <h2 className="h5 card-title">Stato del servizio</h2>
        {loading && <LoadingSpinner label="Verifica della connessione al server…" />}
        <ErrorAlert error={error} onRetry={retry} />
        {info && !loading && (
          <p className="mb-0">
            <span className="badge text-bg-success me-2">Online</span>
            Server raggiungibile · ora della squadra: {romeFormatter.format(new Date(info.serverTime))}
          </p>
        )}
      </div>
    </div>
  )
}
