import { useEffect, useRef } from 'react'

/**
 * Finestra di conferma per le operazioni sensibili (stile modale Bootstrap, gestita da React).
 * Esc o "Annulla" chiudono senza confermare.
 */
export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Conferma',
  confirmVariant = 'primary',
  loading = false,
  onConfirm,
  onCancel,
}) {
  const cancelRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    cancelRef.current?.focus()
    const onKey = (event) => {
      if (event.key === 'Escape' && !loading) onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, loading, onCancel])

  if (!open) return null

  return (
    <>
      <div className="modal d-block" tabIndex="-1" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content">
            <div className="modal-header">
              <h2 id="confirm-title" className="modal-title h5">
                {title}
              </h2>
            </div>
            <div className="modal-body">{message}</div>
            <div className="modal-footer">
              <button ref={cancelRef} type="button" className="btn btn-outline-secondary" onClick={onCancel}
                disabled={loading}>
                Annulla
              </button>
              <button type="button" className={`btn btn-${confirmVariant}`} onClick={onConfirm} disabled={loading}>
                {loading && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                {confirmLabel}
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  )
}
