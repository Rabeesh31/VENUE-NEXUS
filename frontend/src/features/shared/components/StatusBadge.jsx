export default function StatusBadge({ status, type = "default" }) {
  const norm = String(status || "").toUpperCase();

  let icon = "●";
  let label = norm || "Unknown";
  let variant = "default";

  // QR Statuses
  if (norm === "ACTIVE") {
    icon = "✓";
    label = "Active";
    variant = "success";
  } else if (norm === "REVOKED") {
    icon = "✕";
    label = "Revoked";
    variant = "danger";
  } else if (norm === "NONE") {
    icon = "○";
    label = "None";
    variant = "muted";
  }

  // Tenant / Location / Org Statuses
  else if (norm === "SUSPENDED") {
    icon = "⊘";
    label = "Suspended";
    variant = "warning";
  }

  // Resource Statuses
  else if (norm === "AVAILABLE") {
    icon = "✓";
    label = "Available";
    variant = "success";
  } else if (norm === "OCCUPIED") {
    icon = "⏳";
    label = "Occupied";
    variant = "warning";
  } else if (norm === "OUT_OF_SERVICE") {
    icon = "⚠";
    label = "Out of Service";
    variant = "danger";
  }

  return (
    <span className={`vn-badge vn-badge-${variant}`}>
      <span className="vn-badge-icon" aria-hidden="true">{icon}</span>
      <span className="vn-badge-text">{label}</span>
    </span>
  );
}

