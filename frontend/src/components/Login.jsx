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
      <section className="login-brand" aria-hidden="true">
        <div className="brand-mark">VN</div>
        <h2>Every space tagged. Every asset accounted for.</h2>
        <ul className="login-signs">
          <li>Space hierarchy</li>
          <li>Physical resources</li>
          <li>QR identities</li>
        </ul>
      </section>
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Sign in</h1>
        <p className="muted">Use the account your organisation gave you.</p>

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
      </form>
    </main>
  );
}