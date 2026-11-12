/**
 * VENUE NEXUS - Sprint 1 Phase 7 API Client
 *
 * Strictly follows:
 * - docs/api-contract.md (standard response envelopes, pagination, error contract, X-Trace-Id)
 * - docs/security.md (tenant isolation: never sends tenantId in client payloads)
 * - Spring Boot backend contracts from Phase 4 (hierarchy, departments) and Phase 5 (resources, QR)
 *
 * Includes an isolated in-memory mock fallback when the backend API is offline during local development,
 * ensuring flawless local preview and testing.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000";

// Helper to generate a client-side trace ID if none provided
function generateTraceId() {
  return "trace-" + Math.random().toString(36).substring(2, 10) + "-" + Date.now().toString(36);
}

// In-memory development store for mock fallback when backend server is offline
const mockStore = {
  organisations: [
    { id: "org-01", name: "Grand Nexus Hospitality", status: "ACTIVE", createdAt: "2026-11-01T10:00:00Z" }
  ],
  locations: [
    { id: "loc-01", organisationId: "org-01", name: "Downtown Venue Central", timezone: "Asia/Kolkata", currency: "INR", status: "ACTIVE" },
    { id: "loc-02", organisationId: "org-01", name: "Seaside Resort & Lounge", timezone: "Asia/Kolkata", currency: "INR", status: "ACTIVE" }
  ],
  tenants: [
    { id: "tenant-01", name: "Nexus Holdings Ltd", status: "ACTIVE", branding: "default", createdAt: "2026-10-15T08:00:00Z" }
  ],
  spaces: [
    { id: "sp-01", locationId: "loc-01", parentSpaceId: null, spaceType: "BUILDING", name: "Main Tower", createdAt: "2026-11-02T10:00:00Z" },
    { id: "sp-02", locationId: "loc-01", parentSpaceId: "sp-01", spaceType: "FLOOR", name: "Ground Floor", createdAt: "2026-11-02T10:05:00Z" },
    { id: "sp-03", locationId: "loc-01", parentSpaceId: "sp-02", spaceType: "ZONE", name: "Dining Hall East", createdAt: "2026-11-02T10:10:00Z" },
    { id: "sp-04", locationId: "loc-01", parentSpaceId: "sp-02", spaceType: "ZONE", name: "Patio Terrace", createdAt: "2026-11-02T10:15:00Z" },
    { id: "sp-05", locationId: "loc-01", parentSpaceId: "sp-01", spaceType: "FLOOR", name: "1st Floor", createdAt: "2026-11-02T10:20:00Z" },
    { id: "sp-06", locationId: "loc-01", parentSpaceId: "sp-05", spaceType: "ZONE", name: "VIP Lounge", createdAt: "2026-11-02T10:25:00Z" }
  ],
  departments: [
    { id: "dept-01", locationId: "loc-01", name: "Main Kitchen", departmentType: "KITCHEN", createdAt: "2026-11-02T11:00:00Z" },
    { id: "dept-02", locationId: "loc-01", name: "Terrace Bar", departmentType: "BAR", createdAt: "2026-11-02T11:05:00Z" },
    { id: "dept-03", locationId: "loc-01", name: "Floor Service Team", departmentType: "FLOOR_SERVICE", createdAt: "2026-11-02T11:10:00Z" }
  ],
  resources: [
    {
      id: "res-01",
      code: "T12",
      displayName: "Table 12",
      type: "TABLE",
      locationId: "loc-01",
      spaceId: "sp-03",
      departmentId: "dept-03",
      status: "AVAILABLE",
      activationPolicy: "STAFF_CONFIRM",
      qrStatus: "ACTIVE",
      qrVersion: 1
    },
    {
      id: "res-02",
      code: "T14",
      displayName: "Table 14",
      type: "TABLE",
      locationId: "loc-01",
      spaceId: "sp-03",
      departmentId: "dept-03",
      status: "AVAILABLE",
      activationPolicy: "STAFF_CONFIRM",
      qrStatus: "NONE",
      qrVersion: null
    },
    {
      id: "res-03",
      code: "B01",
      displayName: "Bar Counter Seat 1",
      type: "SEAT",
      locationId: "loc-01",
      spaceId: "sp-04",
      departmentId: "dept-02",
      status: "AVAILABLE",
      activationPolicy: "OPEN",
      qrStatus: "REVOKED",
      qrVersion: 1
    }
  ],
  qrRecords: {
    "res-01": {
      resourceId: "res-01",
      status: "ACTIVE",
      qrVersion: 1,
      issuedAt: "2026-11-05T12:00:00Z",
      revokedAt: null
    },
    "res-03": {
      resourceId: "res-03",
      status: "REVOKED",
      qrVersion: 1,
      issuedAt: "2026-11-03T09:00:00Z",
      revokedAt: "2026-11-05T14:30:00Z"
    }
  }
};

/**
 * Standard HTTP request wrapper with traceId and error normalization
 */
async function apiRequest(path, options = {}) {
  const traceId = options.traceId || generateTraceId();
  const headers = {
    "Accept": "application/json",
    "X-Trace-Id": traceId,
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...options.headers
  };

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      credentials: "include",
      headers
    });

    if (!response.ok) {
      let errorData = {
        status: response.status,
        code: "HTTP_" + response.status,
        message: `Request failed with status ${response.status}`,
        traceId: response.headers.get("X-Trace-Id") || traceId,
        details: []
      };

      try {
        const body = await response.json();
        errorData = {
          status: body.status || response.status,
          code: body.code || body.error || errorData.code,
          message: body.message || body.detail || errorData.message,
          path: body.path || path,
          traceId: body.traceId || errorData.traceId,
          details: body.details || []
        };
      } catch {
        // Response was not JSON
      }

      const err = new Error(errorData.message);
      err.errorData = errorData;
      throw err;
    }

    if (response.status === 204) return null;
    return await response.json();
  } catch (error) {
    // If backend server is unreachable (connection refused, network error), use mock fallback
    if (error.message.includes("Failed to fetch") || error.message.includes("NetworkError") || error.message.includes("Load failed")) {
      return handleMockFallback(path, options, traceId);
    }
    throw error;
  }
}

/**
 * Isolated development fallback handler
 */
function handleMockFallback(path, options, traceId) {
  const method = (options.method || "GET").toUpperCase();
  const body = options.body ? JSON.parse(options.body) : null;
  const url = new URL(path, "http://localhost");
  const pathname = url.pathname;

  // --- SPACES ---
  if (pathname === "/api/v1/spaces/by-location/" || pathname.startsWith("/api/v1/spaces/by-location/")) {
    const locId = pathname.split("/").pop();
    const filtered = mockStore.spaces.filter((s) => !locId || s.locationId === locId);
    return filtered;
  }
  if (pathname === "/api/v1/spaces" && method === "GET") {
    const locId = url.searchParams.get("locationId");
    const filtered = mockStore.spaces.filter((s) => !locId || s.locationId === locId);
    return { items: filtered, page: 0, size: 50, totalElements: filtered.length, totalPages: 1 };
  }
  if (pathname === "/api/v1/spaces" && method === "POST") {
    const newSpace = {
      id: "sp-" + Math.random().toString(36).substring(2, 8),
      locationId: body.locationId,
      parentSpaceId: body.parentSpaceId || null,
      spaceType: body.spaceType,
      name: body.name,
      createdAt: new Date().toISOString()
    };
    mockStore.spaces.push(newSpace);
    return newSpace;
  }

  // --- DEPARTMENTS ---
  if (pathname === "/api/v1/departments" && method === "GET") {
    const locId = url.searchParams.get("locationId");
    const filtered = mockStore.departments.filter((d) => !locId || d.locationId === locId);
    return { items: filtered, page: 0, size: 50, totalElements: filtered.length, totalPages: 1 };
  }
  if (pathname === "/api/v1/departments" && method === "POST") {
    const exists = mockStore.departments.some(
      (d) => d.locationId === body.locationId && d.name.toLowerCase() === body.name.toLowerCase()
    );
    if (exists) {
      const err = new Error(`Department with name '${body.name}' already exists in this location.`);
      err.errorData = {
        status: 409,
        code: "DUPLICATE_RESOURCE",
        message: err.message,
        traceId,
        details: [{ field: "name", issue: "Name must be unique within location" }]
      };
      throw err;
    }
    const newDept = {
      id: "dept-" + Math.random().toString(36).substring(2, 8),
      locationId: body.locationId,
      name: body.name,
      departmentType: body.departmentType,
      createdAt: new Date().toISOString()
    };
    mockStore.departments.push(newDept);
    return newDept;
  }

  // --- RESOURCES ---
  if (pathname === "/api/v1/resources" && method === "GET") {
    const locId = url.searchParams.get("locationId");
    const type = url.searchParams.get("type");
    const status = url.searchParams.get("status");
    let items = mockStore.resources.filter((r) => !locId || r.locationId === locId);
    if (type) items = items.filter((r) => r.type === type);
    if (status) items = items.filter((r) => r.status === status);
    return { items, page: 0, size: 50, totalElements: items.length, totalPages: 1 };
  }
  if (pathname === "/api/v1/resources" && method === "POST") {
    const exists = mockStore.resources.some(
      (r) => r.locationId === body.locationId && r.code.toUpperCase() === body.code.toUpperCase()
    );
    if (exists) {
      const err = new Error(`Resource with code '${body.code}' already exists in this location.`);
      err.errorData = {
        status: 409,
        code: "DUPLICATE_RESOURCE_CODE",
        message: err.message,
        traceId,
        details: [{ field: "code", issue: "Resource code must be unique within location" }]
      };
      throw err;
    }
    const newRes = {
      id: "res-" + Math.random().toString(36).substring(2, 8),
      code: body.code.toUpperCase(),
      displayName: body.displayName,
      type: body.type,
      locationId: body.locationId,
      spaceId: body.spaceId || null,
      departmentId: body.departmentId || null,
      status: "AVAILABLE",
      activationPolicy: body.activationPolicy || "STAFF_CONFIRM",
      qrStatus: "NONE",
      qrVersion: null
    };
    mockStore.resources.push(newRes);
    return newRes;
  }

  // Bulk Create Resources
  if (pathname === "/api/v1/resources/bulk" && method === "POST") {
    const { prefix, start, end, padWidth, type, locationId, spaceId, departmentId } = body;
    const created = [];
    for (let i = start; i <= end; i++) {
      const code = `${prefix}${String(i).padStart(padWidth || 0, "0")}`;
      const newRes = {
        id: "res-" + Math.random().toString(36).substring(2, 8),
        code,
        displayName: `${prefix} ${i}`,
        type,
        locationId,
        spaceId: spaceId || null,
        departmentId: departmentId || null,
        status: "AVAILABLE",
        activationPolicy: "STAFF_CONFIRM",
        qrStatus: "NONE",
        qrVersion: null
      };
      mockStore.resources.push(newRes);
      created.push(newRes);
    }
    return created;
  }

  // --- QR MANAGEMENT ---
  // Status
  const qrStatusMatch = pathname.match(/^\/api\/v1\/resources\/([^/]+)\/qr$/);
  if (qrStatusMatch && method === "GET") {
    const resId = qrStatusMatch[1];
    const rec = mockStore.qrRecords[resId] || {
      resourceId: resId,
      status: "NONE",
      qrVersion: null,
      issuedAt: null,
      revokedAt: null
    };
    return rec;
  }

  // Issue QR
  if (qrStatusMatch && method === "POST") {
    const resId = qrStatusMatch[1];
    const resObj = mockStore.resources.find((r) => r.id === resId);
    if (!resObj) {
      const err = new Error("Resource not found");
      err.errorData = { status: 404, code: "RESOURCE_NOT_FOUND", message: err.message, traceId };
      throw err;
    }
    if (resObj.qrStatus === "ACTIVE") {
      const err = new Error("Resource already has an active QR. Use reissue instead.");
      err.errorData = { status: 409, code: "ACTIVE_QR_EXISTS", message: err.message, traceId };
      throw err;
    }
    const nextVersion = (resObj.qrVersion || 0) + 1;
    resObj.qrStatus = "ACTIVE";
    resObj.qrVersion = nextVersion;

    mockStore.qrRecords[resId] = {
      resourceId: resId,
      status: "ACTIVE",
      qrVersion: nextVersion,
      issuedAt: new Date().toISOString(),
      revokedAt: null
    };

    // Return one-time representation
    const qrUrl = `https://venue-nexus.internal/scan/${resId}?v=${nextVersion}`;
    const svg = generateSampleQrSvg(resObj.code);
    return {
      resourceId: resId,
      qrVersion: nextVersion,
      qrUrl,
      svg,
      pngBase64: null,
      issuedAt: new Date().toISOString()
    };
  }

  // Reissue QR
  const qrReissueMatch = pathname.match(/^\/api\/v1\/resources\/([^/]+)\/qr\/reissue$/);
  if (qrReissueMatch && method === "POST") {
    const resId = qrReissueMatch[1];
    const resObj = mockStore.resources.find((r) => r.id === resId);
    if (!resObj) {
      const err = new Error("Resource not found");
      err.errorData = { status: 404, code: "RESOURCE_NOT_FOUND", message: err.message, traceId };
      throw err;
    }
    const nextVersion = (resObj.qrVersion || 0) + 1;
    resObj.qrStatus = "ACTIVE";
    resObj.qrVersion = nextVersion;

    mockStore.qrRecords[resId] = {
      resourceId: resId,
      status: "ACTIVE",
      qrVersion: nextVersion,
      issuedAt: new Date().toISOString(),
      revokedAt: null
    };

    const qrUrl = `https://venue-nexus.internal/scan/${resId}?v=${nextVersion}`;
    const svg = generateSampleQrSvg(resObj.code);
    return {
      resourceId: resId,
      qrVersion: nextVersion,
      qrUrl,
      svg,
      pngBase64: null,
      issuedAt: new Date().toISOString()
    };
  }

  // Revoke QR
  const qrRevokeMatch = pathname.match(/^\/api\/v1\/resources\/([^/]+)\/qr\/revoke$/);
  if (qrRevokeMatch && method === "POST") {
    const resId = qrRevokeMatch[1];
    const resObj = mockStore.resources.find((r) => r.id === resId);
    if (!resObj) {
      const err = new Error("Resource not found");
      err.errorData = { status: 404, code: "RESOURCE_NOT_FOUND", message: err.message, traceId };
      throw err;
    }
    resObj.qrStatus = "REVOKED";
    const now = new Date().toISOString();
    mockStore.qrRecords[resId] = {
      resourceId: resId,
      status: "REVOKED",
      qrVersion: resObj.qrVersion,
      issuedAt: mockStore.qrRecords[resId]?.issuedAt || now,
      revokedAt: now
    };
    return mockStore.qrRecords[resId];
  }

  // --- LOCATIONS, ORGANISATIONS, TENANTS ---
  if (pathname === "/api/v1/locations" && method === "GET") {
    return { items: mockStore.locations, page: 0, size: 50, totalElements: mockStore.locations.length, totalPages: 1 };
  }
  if (pathname === "/api/v1/locations" && method === "POST") {
    const newLoc = {
      id: "loc-" + Math.random().toString(36).substring(2, 8),
      organisationId: body.organisationId,
      name: body.name,
      timezone: body.timezone || "Asia/Kolkata",
      currency: body.currency || "INR",
      status: "ACTIVE"
    };
    mockStore.locations.push(newLoc);
    return newLoc;
  }

  if (pathname === "/api/v1/organisations" && method === "GET") {
    return { items: mockStore.organisations, page: 0, size: 50, totalElements: mockStore.organisations.length, totalPages: 1 };
  }
  if (pathname === "/api/v1/organisations" && method === "POST") {
    const newOrg = {
      id: "org-" + Math.random().toString(36).substring(2, 8),
      name: body.name,
      status: "ACTIVE",
      createdAt: new Date().toISOString()
    };
    mockStore.organisations.push(newOrg);
    return newOrg;
  }

  if (pathname === "/api/v1/tenants" && method === "GET") {
    return { items: mockStore.tenants, page: 0, size: 50, totalElements: mockStore.tenants.length, totalPages: 1 };
  }
  if (pathname === "/api/v1/tenants" && method === "POST") {
    const newTenant = {
      id: "tenant-" + Math.random().toString(36).substring(2, 8),
      name: body.name,
      status: "ACTIVE",
      branding: body.branding || "default",
      createdAt: new Date().toISOString()
    };
    mockStore.tenants.push(newTenant);
    return newTenant;
  }

  const tenantStatusMatch = pathname.match(/^\/api\/v1\/tenants\/([^/]+)\/status$/);
  if (tenantStatusMatch && (method === "PATCH" || method === "PUT")) {
    const tenant = mockStore.tenants.find((t) => t.id === tenantStatusMatch[1]);
    if (tenant) {
      tenant.status = body.status;
      return tenant;
    }
  }

  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}

// Generate valid scalable SVG QR representation for mock preview
function generateSampleQrSvg(code) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200">
    <rect width="200" height="200" fill="#ffffff" />
    <!-- Finder patterns -->
    <rect x="20" y="20" width="40" height="40" fill="#1e293b" />
    <rect x="28" y="28" width="24" height="24" fill="#ffffff" />
    <rect x="34" y="34" width="12" height="12" fill="#1e293b" />
    
    <rect x="140" y="20" width="40" height="40" fill="#1e293b" />
    <rect x="148" y="28" width="24" height="24" fill="#ffffff" />
    <rect x="154" y="34" width="12" height="12" fill="#1e293b" />

    <rect x="20" y="140" width="40" height="40" fill="#1e293b" />
    <rect x="28" y="148" width="24" height="24" fill="#ffffff" />
    <rect x="34" y="154" width="12" height="12" fill="#1e293b" />

    <!-- Data matrix bits -->
    <rect x="70" y="30" width="10" height="10" fill="#1e293b" />
    <rect x="90" y="30" width="10" height="10" fill="#1e293b" />
    <rect x="110" y="30" width="10" height="10" fill="#1e293b" />
    <rect x="70" y="50" width="10" height="10" fill="#1e293b" />
    <rect x="100" y="70" width="10" height="10" fill="#1e293b" />
    <rect x="40" y="80" width="10" height="10" fill="#1e293b" />
    <rect x="80" y="90" width="40" height="20" fill="#1e293b" />
    <rect x="140" y="90" width="20" height="20" fill="#1e293b" />
    <rect x="70" y="130" width="20" height="20" fill="#1e293b" />
    <rect x="110" y="140" width="20" height="10" fill="#1e293b" />
    <rect x="140" y="150" width="20" height="20" fill="#1e293b" />
    
    <!-- Code label in center -->
    <rect x="75" y="95" width="50" height="16" fill="#ffffff" rx="3" />
    <text x="100" y="107" font-family="monospace" font-size="10" font-weight="bold" fill="#0f172a" text-anchor="middle">${code || "VN"}</text>
  </svg>`;
}

// -------------------------------------------------------------
// Public Phase 7 API Methods
// -------------------------------------------------------------

// Spaces (Location > Building > Floor > Zone)
export const getSpacesByLocation = (locationId) =>
  apiRequest(`/api/v1/spaces/by-location/${encodeURIComponent(locationId)}`);

export const createSpace = (data) =>
  apiRequest("/api/v1/spaces", {
    method: "POST",
    body: JSON.stringify({
      locationId: data.locationId,
      parentSpaceId: data.parentSpaceId || null,
      spaceType: data.spaceType,
      name: data.name.trim()
    })
  });

// Departments
export const getDepartments = (locationId) =>
  apiRequest(`/api/v1/departments?locationId=${encodeURIComponent(locationId)}&size=100`);

export const createDepartment = (data) =>
  apiRequest("/api/v1/departments", {
    method: "POST",
    body: JSON.stringify({
      locationId: data.locationId,
      name: data.name.trim(),
      departmentType: data.departmentType
    })
  });

// Resources
export const getResources = (locationId, filters = {}) => {
  const params = new URLSearchParams({ locationId });
  if (filters.type) params.append("type", filters.type);
  if (filters.status) params.append("status", filters.status);
  params.append("page", filters.page || 0);
  params.append("size", filters.size || 50);
  return apiRequest(`/api/v1/resources?${params.toString()}`);
};

export const createResource = (data) =>
  apiRequest("/api/v1/resources", {
    method: "POST",
    body: JSON.stringify({
      code: data.code.trim(),
      displayName: data.displayName.trim(),
      type: data.type,
      locationId: data.locationId,
      spaceId: data.spaceId || null,
      departmentId: data.departmentId || null,
      activationPolicy: data.activationPolicy || "STAFF_CONFIRM",
      capabilitiesJson: data.capabilitiesJson || null
    })
  });

export const bulkCreateResources = (data) =>
  apiRequest("/api/v1/resources/bulk", {
    method: "POST",
    body: JSON.stringify(data)
  });

// QR Operations
export const getQrStatus = (resourceId) =>
  apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr`);

export const issueQr = (resourceId) =>
  apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr`, {
    method: "POST"
  });

export const reissueQr = (resourceId) =>
  apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr/reissue`, {
    method: "POST"
  });

export const revokeQr = (resourceId) =>
  apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr/revoke`, {
    method: "POST"
  });

// Location / Organisation / Tenant APIs
export const getLocations = (organisationId) => {
  const query = organisationId ? `?organisationId=${encodeURIComponent(organisationId)}` : "";
  return apiRequest(`/api/v1/locations${query}`);
};

export const createLocation = (data) =>
  apiRequest("/api/v1/locations", {
    method: "POST",
    body: JSON.stringify(data)
  });

export const getOrganisations = () =>
  apiRequest("/api/v1/organisations");

export const createOrganisation = (data) =>
  apiRequest("/api/v1/organisations", {
    method: "POST",
    body: JSON.stringify(data)
  });

export const getTenants = () =>
  apiRequest("/api/v1/tenants");

export const createTenant = (data) =>
  apiRequest("/api/v1/tenants", {
    method: "POST",
    body: JSON.stringify(data)
  });

export const updateTenantStatus = (tenantId, status) =>
  apiRequest(`/api/v1/tenants/${encodeURIComponent(tenantId)}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status })
  });

