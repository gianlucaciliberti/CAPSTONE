/** Campo di un form Bootstrap con etichetta, messaggio di errore e testo di aiuto (input o textarea). */
export function FormField({ id, label, error, hint, type = 'text', multiline = false, ...inputProps }) {
  const Control = multiline ? 'textarea' : 'input'
  return (
    <div className="mb-3">
      <label htmlFor={id} className="form-label">
        {label}
      </label>
      <Control
        id={id}
        name={id}
        type={multiline ? undefined : type}
        className={`form-control${error ? ' is-invalid' : ''}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...inputProps}
      />
      {error && (
        <div id={`${id}-error`} className="invalid-feedback">
          {error}
        </div>
      )}
      {!error && hint && (
        <div id={`${id}-hint`} className="form-text">
          {hint}
        </div>
      )}
    </div>
  )
}

/** Pulsante di invio con stato di caricamento: disabilitato durante l'invio per evitare doppi clic. */
export function SubmitButton({ loading, children, loadingLabel = 'Attendi…', className = 'btn btn-primary w-100' }) {
  return (
    <button type="submit" className={className} disabled={loading}>
      {loading && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
      {loading ? loadingLabel : children}
    </button>
  )
}

/** Riquadro centrato per le pagine di autenticazione. */
export function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="row justify-content-center">
      <div className="col-12 col-sm-10 col-md-7 col-lg-5">
        <div className="card shadow-sm border-0">
          <div className="card-body p-4">
            <h1 className="h4 mb-1">{title}</h1>
            {subtitle && <p className="text-secondary mb-4">{subtitle}</p>}
            {children}
          </div>
          {footer && <div className="card-footer text-center small py-3">{footer}</div>}
        </div>
      </div>
    </div>
  )
}
