// Predisposizione PWA: registrazione del service worker, aggiornamenti e installazione dell'app.
// Lo stato è un piccolo store esterno a React, letto dai componenti con useSyncExternalStore (AppStatus.jsx).

let applyUpdate = null // funzione che attiva la nuova versione in attesa, se c'è
let installPrompt = null // evento beforeinstallprompt salvato (Chrome, Edge, Android)
const listeners = new Set()

const emit = () => listeners.forEach((listener) => listener())

export const pwaStore = {
  subscribe(listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  getApplyUpdate: () => applyUpdate,
  getInstallPrompt: () => installPrompt,
}

/** Segnala che è pronta una nuova versione; {@code apply} la attiva. */
export function notifyUpdate(apply) {
  applyUpdate = apply
  emit()
}

/** Salva l'evento di installazione per mostrarlo quando l'utente lo chiede. */
export function captureInstallPrompt(event) {
  event.preventDefault()
  installPrompt = event
  emit()
}

/** Mostra il prompt di installazione del browser; l'evento si può usare una sola volta. */
export async function promptInstall() {
  const event = installPrompt
  if (!event) return false
  installPrompt = null
  emit()
  await event.prompt()
  const { outcome } = await event.userChoice
  return outcome === 'accepted'
}

/** True se l'app è già aperta come app installata (finestra standalone). */
export function isStandalone() {
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true
}

/**
 * Registra il service worker. Chiamata solo nella build di produzione (main.jsx): in sviluppo la cache
 * nasconderebbe le modifiche. Quando il nuovo service worker è installato e una versione precedente controlla
 * già la pagina, chiede all'utente di aggiornare; dopo l'attivazione ricarica la pagina una sola volta.
 */
export function registerServiceWorker() {
  window.addEventListener('beforeinstallprompt', captureInstallPrompt)
  window.addEventListener('appinstalled', () => {
    installPrompt = null
    emit()
  })
  if (!('serviceWorker' in navigator)) return

  // Al primo avvio non c'è un controller: clients.claim() non deve causare un ricaricamento.
  const hadController = Boolean(navigator.serviceWorker.controller)
  let reloading = false
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (hadController && !reloading) {
      reloading = true
      window.location.reload()
    }
  })

  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js')
      const offer = (worker) => notifyUpdate(() => worker.postMessage({ type: 'SKIP_WAITING' }))
      if (registration.waiting && navigator.serviceWorker.controller) offer(registration.waiting)
      registration.addEventListener('updatefound', () => {
        const worker = registration.installing
        worker?.addEventListener('statechange', () => {
          if (worker.state === 'installed' && navigator.serviceWorker.controller) offer(worker)
        })
      })
    } catch (error) {
      // L'app funziona anche senza service worker (es. browser in modalità privata).
      console.warn('Service worker non registrato:', error)
    }
  })
}
