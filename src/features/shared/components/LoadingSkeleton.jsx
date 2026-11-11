export default function LoadingSkeleton({ rows = 4, message = "Loading data…" }) {
  return (
    <div className="vn-skeleton-container" role="status" aria-busy="true">
      <div className="vn-skeleton-spinner" aria-hidden="true" />
      <p className="muted vn-skeleton-text">{message}</p>
      <div className="vn-skeleton-lines">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="vn-skeleton-bar" style={{ width: `${80 - (i % 3) * 15}%` }} />
        ))}
      </div>
    </div>
  );
}

