import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { MyCallupsCard } from './CallupPages'
import { UpcomingMatchesCard } from './MatchPages'

const romeFormatter = new Intl.DateTimeFormat('it-IT', {
  dateStyle: 'full',
  timeStyle: 'short',
  timeZone: 'Europe/Rome',
})

export default function HomePage() {
  const { status, user, hasRole } = useAuth()
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

      {status === 'authenticated' && (
        <div className="row g-4">
          <div className="col-lg-6">
            <UpcomingMatchesCard />
          </div>
          <div className="col-lg-6">
            <MyCallupsCard />
          </div>
        </div>
      )}

      {hasRole('ADMIN') && (
        <div className="alert alert-light border d-flex justify-content-between align-items-center">
          <span>Gestisci approvazioni, sospensioni e ruoli degli utenti.</span>
          <Link className="btn btn-sm btn-primary" to="/admin/users">
            Gestione utenti
          </Link>
        </div>
      )}

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
    </>
  )
}
