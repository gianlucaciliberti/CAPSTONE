import { useSyncExternalStore } from 'react'

function subscribeOnline(callback) {
  window.addEventListener('online', callback)
  window.addEventListener('offline', callback)
  return () => {
    window.removeEventListener('online', callback)
    window.removeEventListener('offline', callback)
  }
}

/** Avviso globale dell'app, sotto la barra in alto, quando manca la connessione. */
export function AppStatusBanners() {
  const online = useSyncExternalStore(subscribeOnline, () => navigator.onLine)

  if (online) return null
  return (
    <div className="container pt-3">
      <div className="alert alert-warning py-2 mb-2 small" role="status">
        Sei offline: i dati potrebbero non essere aggiornati e le modifiche non verranno salvate finché la connessione
        non torna.
      </div>
    </div>
  )
}
