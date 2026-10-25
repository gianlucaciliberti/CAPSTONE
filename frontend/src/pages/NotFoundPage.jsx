import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <section className="pitch-panel notfound-panel" aria-labelledby="notfound-title">
      <h1 id="notfound-title" className="pitch-opponent">Palla fuori campo</h1>
      <p className="pitch-meta">Errore 404: la pagina che cerchi non esiste.</p>
      <Link className="btn btn-primary" to="/">
        Torna alla home
      </Link>
    </section>
  )
}
