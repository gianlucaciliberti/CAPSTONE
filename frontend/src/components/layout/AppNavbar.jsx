import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { ROLE_LABELS, useAuth } from '../../auth/authContext'

export default function AppNavbar() {
  const { status, user, hasRole, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const links = [{ to: '/', label: 'Home', end: true }]
  if (status === 'authenticated') {
    links.push({ to: '/calendar', label: 'Calendario' })
    links.push({ to: '/availability', label: 'Disponibilità' })
    if (hasRole('ADMIN')) links.push({ to: '/admin/users', label: 'Utenti' })
    links.push({ to: '/profile', label: 'Profilo' })
  }

  const onLogout = async () => {
    setOpen(false)
    await logout()
    navigate('/login')
  }

  return (
    <nav className="navbar navbar-expand-lg navbar-dark app-navbar">
      <div className="container">
        <Link className="navbar-brand fw-bold d-flex align-items-center gap-2" to="/">
          <span className="brand-badge" aria-hidden="true">⚽</span>
          Gestionale Squadra
        </Link>
        <button
          className="navbar-toggler"
          type="button"
          aria-controls="main-navbar"
          aria-expanded={open}
          aria-label="Apri il menu di navigazione"
          onClick={() => setOpen((value) => !value)}
        >
          <span className="navbar-toggler-icon" />
        </button>
        <div className={`collapse navbar-collapse${open ? ' show' : ''}`} id="main-navbar">
          <ul className="navbar-nav me-auto">
            {links.map((link) => (
              <li className="nav-item" key={link.to}>
                <NavLink className="nav-link" to={link.to} end={link.end} onClick={() => setOpen(false)}>
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
          {status === 'authenticated' && (
            <div className="d-flex align-items-center gap-2 text-white">
              <span className="small">
                {user.firstName} <span className="opacity-75">· {ROLE_LABELS[user.role]}</span>
              </span>
              <button type="button" className="btn btn-sm btn-outline-light" onClick={onLogout}>
                Esci
              </button>
            </div>
          )}
          {status === 'anonymous' && (
            <div className="d-flex gap-2">
              <Link className="btn btn-sm btn-light" to="/login" onClick={() => setOpen(false)}>
                Accedi
              </Link>
              <Link className="btn btn-sm btn-outline-light" to="/register" onClick={() => setOpen(false)}>
                Registrati
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  )
}
