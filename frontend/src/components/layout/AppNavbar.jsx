import { useState } from 'react'
import { Link, NavLink } from 'react-router'

const links = [{ to: '/', label: 'Home', end: true }]

export default function AppNavbar() {
  const [open, setOpen] = useState(false)

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
          <ul className="navbar-nav ms-auto">
            {links.map((link) => (
              <li className="nav-item" key={link.to}>
                <NavLink className="nav-link" to={link.to} end={link.end} onClick={() => setOpen(false)}>
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </nav>
  )
}
