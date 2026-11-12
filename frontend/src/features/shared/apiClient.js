const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const ACCESS_TOKEN_KEY = "venue_nexus_access_token";
const REFRESH_TOKEN_KEY = "venue_nexus_refresh_token";
const USER_KEY = "venue_nexus_user";

function generateTraceId() {
  return `trace-${crypto.randomUUID()}`;
}

function getStoredUser() {
  const value = localStorage.getItem(USER_KEY);
  return value ? JSON.parse(value) : null;
}

export function getAuthenticatedUser() {
  return getStoredUser();
}

export function clearAuthentication() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function storeAuthentication(response) {
  const user = response.user
    ? {
        ...response.user,
        name: response.user.name || response.user.displayName,
        roles: response.user.roles || (response.user.role ? [response.user.role] : [])
      }
    : response.user;
  localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return { ...response, user };
}

function parseError(response, path, traceId) {
  return response.json().catch(() => ({})).then((body) => {
    const errorData = {
      status: response.status,
      code: body.code || body.error || `HTTP_${response.status}`,
      message: body.message || body.detail || `Request failed with status ${response.status}`,
      path: body.path || path,
      traceId: body.traceId || response.headers.get("X-Trace-Id") || traceId,
      details: body.details || []
    };
    const error = new Error(errorData.message);
    error.errorData = errorData;
    throw error;
  });
}

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return false;

  const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken })
  });
  if (!response.ok) {
    clearAuthentication();
    return false;
  }
  storeAuthentication(await response.json());
  return true;
}

export async function apiRequest(path, options = {}, canRefresh = true) {
  const traceId = options.traceId || generateTraceId();
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  const headers = {
    Accept: "application/json",
    "X-Trace-Id": traceId,
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers
  };

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  } catch {
    const error = new Error("Unable to reach the VENUE NEXUS backend.");
    error.errorData = { status: 0, code: "NETWORK_ERROR", message: error.message, path, traceId, details: [] };
    throw error;
  }

  if (response.status === 401 && canRefresh && !path.includes("/auth/")) {
    if (await refreshAccessToken()) return apiRequest(path, options, false);
    clearAuthentication();
  }
  if (!response.ok) return parseError(response, path, traceId);
  if (response.status === 204) return null;
  return response.json();
}

export async function login(email, password) {
  return storeAuthentication(await apiRequest("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password })
  }));
}

export async function logout() {
  try {
    await apiRequest("/api/v1/auth/logout", {
      method: "POST",
      body: JSON.stringify({ refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) })
    }, false);
  } finally {
    clearAuthentication();
  }
}

export const getCurrentUser = async () => {
  const user = getStoredUser();
  if (!user || !localStorage.getItem(ACCESS_TOKEN_KEY)) {
    throw new Error("Not authenticated.");
  }
  return user;
};

export const getPermittedLocations = () => apiRequest("/api/v1/locations?size=100");

export const getStaffUsers = (params = {}) => {
  const query = new URLSearchParams({ page: "0", size: "100", ...params });
  [...query.keys()].forEach((key) => {
    if (!query.get(key)) query.delete(key);
  });
  return apiRequest(`/api/v1/users?${query.toString()}`);
};

export const createStaffUser = (data) => apiRequest("/api/v1/users", {
  method: "POST",
  body: JSON.stringify(data)
});

export const updateStaffUser = (id, data) => apiRequest(`/api/v1/users/${encodeURIComponent(id)}`, {
  method: "PUT",
  body: JSON.stringify(data)
});

export const deactivateStaffUser = (id) => apiRequest(`/api/v1/users/${encodeURIComponent(id)}/deactivate`, {
  method: "POST"
});

export const reactivateStaffUser = (id) => apiRequest(`/api/v1/users/${encodeURIComponent(id)}/reactivate`, {
  method: "POST"
});

export const setActiveLocation = async (locationId) => {
  const user = getStoredUser();
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify({ ...user, locationId }));
  }
  return { active_location: { id: locationId } };
};

export const getSpacesByLocation = (locationId) =>
  apiRequest(`/api/v1/spaces/by-location/${encodeURIComponent(locationId)}`);

export const createSpace = (data) => apiRequest("/api/v1/spaces", {
  method: "POST",
  body: JSON.stringify({ ...data, name: data.name.trim(), parentSpaceId: data.parentSpaceId || null })
});

export const getDepartments = (locationId) =>
  apiRequest(`/api/v1/departments?locationId=${encodeURIComponent(locationId)}&size=100`);

export const createDepartment = (data) => apiRequest("/api/v1/departments", {
  method: "POST",
  body: JSON.stringify({ ...data, name: data.name.trim() })
});

export const getResources = (locationId, filters = {}) => {
  const params = new URLSearchParams({ locationId, page: filters.page || 0, size: filters.size || 50 });
  if (filters.type) params.set("type", filters.type);
  if (filters.status) params.set("status", filters.status);
  return apiRequest(`/api/v1/resources?${params}`);
};

export const createResource = (data) => apiRequest("/api/v1/resources", {
  method: "POST",
  body: JSON.stringify({ ...data, code: data.code.trim(), displayName: data.displayName.trim(), spaceId: data.spaceId || null, departmentId: data.departmentId || null })
});

export const bulkCreateResources = (data) => apiRequest("/api/v1/resources/bulk", {
  method: "POST",
  body: JSON.stringify(data)
});

export const getQrStatus = (resourceId) => apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr`);
export const issueQr = (resourceId) => apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr`, { method: "POST" });
export const reissueQr = (resourceId) => apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr/reissue`, { method: "POST" });
export const revokeQr = (resourceId) => apiRequest(`/api/v1/resources/${encodeURIComponent(resourceId)}/qr/revoke`, { method: "POST" });

export const getLocations = (organisationId) =>
  apiRequest(`/api/v1/locations${organisationId ? `?organisationId=${encodeURIComponent(organisationId)}` : "?size=100"}`);
export const createLocation = (data) => apiRequest("/api/v1/locations", { method: "POST", body: JSON.stringify(data) });
export const getOrganisations = () => apiRequest("/api/v1/organisations?size=100");
export const createOrganisation = (data) => apiRequest("/api/v1/organisations", { method: "POST", body: JSON.stringify(data) });
export const getTenants = () => apiRequest("/api/v1/tenants?size=100");
export const createTenant = (data) => apiRequest("/api/v1/tenants", { method: "POST", body: JSON.stringify(data) });

export const updateTenantStatus = (tenantId, status) => apiRequest(
  `/api/v1/tenants/${encodeURIComponent(tenantId)}/${status === "ACTIVE" ? "reactivate" : "suspend"}`,
  { method: "POST" }
);
