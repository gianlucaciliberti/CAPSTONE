/*
 * Disattivazione del vecchio service worker. L'app non è più installabile e non registra service worker, ma i
 * browser che hanno aperto il sito quando lo era ne hanno ancora uno attivo: al controllo degli aggiornamenti
 * scaricano questo file, che cancella le cache dell'app e si rimuove. Si potrà eliminare quando nessun
 * dispositivo avrà più la vecchia versione (o sostituire quando l'app tornerà installabile).
 */

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key.startsWith('squadra-')).map((key) => caches.delete(key))))
      .then(() => self.registration.unregister())
      .then(() => self.clients.matchAll({ type: 'window' }))
      .then((clients) => clients.forEach((client) => client.navigate(client.url))),
  )
})
