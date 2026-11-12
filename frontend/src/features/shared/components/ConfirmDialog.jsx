import Modal from "./Modal";

export default function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title = "Confirm Action",
  message,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  variant = "danger", // danger, warning, primary
  isBusy = false
}) {
  return (
    <Modal isOpen={isOpen} onClose={isBusy ? () => {} : onClose} title={title} maxWidth="460px">
      <div className="vn-confirm-content">
        <p className="vn-confirm-message">{message}</p>
        <div className="vn-confirm-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={onClose}
            disabled={isBusy}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={variant === "danger" ? "danger-button" : "primary-button"}
            onClick={onConfirm}
            disabled={isBusy}
          >
            {isBusy ? "Processing…" : confirmLabel}
          </button>
        </div>
      </div>
    </Modal>
  );
}

