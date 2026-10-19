import { useCallback, useEffect, useMemo, useState } from 'react'
import { authApi, refreshSession, setAccessToken, setSessionExpiredHandler } from '../api/client'
import { AuthContext, roleIncludes } from './authContext'

/**
 * Gestisce la sessione dell'utente.
 * All'avvio prova a ripristinare la sessione con il cookie HttpOnly del refresh token: in questo modo
 * l'access token resta solo in memoria e un ricaricamento della pagina non obbliga a rifare il login.
 */
export default function AuthProvider({ children }) {
  // status: 'loading' durante il ripristino iniziale, poi 'authenticated' o 'anonymous'
  const [status, setStatus] = useState('loading')
  const [user, setUser] = useState(null)

  const setSession = useCallback((session) => {
    setAccessToken(session.accessToken)
    setUser(session.user)
    setStatus('authenticated')
  }, [])

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setStatus('anonymous')
  }, [])

  useEffect(() => {
    setSessionExpiredHandler(clearSession)
    refreshSession()
      .then(setSession)
      .catch(clearSession)
  }, [setSession, clearSession])

  const login = useCallback(
    async (email, password) => {
      const session = await authApi.login(email, password)
      setSession(session)
      return session.user
    },
    [setSession],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }, [clearSession])

  const hasRole = useCallback((role) => user !== null && roleIncludes(user.role, role), [user])

  const value = useMemo(
    () => ({ status, user, login, logout, setSession, hasRole }),
    [status, user, login, logout, setSession, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
