const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const GENERIC_ERROR = 'Si è verificato un errore imprevisto. Riprova più tardi.'
const NETWORK_ERROR = 'Impossibile contattare il server. Controlla la connessione e riprova.'

/** Errore restituito dalle API (formato ProblemDetail RFC 9457 del backend). */
export class ApiError extends Error {
  constructor(status, message, code, fieldErrors = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

async function parseError(response) {
  let body = null
  try {
    body = await response.json()
  } catch {
    // Risposta senza corpo JSON (es. errore del proxy): si usa il messaggio generico.
  }
  return new ApiError(
    response.status,
    body?.detail || GENERIC_ERROR,
    body?.code || 'UNKNOWN',
    body?.fieldErrors || {},
  )
}

/**
 * Chiamata alle API del backend. Le credenziali (cookie HttpOnly) viaggiano solo verso la stessa origine:
 * nessun token viene mai salvato in localStorage.
 */
export async function apiRequest(path, { method = 'GET', body, headers = {}, signal } = {}) {
  const init = {
    method,
    signal,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...headers },
  }
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, init)
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError(0, NETWORK_ERROR, 'NETWORK_ERROR')
  }

  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return null
  return response.json()
}

export const api = {
  get: (path, options) => apiRequest(path, { ...options, method: 'GET' }),
  post: (path, body, options) => apiRequest(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => apiRequest(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options) => apiRequest(path, { ...options, method: 'PATCH', body }),
  delete: (path, options) => apiRequest(path, { ...options, method: 'DELETE' }),
}
