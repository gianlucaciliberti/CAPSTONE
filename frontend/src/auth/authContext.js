import { createContext, useContext } from 'react'

export const AuthContext = createContext(null)

/** Stato e azioni di autenticazione: { status, user, login, logout, setSession, hasRole }. */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth deve essere usato dentro <AuthProvider>')
  return context
}

/** Gerarchia dei ruoli, come nel backend: ADMIN include COACH, che include PLAYER. */
const ROLE_LEVEL = { PLAYER: 1, COACH: 2, ADMIN: 3 }

export function roleIncludes(userRole, requiredRole) {
  return (ROLE_LEVEL[userRole] ?? 0) >= (ROLE_LEVEL[requiredRole] ?? Infinity)
}

export const ROLE_LABELS = { PLAYER: 'Giocatore', COACH: 'Allenatore', ADMIN: 'Amministratore' }

export const STATUS_LABELS = {
  PENDING: 'In attesa',
  ACTIVE: 'Attivo',
  REJECTED: 'Rifiutato',
  SUSPENDED: 'Sospeso',
}
