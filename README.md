# Venue Nexus – Phase 6 Frontend Starter

This is a React + Vite starter for Phase 6: design foundation, login, dashboard shell, permitted location switcher, and API client.

## Run locally

1. Install Node.js (LTS).
2. Extract the ZIP and open this folder in VS Code.
3. Run `npm install`.
4. Copy `.env.example` to `.env` and set `VITE_API_BASE_URL` to your backend URL.
5. Run `npm run dev`.
6. Open the local URL printed by Vite.

## Backend contract to confirm with your team

The example client currently expects:

- `POST /v1/auth/login` with `{ "email": "...", "password": "..." }`
- `GET /v1/auth/me` returning the current user profile
- `POST /v1/auth/logout`
- `GET /v1/locations` returning either an array of permitted locations or `{ "locations": [...], "active_location_id": "..." }`
- `POST /v1/locations/active` with `{ "location_id": "..." }`

A location should contain `id` and `name` (or `location_name`). The user profile should contain `name` (or `full_name`), `email`, and `roles` such as `["user"]` or `["admin"]`.

**Important:** These paths and response fields are integration placeholders. Match them to the real Phase 2 / backend OpenAPI specification before connecting. The backend must enforce authentication, role permissions, and location permissions; hiding links in the frontend is not security.

## Session and security notes

- The example uses `credentials: "include"` for a server-managed HTTP-only session cookie.
- Configure backend CORS to allow the frontend origin and credentials.
- Configure secure cookie attributes appropriately for your deployment.
- Do not store passwords, access tokens, or refresh tokens in localStorage.
- The active-location API must validate that the signed-in user is permitted to access the requested location.
- Re-fetch location-scoped data after a successful location change. Clear any client query/cache store your team uses at that point.

## Files

- `src/components/Login.jsx` – login form
- `src/components/Dashboard.jsx` – dashboard shell and overview
- `src/components/Sidebar.jsx` – role-filtered navigation
- `src/components/LocationSwitcher.jsx` – permitted location selector
- `src/services/api.js` – backend API client
- `src/styles.css` – design tokens and responsive styling
