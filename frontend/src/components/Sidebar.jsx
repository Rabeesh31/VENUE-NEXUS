import { getNavItems } from "../navigation";
import Icon from "./Icon";

export default function Sidebar({ roles = [], activePage, onNavigate }) {
  const items = getNavItems(roles);

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="brand-mark small">VN</span>
        <span className="brand-word">Venue<br />Nexus</span>
      </div>
      <nav aria-label="Main navigation">
        {items.map((item) => (
          <button key={item.label}
            className={`nav-item ${activePage === item.label ? "selected" : ""}`}
            aria-current={activePage === item.label ? "page" : undefined}
            title={item.label}
            onClick={() => onNavigate(item.label)}>
            <Icon name={item.icon} />
            <span className="nav-label">{item.label}</span>
          </button>
        ))}
      </nav>
      <div className="sidebar-bottom">Press <kbd>Ctrl</kbd> <kbd>K</kbd> to jump anywhere</div>
    </aside>
  );
}
