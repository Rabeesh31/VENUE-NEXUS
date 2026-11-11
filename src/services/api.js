const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    credentials: "include", // Uses a server-managed HTTP-only session cookie.
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers
    }
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.detail || body.message || message;
    } catch {
      // Keep the HTTP status message if the response is not JSON.
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response.json();
}

// Adjust these paths and payload fields to match your team's OpenAPI document.
export const login = (email, password) =>
  request("/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password })
  });

export const getCurrentUser = () => request("/v1/auth/me");

export const getPermittedLocations = () => request("/v1/locations");

export const setActiveLocation = (locationId) =>
  request("/v1/locations/active", {
    method: "POST",
    body: JSON.stringify({ location_id: locationId })
  });

export const logout = () =>
  request("/v1/auth/logout", { method: "POST" });