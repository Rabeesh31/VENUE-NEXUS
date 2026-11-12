import { useState } from "react";
import { login } from "../services/api";

export default function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const result = await login(email.trim(), password);
      // The backend should return the authenticated user/profile.
      onLogin(result.user || result);
    } catch (err) {
      setError(err.message || "Unable to sign in. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="brand-mark">VN</div>
        <p className="eyebrow">VENUE MANAGEMENT PLATFORM</p>
        <h1>Welcome back</h1>
        <p className="muted">Sign in to continue to Venue Nexus.</p>

        <label htmlFor="email">Email address</label>
        <input id="email" type="email" autoComplete="username"
          placeholder="name@example.com" value={email}
          onChange={(e) => setEmail(e.target.value)} required />

        <label htmlFor="password">Password</label>
        <input id="password" type="password" autoComplete="current-password"
          placeholder="Enter your password" value={password}
          onChange={(e) => setPassword(e.target.value)} required />

        {error && <p className="error-message" role="alert">{error}</p>}
        <button className="primary-button full-width" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
        <p className="login-footnote">Access is managed by your organisation.</p>
      </form>
    </main>
  );
}