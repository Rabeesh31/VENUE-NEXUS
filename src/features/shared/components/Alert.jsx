export default function Alert({
  type = "error", // error, success, warning, info
  message,
  traceId,
  onDismiss
}) {
  if (!message) return null;

  const ariaLive = type === "error" ? "assertive" : "polite";

  return (
    <div className={`vn-alert vn-alert-${type}`} role="alert" aria-live={ariaLive}>
      <div className="vn-alert-content">
        <span className="vn-alert-icon" aria-hidden="true">
          {type === "error" && "⚠"}
          {type === "success" && "✓"}
          {type === "warning" && "!"}
          {type === "info" && "ℹ"}
        </span>
        <div className="vn-alert-text">
          <p className="vn-alert-message">{message}</p>
          {traceId && (
            <p className="vn-alert-trace">
              Trace ID for support: <code>{traceId}</code>
            </p>
          )}
        </div>
      </div>
      {onDismiss && (
        <button
          type="button"
          className="vn-alert-dismiss"
          onClick={onDismiss}
          aria-label="Dismiss alert"
        >
          &times;
        </button>
      )}
    </div>
  );
}

