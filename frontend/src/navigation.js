// Single source of truth for navigation: sidebar, overview tiles and the Ctrl+K palette all read from here.
export const NAV_ITEMS = [
  { label: "Overview", icon: "grid", blurb: "Your workspace at a glance",
    roles: ["user", "admin", "staff", "supervisor", "venue_manager", "tenant_admin", "platform_admin"] },
  { label: "Space Hierarchy", icon: "layers", blurb: "Buildings, floors and zones",
    roles: ["admin", "venue_manager", "tenant_admin"] },
  { label: "Departments", icon: "people", blurb: "Teams that run each space",
    roles: ["admin", "venue_manager", "tenant_admin"] },
  { label: "Resources", icon: "box", blurb: "Register and track physical assets",
    roles: ["user", "admin", "staff", "supervisor", "venue_manager", "tenant_admin"] },
  { label: "QR Management", icon: "qr", blurb: "Issue, print, revoke and reissue QR tags",
    roles: ["admin", "venue_manager", "tenant_admin", "supervisor"] },
  { label: "Locations", icon: "pin", blurb: "Sites your organisation operates",
    roles: ["admin", "venue_manager", "tenant_admin"] },
  { label: "Organisations", icon: "building", blurb: "Organisations under your tenant",
    roles: ["admin", "tenant_admin"] },
  { label: "Staff Management", icon: "people", blurb: "Manage staff access and roles",
    roles: ["admin", "venue_manager", "tenant_admin"] },
  { label: "Tenants", icon: "key", blurb: "Platform-wide tenant control",
    roles: ["admin", "platform_admin"] }
];

export function getNavItems(roles = []) {
  const normalized = roles.map((role) => String(role).toLowerCase());
  return NAV_ITEMS.filter((item) => item.roles.some((role) => normalized.includes(role)));
}

// The page a role most likely wants first.
export function suggestedStart(items) {
  const order = ["Space Hierarchy", "Resources", "QR Management"];
  return order.find((label) => items.some((item) => item.label === label)) || null;
}
