const NAV_ITEMS = [
  {
    label: "Overview",
    roles: ["user", "admin", "staff", "supervisor", "venue_manager", "tenant_admin", "platform_admin"]
  },
  {
    label: "Space Hierarchy",
    roles: ["admin", "venue_manager", "tenant_admin"]
  },
  {
    label: "Departments",
    roles: ["admin", "venue_manager", "tenant_admin"]
  },
  {
    label: "Resources",
    roles: ["user", "admin", "staff", "supervisor", "venue_manager", "tenant_admin"]
  },
  {
    label: "QR Management",
    roles: ["admin", "venue_manager", "tenant_admin", "supervisor"]
  },
  {
    label: "Locations",
    roles: ["admin", "venue_manager", "tenant_admin"]
  },
  {
    label: "Organisations",
    roles: ["admin", "tenant_admin"]
  },
  {
    label: "Tenants",
    roles: ["admin", "platform_admin"]
  }
];

export default function Sidebar({ roles = [], activePage, onNavigate }) {
  const normalizedRoles = roles.map((role) => String(role).toLowerCase());

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="brand-mark small">VN</span>
        <span>Venue Nexus</span>
      </div>
      <p className="sidebar-caption">WORKSPACE</p>
      <nav aria-label="Main navigation">
        {NAV_ITEMS
          .filter((item) => item.roles.some((role) => normalizedRoles.includes(role)))
          .map((item) => (
            <button key={item.label}
              className={`nav-item ${activePage === item.label ? "selected" : ""}`}
              onClick={() => onNavigate(item.label)}>
              <span className="nav-dot" />
              {item.label}
            </button>
          ))}
      </nav>
      <div className="sidebar-bottom">Venue Nexus · Sprint 1</div>
    </aside>
  );
}