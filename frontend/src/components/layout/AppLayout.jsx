import { Outlet } from 'react-router'
import AppNavbar from './AppNavbar'

export default function AppLayout() {
  return (
    <div className="d-flex flex-column min-vh-100">
      <AppNavbar />
      <main className="container py-4 flex-grow-1">
        <Outlet />
      </main>
      <footer className="app-footer py-3">
        <div className="container small text-secondary">Gestionale Squadra · uso interno della squadra</div>
      </footer>
    </div>
  )
}
