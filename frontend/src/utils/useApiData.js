import { useEffect, useState } from 'react'
import { api } from '../api/client'

/**
 * Carica dati da un endpoint GET, con stato di caricamento ed errore.
 * Con `path` null non fa nulla. `reload` ripete la richiesta; `setData` aggiorna i dati localmente.
 */
export function useApiData(path) {
  const [state, setState] = useState({ data: null, error: null, path: null })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!path) return undefined
    const controller = new AbortController()
    api
      .get(path, { signal: controller.signal })
      .then((data) => setState({ data, error: null, path }))
      .catch((error) => error.name !== 'AbortError' && setState({ data: null, error, path }))
    return () => controller.abort()
  }, [path, attempt])

  // Finché non arriva la risposta per il percorso attuale si mostra il caricamento (es. cambio mese).
  const current = state.path === path
  return {
    data: current ? state.data : null,
    error: current ? state.error : null,
    loading: !current,
    reload: () => setAttempt((a) => a + 1),
    setData: (data) => setState({ data, error: null, path }),
  }
}
