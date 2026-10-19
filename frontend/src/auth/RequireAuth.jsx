import { Link, Navigate, Outlet, useLocation } from 'react-router'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { useAuth } from './authContext'

/**
 * Protegge le rotte figlie: richiede un utente autenticato e, se indicato, un ruolo minimo.
 * È solo una comodità di interfaccia: i permessi veri sono sempre verificati dal backend.
 */
export function RequireAuth({ role }) {
  const { status, hasRole } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <LoadingSpinner label="Verifica della sessione…" />
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  if (role && !hasRole(role)) {
    return (
      <div className="alert alert-warning" role="alert">
        Non hai i permessi per accedere a questa pagina. <Link to="/">Torna alla home</Link>
      </div>
    )
  }
  return <Outlet />
}

/** Pagine per utenti non autenticati (login, registrazione...): chi è già dentro va alla home. */
export function RequireAnonymous() {
  const { status } = useAuth()
  if (status === 'loading') return <LoadingSpinner label="Verifica della sessione…" />
  if (status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}
