import { useState } from "react";
import Sidebar from "./Sidebar";
import LocationSwitcher from "./LocationSwitcher";
import SpaceHierarchyPage from "../features/hierarchy/SpaceHierarchyPage";
import DepartmentsPage from "../features/departments/DepartmentsPage";
import ResourcesPage from "../features/resources/ResourcesPage";
import QrManagementPage from "../features/qr/QrManagementPage";
import LocationsPage from "../features/hierarchy/LocationsPage";
import OrganisationsPage from "../features/hierarchy/OrganisationsPage";
import TenantsPage from "../features/hierarchy/TenantsPage";

export default function Dashboard({ user, onLogout }) {
  const [activePage, setActivePage] = useState("Overview");
  const [activeLocation, setActiveLocation] = useState(null);
  const roles = Array.isArray(user.roles) ? user.roles : [];

  function handleLocationChanged(location) {
    setActiveLocation(location || null);
    // Location-specific data should be re-fetched by the page that owns it.
    // Do not keep displaying data from the previous location.
  }

  return (
    <div className="app-shell">
      <Sidebar roles={roles} activePage={activePage} onNavigate={setActivePage} />
      <main className="main-panel">
        <header className="topbar">
          <div>
            <p className="eyebrow">VENUE NEXUS WORKSPACE</p>
            <h1>{activePage}</h1>
          </div>
          <div className="user-area">
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
              <h2>Hello, {user.name || user.full_name || "there"}!</h2>
              <p className="muted">Here is your workspace at a glance.</p>
            </div>
            <LocationSwitcher onLocationChanged={handleLocationChanged} />
          </div>

          {activePage === "Overview" && (
            <>
              <div className="stat-grid">
                <article className="stat-card">
                  <span className="stat-label">Workspace</span>
                  <strong>Venue Nexus</strong>
                  <span className="stat-note">Management dashboard</span>
                </article>
                <article className="stat-card">
                  <span className="stat-label">Your role</span>
                  <strong>{roles.join(", ") || "Member"}</strong>
                  <span className="stat-note">Access based on your account</span>
                </article>
                <article className="stat-card">
                  <span className="stat-label">Active location</span>
                  <strong>{activeLocation?.name || activeLocation?.location_name || "Select a location"}</strong>
                  <span className="stat-note">Permitted locations only</span>
                </article>
              </div>
              <article className="panel-card">
                <h3>Welcome to Venue Nexus</h3>
                <p className="muted">
                  Use the navigation sidebar to configure spaces, manage operational departments,
                  register physical resources, and issue digital QR identities.
                </p>
              </article>
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
              onNavigateToQr={() => setActivePage("QR Management")}
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
        </section>
      </main>
    </div>
  );
}