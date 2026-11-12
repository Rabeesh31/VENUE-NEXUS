import { useEffect, useState } from "react";
import { getLocations, createLocation, getOrganisations } from "../shared/apiClient";
import Modal from "../shared/components/Modal";
import Alert from "../shared/components/Alert";
import StatusBadge from "../shared/components/StatusBadge";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";

export default function LocationsPage() {
  const [locations, setLocations] = useState([]);
  const [organisations, setOrganisations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");

  // Create Modal
  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [orgId, setOrgId] = useState("");
  const [timezone, setTimezone] = useState("Asia/Kolkata");
  const [currency, setCurrency] = useState("INR");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    Promise.all([getLocations(), getOrganisations()])
      .then(([locData, orgData]) => {
        if (!active) return;
        const locItems = Array.isArray(locData) ? locData : (locData.items || []);
        const orgItems = Array.isArray(orgData) ? orgData : (orgData.items || []);
        setLocations(locItems);
        setOrganisations(orgItems);
        if (orgItems.length > 0) setOrgId(orgItems[0].id);
      })
      .catch((err) => {
        if (!active) return;
        setError({
          message: err.message || "Failed to load locations.",
          traceId: err.errorData?.traceId
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    if (!name.trim()) {
      setFormError("Location name is required.");
      return;
    }
    if (!orgId) {
      setFormError("Please select an organisation for this location.");
      return;
    }

    setSubmitting(true);
    setFormError("");

    try {
      const created = await createLocation({
        organisationId: orgId,
        name: name.trim(),
        timezone,
        currency
      });

      setLocations((prev) => [...prev, created]);
      setSuccessMsg(`Location "${created.name}" created successfully.`);
      setName("");
      setModalOpen(false);
    } catch (err) {
      setFormError(err.message || "Could not create location.");
    } finally {
      setSubmitting(false);
    }
  }

  function getOrgName(oId) {
    const org = organisations.find((o) => o.id === oId);
    return org?.name || "Organisation " + oId;
  }

  return (
    <div className="vn-feature-container">
      <div className="vn-feature-header">
        <div>
          <h2>Locations</h2>
          <p className="muted">Manage venue operating branches, time zones, and base currencies.</p>
        </div>
        <button
          type="button"
          className="primary-button"
          onClick={() => {
            setFormError("");
            setModalOpen(true);
          }}
        >
          + Add Location
        </button>
      </div>

      {successMsg && (
        <Alert type="success" message={successMsg} onDismiss={() => setSuccessMsg("")} />
      )}
      {error && (
        <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />
      )}

      {loading ? (
        <LoadingSkeleton message="Loading locations…" />
      ) : locations.length === 0 ? (
        <EmptyState
          title="No Locations Yet"
          message="No venue locations configured. Add your first location to begin setting up spaces."
          actionLabel="+ Add Location"
          onAction={() => setModalOpen(true)}
          icon="📍"
        />
      ) : (
        <div className="vn-table-wrapper">
          <table className="vn-table">
            <thead>
              <tr>
                <th scope="col">Location Name</th>
                <th scope="col">Organisation</th>
                <th scope="col">Time Zone</th>
                <th scope="col">Currency</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {locations.map((loc) => (
                <tr key={loc.id}>
                  <td><strong>{loc.name || loc.location_name}</strong></td>
                  <td>{getOrgName(loc.organisationId)}</td>
                  <td><code>{loc.timezone || loc.time_zone || "UTC"}</code></td>
                  <td><span className="vn-pill">{loc.currency || "USD"}</span></td>
                  <td><StatusBadge status={loc.status || "ACTIVE"} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Location Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => !submitting && setModalOpen(false)}
        title="Add Location"
      >
        <form onSubmit={handleCreate} className="vn-form">
          {formError && <Alert type="error" message={formError} />}

          <div className="vn-form-group">
            <label htmlFor="locOrg">
              Organisation <span className="vn-required">*</span>
            </label>
            <select
              id="locOrg"
              value={orgId}
              onChange={(e) => setOrgId(e.target.value)}
              required
            >
              {organisations.map((org) => (
                <option key={org.id} value={org.id}>
                  {org.name}
                </option>
              ))}
            </select>
          </div>

          <div className="vn-form-group">
            <label htmlFor="locName">
              Location Name <span className="vn-required">*</span>
            </label>
            <input
              id="locName"
              type="text"
              placeholder="e.g. Downtown Central, Airport Lounge"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="locTimezone">Time Zone</label>
              <select
                id="locTimezone"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
              >
                <option value="Asia/Kolkata">Asia/Kolkata (IST)</option>
                <option value="UTC">UTC</option>
                <option value="America/New_York">America/New_York (EST)</option>
                <option value="Europe/London">Europe/London (GMT)</option>
                <option value="Asia/Dubai">Asia/Dubai (GST)</option>
                <option value="Asia/Singapore">Asia/Singapore (SGT)</option>
              </select>
            </div>

            <div className="vn-form-group">
              <label htmlFor="locCurrency">Currency</label>
              <select
                id="locCurrency"
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
              >
                <option value="INR">INR (₹)</option>
                <option value="USD">USD ($)</option>
                <option value="EUR">EUR (€)</option>
                <option value="GBP">GBP (£)</option>
                <option value="AED">AED</option>
                <option value="SGD">SGD</option>
              </select>
            </div>
          </div>

          <div className="vn-form-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={() => setModalOpen(false)}
              disabled={submitting}
            >
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={submitting}>
              {submitting ? "Saving…" : "Create Location"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

