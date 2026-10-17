import { useEffect, useState } from 'react'
import { api } from '../api/client'
import ErrorAlert from '../components/common/ErrorAlert'
import LoadingSpinner from '../components/common/LoadingSpinner'

const romeFormatter = new Intl.DateTimeFormat('it-IT', {
  dateStyle: 'full',
  timeStyle: 'short',
  timeZone: 'Europe/Rome',
})

export default function HomePage() {
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
        <h1 className="display-6 fw-bold mb-2">Benvenuto nel gestionale della squadra</h1>
        <p className="lead mb-0">Calendario, disponibilità, convocazioni, risultati e pagamenti in un unico posto.</p>
      </section>

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
