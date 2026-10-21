import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatDateTime, formatDaysFromToday, formatLongDate, formatTime } from '../utils/dates'
import { formatEuro } from '../utils/money'
import { useApiData } from '../utils/useApiData'
import { AvailabilityBadge } from './AvailabilityPages'
import { MyCallupsCard } from './CallupPages'
import { UpcomingMatchesCard } from './MatchPages'
import { ResultBadge } from './ScoresheetPages'

const romeFormatter = new Intl.DateTimeFormat('it-IT', {
  dateStyle: 'full',
  timeStyle: 'short',
  timeZone: 'Europe/Rome',
})

export default function HomePage() {
  const { status, user } = useAuth()

  return (
    <>
      <section className="hero-pitch rounded-4 p-4 p-md-5 mb-4 text-white">
        <h1 className="display-6 fw-bold mb-2">
          {user ? `Ciao ${user.firstName}!` : 'Benvenuto nel gestionale della squadra'}
        </h1>
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

      {status === 'authenticated' ? <Dashboard /> : <ServiceStatusCard />}
    </>
  )
}

/** Home dell'utente autenticato: una sola chiamata a /dashboard più gli elenchi di partite e convocazioni. */
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
            <PaymentsCard payments={data.payments} />
            <LastResultCard match={data.lastResult} />
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
      <div className="card shadow-sm border-0 h-100">
        <div className="card-body">
          <h2 className="h5">Prossima partita</h2>
          <p className="text-secondary mb-0">Nessuna partita in calendario: l'Admin la aggiungerà a breve.</p>
        </div>
      </div>
    )
  }
  const { match } = next
  return (
    <div className="card shadow-sm border-0 h-100" aria-labelledby="next-match-title">
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-start gap-2">
          <h2 id="next-match-title" className="h5 mb-0">
            Prossima partita
          </h2>
          <span className="badge text-bg-primary text-capitalize">{formatDaysFromToday(match.kickoffAt, now)}</span>
        </div>
        <p className="h4 mt-2 mb-1">vs {match.opponent}</p>
        <p className="text-secondary mb-3">
          <span className="text-capitalize">{formatLongDate(match.kickoffAt)}</span> · ore {formatTime(match.kickoffAt)}
          <br />
          {match.venueName}
        </p>

        {next.callupPublished ? (
          <div className={`alert py-2 ${next.convocated ? 'alert-success' : 'alert-light border'}`}>
            {next.convocated ? 'Sei convocato per questa partita.' : 'Non sei tra i convocati per questa partita.'}
          </div>
        ) : (
          <p className="small text-secondary">Convocazione non ancora pubblicata.</p>
        )}

        <div className="d-flex flex-wrap align-items-center gap-2">
          <span className="small">La tua disponibilità:</span>
          <AvailabilityBadge status={next.myAvailability} />
        </div>
        <Link className={`btn btn-sm mt-3 ${next.availabilityOpen && next.myAvailability === 'PENDING' ? 'btn-primary' : 'btn-outline-primary'}`}
          to={`/matches/${match.id}`}>
          {next.availabilityOpen ? (next.myAvailability === 'PENDING' ? 'Rispondi ora' : 'Modifica disponibilità') : 'Dettagli partita'}
        </Link>
      </div>
    </div>
  )
}

function PaymentsCard({ payments }) {
  const due = payments.outstandingCents > 0
  return (
    <div className="card shadow-sm border-0">
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-center mb-2">
          <h2 className="h5 mb-0">I tuoi pagamenti</h2>
          <Link to="/payments" className="small">
            Dettagli
          </Link>
        </div>
        {due ? (
          <p className="mb-0">
            Da pagare: <strong>{formatEuro(payments.outstandingCents)}</strong>
            {payments.overdueCount > 0 && <span className="badge text-bg-danger ms-2">{payments.overdueCount} scadute</span>}
          </p>
        ) : (
          <p className="text-secondary mb-0">Nessun pagamento in sospeso.</p>
        )}
      </div>
    </div>
  )
}

function LastResultCard({ match }) {
  return (
    <div className="card shadow-sm border-0">
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-center mb-2">
          <h2 className="h5 mb-0">Ultimo risultato</h2>
          <Link to="/results" className="small">
            Tutti i risultati
          </Link>
        </div>
        {match ? (
          <p className="mb-0">
            <ResultBadge match={match} className="me-2" />
            <Link to={`/matches/${match.id}`} className="fw-semibold">
              Noi {match.teamGoals} - {match.opponentGoals} {match.opponent}
            </Link>
            <span className="d-block small text-secondary">{formatDateTime(match.kickoffAt)}</span>
          </p>
        ) : (
          <p className="text-secondary mb-0">Nessuna partita conclusa.</p>
        )}
      </div>
    </div>
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
