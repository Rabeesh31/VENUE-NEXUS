import { useEffect, useState } from "react";
import Login from "./components/Login";
import Dashboard from "./components/Dashboard";
import { getCurrentUser, logout } from "./services/api";

export default function App() {
  const [user, setUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);

  // Restore the server-managed session after a page refresh.
  useEffect(() => {
    let active = true;
    getCurrentUser()
      .then((currentUser) => {
        if (active) setUser(currentUser);
      })
      .catch(() => {
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setCheckingSession(false);
      });

    return () => { active = false; };
  }, []);

  async function handleLogout() {
    try {
      await logout();
    } finally {
      // Remove user-specific UI state even if the network request fails.
      setUser(null);
    }
  }

  if (checkingSession) {
    return <div className="loading-screen">Checking your session…</div>;
  }

  return user
    ? <Dashboard user={user} onLogout={handleLogout} />
    : <Login onLogin={setUser} />;
}