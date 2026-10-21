import { Outlet } from 'react-router'
import { useAuth } from '../../auth/authContext'
import AppNavbar, { BottomNav } from './AppNavbar'

export default function AppLayout() {
  const { status } = useAuth()
  const authenticated = status === 'authenticated'
  return (
    <div className={`d-flex flex-column min-vh-100${authenticated ? ' has-bottom-nav' : ''}`}>
      <AppNavbar />
      <main className="container py-3 py-md-4 flex-grow-1">
        <Outlet />
      </main>
      <footer className="app-footer py-3">
        <div className="container small text-secondary">Gestionale Squadra · uso interno della squadra</div>
      </footer>
      {authenticated && <BottomNav />}
    </div>
  )
}
