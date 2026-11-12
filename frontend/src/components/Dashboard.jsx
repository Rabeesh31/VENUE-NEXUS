import { useEffect, useState } from "react";
import Sidebar from "./Sidebar";
import Icon from "./Icon";
import CommandPalette from "./CommandPalette";
import { getNavItems, suggestedStart } from "../navigation";
import LocationSwitcher from "./LocationSwitcher";
import SpaceHierarchyPage from "../features/hierarchy/SpaceHierarchyPage";
import DepartmentsPage from "../features/departments/DepartmentsPage";
import ResourcesPage from "../features/resources/ResourcesPage";
import QrManagementPage from "../features/qr/QrManagementPage";
import LocationsPage from "../features/hierarchy/LocationsPage";
import OrganisationsPage from "../features/hierarchy/OrganisationsPage";
import TenantsPage from "../features/hierarchy/TenantsPage";
import StaffManagementPage from "../features/staff/StaffManagementPage";

export default function Dashboard({ user, onLogout }) {
  const [activePage, setActivePage] = useState("Overview");
  const [activeLocation, setActiveLocation] = useState(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [history, setHistory] = useState([]);
  const roles = Array.isArray(user.roles) ? user.roles : [];
  const navItems = getNavItems(roles);
  const startLabel = suggestedStart(navItems);
  const locationName = activeLocation?.name || activeLocation?.location_name || "";
  const displayName = user.name || user.full_name || "there";

  useEffect(() => {
    function onKey(event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPaletteOpen((open) => !open);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  function go(label) {
    setPaletteOpen(false);
    if (label === activePage) return;
    setHistory((stack) => [...stack, activePage]);
    setActivePage(label);
  }

  function goBack() {
    setHistory((stack) => {
      if (stack.length === 0) return stack;
      setActivePage(stack[stack.length - 1]);
      return stack.slice(0, -1);
    });
  }

  function handleLocationChanged(location) {
    setActiveLocation(location || null);
    // Location-specific data should be re-fetched by the page that owns it.
    // Do not keep displaying data from the previous location.
  }

  return (
    <div className="app-shell">
      <Sidebar roles={roles} activePage={activePage} onNavigate={go} />
      <main className="main-panel">
        <header className="topbar">
          <div className="topbar-title">
            {history.length > 0 && (
              <button className="back-button" onClick={goBack}>
                <span aria-hidden="true">&larr;</span> Back to {history[history.length - 1]}
              </button>
            )}
            <p className="crumb">Venue Nexus{locationName ? ` / ${locationName}` : ""}</p>
            <h1>{activePage}</h1>
          </div>
          <div className="user-area">
            <button className="palette-trigger" onClick={() => setPaletteOpen(true)}>
              <Icon name="search" size={16} />
              <span>Jump to</span>
              <kbd>Ctrl K</kbd>
            </button>
            <div className="user-details">
              <strong>{user.name || user.full_name || user.email || "User"}</strong>
              <span>{roles.join(", ") || "Member"}</span>
            </div>
            <button className="secondary-button" onClick={onLogout}>Log out</button>
          </div>
        </header>

        <section className="content-area">
          <div className="welcome-row">
            <div>
              <h2>Hello, {displayName}</h2>
              <p className="muted">
                {locationName ? `Working in ${locationName}.` : "Choose a location to scope every page."}
              </p>
            </div>
            <LocationSwitcher onLocationChanged={handleLocationChanged} />
          </div>

          {activePage === "Overview" && (
            <>
              {!locationName && (
                <p className="notice" role="status">
                  No active location yet. Pick one above and every page will show only that site&apos;s data.
                </p>
              )}
              <div className="stat-grid">
                <article className="stat-card">
                  <span className="stat-label">Your role</span>
                  <strong>{roles.join(", ") || "Member"}</strong>
                  <span className="stat-note">Set by your organisation</span>
                </article>
                <article className="stat-card">
                  <span className="stat-label">Active location</span>
                  <strong>{locationName || "None selected"}</strong>
                  <span className="stat-note">Only permitted locations are listed</span>
                </article>
                <article className="stat-card">
                  <span className="stat-label">Pages you can open</span>
                  <strong>{navItems.length - 1}</strong>
                  <span className="stat-note">Based on your role</span>
                </article>
              </div>

              <h3 className="section-title">Go straight to</h3>
              <div className="action-grid">
                {navItems.filter((item) => item.label !== "Overview").map((item) => (
                  <button key={item.label}
                    className={`action-tile ${item.label === startLabel ? "suggested" : ""}`}
                    onClick={() => go(item.label)}>
                    <span className="action-icon"><Icon name={item.icon} size={22} /></span>
                    <span className="action-text">
                      <strong>{item.label}</strong>
                      <small>{item.blurb}</small>
                    </span>
                    {item.label === startLabel && <span className="action-flag">Start here</span>}
                    <Icon name="arrow" size={18} />
                  </button>
                ))}
              </div>
            </>
          )}

          {activePage === "Space Hierarchy" && (
            <SpaceHierarchyPage activeLocation={activeLocation} roles={roles} />
          )}

          {activePage === "Departments" && (
            <DepartmentsPage activeLocation={activeLocation} roles={roles} />
          )}

          {activePage === "Resources" && (
            <ResourcesPage
              activeLocation={activeLocation}
              roles={roles}
              onNavigateToQr={() => go("QR Management")}
            />
          )}

          {activePage === "QR Management" && (
            <QrManagementPage activeLocation={activeLocation} roles={roles} />
          )}

          {activePage === "Locations" && (
            <LocationsPage activeLocation={activeLocation} roles={roles} />
          )}

          {activePage === "Organisations" && (
            <OrganisationsPage activeLocation={activeLocation} roles={roles} />
          )}

          {activePage === "Tenants" && (
            <TenantsPage user={user} roles={roles} />
          )}

          {activePage === "Staff Management" && (
            <StaffManagementPage activeLocation={activeLocation} />
          )}
        </section>
      </main>
      {paletteOpen && (
        <CommandPalette items={navItems} onSelect={go} onClose={() => setPaletteOpen(false)} />
      )}
    </div>
  );
}