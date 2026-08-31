import { useEffect, useRef, type ReactNode } from 'react'

interface ModalProps {
  title: string
  isOpen: boolean
  onClose: () => void
  children: ReactNode
}

/**
 * Built on the native `<dialog>` element, which gives focus trapping, Escape handling and the
 * top-layer backdrop for free — all of which are easy to get subtly wrong by hand.
 */
export function Modal({ title, isOpen, onClose, children }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return

    if (isOpen && !dialog.open) {
      dialog.showModal()
    } else if (!isOpen && dialog.open) {
      dialog.close()
    }
  }, [isOpen])

  return (
    <dialog
      ref={dialogRef}
      // Escape closes the dialog natively; this keeps React's state in step with that.
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClose={onClose}
      // Clicking the backdrop lands on the dialog element itself rather than its content.
      onClick={(event) => {
        if (event.target === dialogRef.current) onClose()
      }}
      aria-label={title}
      className="w-full max-w-xl rounded-panel border border-line bg-surface p-0 text-fg backdrop:bg-ink/70 backdrop:backdrop-blur-sm open:m-auto"
    >
      <header className="flex items-center justify-between gap-4 border-b border-line px-5 py-4">
        <h2 className="text-base font-semibold text-fg">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="rounded-lg px-2 py-1 text-fg-muted transition-colors hover:bg-surface-raised hover:text-fg"
        >
          ✕
        </button>
      </header>
      <div className="p-5">{children}</div>
    </dialog>
  )
}
