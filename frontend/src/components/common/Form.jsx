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

/**
 * Riquadro centrato per le pagine di accesso: in alto una metà campo verde con il titolo, il modulo sotto la linea
 * di metà campo, come si entra in campo dal centro.
 */
export function AuthCard({ title, subtitle, children, footer }) {
  return (
    <section className="auth-card">
      <header className="auth-pitch">
        <h1 className="auth-title">{title}</h1>
        {subtitle && <p className="auth-subtitle">{subtitle}</p>}
      </header>
      <div className="auth-body">{children}</div>
      {footer && <div className="auth-footer">{footer}</div>}
    </section>
  )
}
