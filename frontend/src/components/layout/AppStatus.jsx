import { useState, useSyncExternalStore } from 'react'
import { isStandalone, promptInstall, pwaStore } from '../../pwa'

function subscribeOnline(callback) {
  window.addEventListener('online', callback)
  window.addEventListener('offline', callback)
  return () => {
    window.removeEventListener('online', callback)
    window.removeEventListener('offline', callback)
  }
}

/** Avvisi globali dell'app, sotto la barra in alto: connessione assente e nuova versione disponibile. */
export function AppStatusBanners() {
  const online = useSyncExternalStore(subscribeOnline, () => navigator.onLine)
  const applyUpdate = useSyncExternalStore(pwaStore.subscribe, pwaStore.getApplyUpdate)
  const [updating, setUpdating] = useState(false)

  if (online && !applyUpdate) return null
  return (
    <div className="container pt-3">
      {!online && (
        <div className="alert alert-warning py-2 mb-2 small" role="status">
          Sei offline: i dati potrebbero non essere aggiornati e le modifiche non verranno salvate finché la connessione
          non torna.
        </div>
      )}
      {applyUpdate && (
        <div className="alert alert-info py-2 mb-2 small d-flex flex-wrap justify-content-between align-items-center gap-2"
          role="status">
          <span>È disponibile una nuova versione dell'app.</span>
          <button type="button" className="btn btn-sm btn-primary" disabled={updating}
            onClick={() => {
              setUpdating(true)
              applyUpdate()
            }}>
            Aggiorna
          </button>
        </div>
      )}
    </div>
  )
}

/**
 * Riquadro "App sul telefono" nel profilo: installazione con un clic dove il browser lo permette
 * (Chrome, Edge, Android), istruzioni manuali per Safari su iPhone e iPad.
 */
export function InstallAppCard() {
  const installPrompt = useSyncExternalStore(pwaStore.subscribe, pwaStore.getInstallPrompt)
  const [installed, setInstalled] = useState(() => isStandalone())

  return (
    <div className="card shadow-sm border-0 mt-3">
      <div className="card-body">
        <h2 className="h5">App sul telefono</h2>
        {installed ? (
          <p className="text-secondary mb-0">Stai usando l'app installata.</p>
        ) : installPrompt ? (
          <>
            <p className="small">Installa il gestionale sulla schermata Home: si apre a schermo intero, come un'app.</p>
            <button type="button" className="btn btn-primary btn-sm" onClick={async () => setInstalled(await promptInstall())}>
              Installa l'app
            </button>
          </>
        ) : (
          <p className="small mb-0">
            Puoi aggiungere il gestionale alla schermata Home. Su iPhone e iPad apri il sito con Safari, tocca{' '}
            <strong>Condividi</strong> e poi <strong>Aggiungi alla schermata Home</strong>. Su Android usa il menu del
            browser e scegli <strong>Installa app</strong>.
          </p>
        )}
      </div>
    </div>
  )
}
