export default function ErrorAlert({ error, onRetry }) {
  if (!error) return null
  return (
    <div className="alert alert-danger d-flex justify-content-between align-items-center gap-3" role="alert">
      <span>{error.message || 'Si è verificato un errore imprevisto.'}</span>
      {onRetry && (
        <button type="button" className="btn btn-sm btn-outline-danger" onClick={onRetry}>
          Riprova
        </button>
      )}
    </div>
  )
}
