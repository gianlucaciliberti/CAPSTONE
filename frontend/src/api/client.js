const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const GENERIC_ERROR = 'Si è verificato un errore imprevisto. Riprova più tardi.'
const NETWORK_ERROR = 'Impossibile contattare il server. Controlla la connessione e riprova.'
/** Header richiesto dal backend sugli endpoint autenticati dal cookie (protezione CSRF). */
const CSRF_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' }

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

// L'access token vive solo in memoria (mai in localStorage/sessionStorage): un ricaricamento della pagina
// lo perde e la sessione viene ripristinata con il refresh token, che sta in un cookie HttpOnly.
let accessToken = null
let refreshPromise = null
let sessionExpiredHandler = () => {}

export function setAccessToken(token) {
  accessToken = token
}

export function hasAccessToken() {
  return accessToken !== null
}

/** Funzione chiamata quando la sessione non è più rinnovabile (es. per riportare l'utente al login). */
export function setSessionExpiredHandler(handler) {
  sessionExpiredHandler = handler
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

async function send(path, { method = 'GET', body, headers = {}, signal } = {}) {
  const init = {
    method,
    signal,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...headers },
  }
  if (accessToken) init.headers.Authorization = `Bearer ${accessToken}`
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

/**
 * Rinnova la sessione con il cookie del refresh token e restituisce { accessToken, expiresAt, user }.
 * - Nella stessa scheda le chiamate contemporanee condividono un'unica richiesta.
 * - Tra schede diverse le richieste sono messe in fila con la Web Locks API: ogni scheda usa il cookie
 *   già ruotato dalla precedente (il backend considera il riuso di un token ruotato un furto di sessione).
 */
export function refreshSession() {
  if (!refreshPromise) {
    const doRefresh = () => send('/auth/refresh', { method: 'POST', headers: CSRF_HEADERS })
    const locked = globalThis.navigator?.locks
      ? navigator.locks.request('gestionale-auth-refresh', doRefresh)
      : doRefresh()
    refreshPromise = locked
      .then((data) => {
        accessToken = data.accessToken
        return data
      })
      .catch((error) => {
        accessToken = null
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

/**
 * Chiamata alle API. Se l'access token è scaduto (401) prova una sola volta a rinnovare la sessione
 * e ripete la richiesta; se il rinnovo fallisce avvisa l'applicazione che la sessione è terminata.
 */
export async function apiRequest(path, options = {}) {
  try {
    return await send(path, options)
  } catch (error) {
    // Si rinnova solo se la richiesta era autenticata: un 401 senza token (es. login errato) è un esito normale.
    const canRefresh = error instanceof ApiError && error.status === 401 && accessToken && path !== '/auth/refresh'
    if (!canRefresh) throw error
    try {
      await refreshSession()
    } catch {
      sessionExpiredHandler()
      throw error
    }
    return send(path, options)
  }
}

export const api = {
  get: (path, options) => apiRequest(path, { ...options, method: 'GET' }),
  post: (path, body, options) => apiRequest(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => apiRequest(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options) => apiRequest(path, { ...options, method: 'PATCH', body }),
  delete: (path, options) => apiRequest(path, { ...options, method: 'DELETE' }),
}

/** Endpoint di autenticazione. */
export const authApi = {
  login: (email, password) => send('/auth/login', { method: 'POST', body: { email, password } }),
  logout: () => send('/auth/logout', { method: 'POST', headers: CSRF_HEADERS }),
  register: (data) => send('/auth/register', { method: 'POST', body: data }),
  me: () => api.get('/auth/me'),
  verifyEmail: (token) => send('/auth/verify-email', { method: 'POST', body: { token } }),
  resendVerification: (email) => send('/auth/resend-verification', { method: 'POST', body: { email } }),
  forgotPassword: (email) => send('/auth/forgot-password', { method: 'POST', body: { email } }),
  resetPassword: (token, newPassword) =>
    send('/auth/reset-password', { method: 'POST', body: { token, newPassword } }),
  changePassword: (currentPassword, newPassword) =>
    api.post('/auth/change-password', { currentPassword, newPassword }),
}
