/*
 * Service worker dell'app (registrato solo nella build di produzione, vedi src/pwa.js).
 *
 * - App shell in cache: index.html, JavaScript e CSS della build, icone e pagina offline. L'elenco dei file e la
 *   versione vengono inseriti a fine build dal plugin "service-worker-manifest" di vite.config.js: a ogni deploy
 *   il service worker cambia, il browser lo scarica e le cache delle versioni precedenti vengono eliminate.
 * - Le API (/api/...) non passano MAI dalla cache: contengono dati personali e di autenticazione e devono essere
 *   sempre aggiornate. Senza rete l'app mostra i suoi messaggi di errore.
 * - Navigazione "network-first": si prova sempre la rete (index.html aggiornato); offline si usa la shell in
 *   cache e, se manca anche quella, offline.html.
 * - La nuova versione si attiva solo quando l'utente sceglie "Aggiorna" (messaggio SKIP_WAITING), così una pagina
 *   aperta non si ritrova a metà con file di due versioni diverse.
 */

const VERSION = '__BUILD_VERSION__'
const PRECACHE = [] /* __PRECACHE__ */
const CACHE_PREFIX = 'squadra-'
const CACHE = CACHE_PREFIX + VERSION
const SHELL_URL = '/'
const OFFLINE_URL = '/offline.html'

/** Strategia per una richiesta: 'navigate', 'cache-first' oppure 'network' (il service worker non interviene). */
function routeFor(request) {
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin) return 'network'
  if (url.pathname.startsWith('/api/')) return 'network'
  if (request.mode === 'navigate') return 'navigate'
  // File con hash nel nome (immutabili) e icone: si possono servire dalla cache.
  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) return 'cache-first'
  return 'network'
}

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith(CACHE_PREFIX) && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting()
})

self.addEventListener('fetch', (event) => {
  const route = routeFor(event.request)
  if (route === 'navigate') {
    event.respondWith(
      fetch(event.request).catch(async () => (await caches.match(SHELL_URL)) ?? caches.match(OFFLINE_URL)),
    )
  } else if (route === 'cache-first') {
    event.respondWith(
      caches.match(event.request).then(
        (hit) =>
          hit ??
          fetch(event.request).then((response) => {
            if (response.ok) {
              const copy = response.clone()
              caches.open(CACHE).then((cache) => cache.put(event.request, copy))
            }
            return response
          }),
      ),
    )
  }
})
