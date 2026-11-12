export default function EmptyState({
  title = "No records found",
  message = "Get started by creating your first entry.",
  actionLabel,
  onAction,
  icon = "📂"
}) {
  return (
    <div className="vn-empty-state">
      <div className="vn-empty-icon" aria-hidden="true">{icon}</div>
      <h3 className="vn-empty-title">{title}</h3>
      <p className="vn-empty-message muted">{message}</p>
      {actionLabel && onAction && (
        <button type="button" className="primary-button" onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </div>
  );
}

