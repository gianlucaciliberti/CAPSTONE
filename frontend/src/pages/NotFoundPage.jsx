import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <div className="text-center py-5">
      <h1 className="display-5 fw-bold">404</h1>
      <p className="lead">La pagina che cerchi non esiste.</p>
      <Link className="btn btn-primary" to="/">
        Torna alla home
      </Link>
    </div>
  )
}
