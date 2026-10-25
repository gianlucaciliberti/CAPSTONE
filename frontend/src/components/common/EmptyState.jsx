/**
 * Stato vuoto: il cerchio di centrocampo disegnato in CSS (un campo ancora senza gioco), la frase che dice cosa
 * manca e, se serve, cosa succederà o cosa si può fare. `compact` per i riquadri piccoli e le celle di tabella.
 */
export default function EmptyState({ children, hint, action, compact = false }) {
  return (
    <div className={`empty-state${compact ? ' is-compact' : ''}`}>
      <span className="empty-mark" aria-hidden="true" />
      <p className="empty-text">{children}</p>
      {hint && <p className="empty-hint">{hint}</p>}
      {action && <div className="empty-action">{action}</div>}
    </div>
  )
}
