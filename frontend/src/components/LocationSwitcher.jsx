import { useEffect, useState } from "react";
import { getPermittedLocations, setActiveLocation } from "../services/api";

export default function LocationSwitcher({ onLocationChanged }) {
  const [locations, setLocations] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getPermittedLocations()
      .then((result) => {
        const items = Array.isArray(result) ? result : (result.items || result.locations || []);
        if (!active) return;
        setLocations(items);
        const initial = result.active_location_id || items[0]?.id || "";
        setSelectedId(String(initial));
        onLocationChanged?.(items.find((location) => String(location.id) === String(initial)) || null);
      })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function handleChange(event) {
    const nextId = event.target.value;
    const previousId = selectedId;
    setError("");
    setSelectedId(nextId);
    try {
      const result = await setActiveLocation(nextId);
      // Parent clears any location-scoped UI/cache state after server confirmation.
      onLocationChanged?.(result.active_location || locations.find((x) => String(x.id) === nextId));
    } catch (err) {
      setSelectedId(previousId);
      setError(err.message || "Could not change location.");
    }
  }

  return (
    <div className="location-control">
      <label htmlFor="location">Active location</label>
      <select id="location" value={selectedId} onChange={handleChange}
        disabled={loading || locations.length === 0}>
        {loading && <option value="">Loading locations…</option>}
        {!loading && locations.length === 0 && <option value="">No permitted locations</option>}
        {locations.map((location) => (
          <option key={location.id} value={location.id}>
            {location.name || location.location_name}
          </option>
        ))}
      </select>
      {error && <span className="inline-error">{error}</span>}
    </div>
  );
}