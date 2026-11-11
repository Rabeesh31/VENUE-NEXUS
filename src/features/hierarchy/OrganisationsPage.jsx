import { useEffect, useState } from "react";
import { getOrganisations, createOrganisation } from "../shared/apiClient";
import Modal from "../shared/components/Modal";
import Alert from "../shared/components/Alert";
import StatusBadge from "../shared/components/StatusBadge";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";

export default function OrganisationsPage() {
  const [organisations, setOrganisations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    getOrganisations()
      .then((data) => {
        if (!active) return;
        const items = Array.isArray(data) ? data : (data.items || []);
        setOrganisations(items);
      })
      .catch((err) => {
        if (!active) return;
        setError({
          message: err.message || "Failed to load organisations.",
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
      setFormError("Organisation name is required.");
      return;
    }

    setSubmitting(true);
    setFormError("");

    try {
      const created = await createOrganisation({ name: name.trim() });
      setOrganisations((prev) => [...prev, created]);
      setSuccessMsg(`Organisation "${created.name}" created successfully.`);
      setName("");
      setModalOpen(false);
    } catch (err) {
      setFormError(err.message || "Could not create organisation.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="vn-feature-container">
      <div className="vn-feature-header">
        <div>
          <h2>Organisations</h2>
          <p className="muted">Manage business units and venue brand groupings under your tenant.</p>
        </div>
        <button
          type="button"
          className="primary-button"
          onClick={() => {
            setFormError("");
            setModalOpen(true);
          }}
        >
          + Add Organisation
        </button>
      </div>

      {successMsg && (
        <Alert type="success" message={successMsg} onDismiss={() => setSuccessMsg("")} />
      )}
      {error && (
        <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />
      )}

      {loading ? (
        <LoadingSkeleton message="Loading organisations…" />
      ) : organisations.length === 0 ? (
        <EmptyState
          title="No Organisations Yet"
          message="No organisations defined. Create one to organize your venue locations."
          actionLabel="+ Add Organisation"
          onAction={() => setModalOpen(true)}
          icon="🏢"
        />
      ) : (
        <div className="vn-table-wrapper">
          <table className="vn-table">
            <thead>
              <tr>
                <th scope="col">Organisation Name</th>
                <th scope="col">Status</th>
                <th scope="col">Created Date</th>
              </tr>
            </thead>
            <tbody>
              {organisations.map((org) => (
                <tr key={org.id}>
                  <td><strong>{org.name}</strong></td>
                  <td><StatusBadge status={org.status || "ACTIVE"} /></td>
                  <td className="muted">
                    {org.createdAt ? new Date(org.createdAt).toLocaleDateString() : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        isOpen={modalOpen}
        onClose={() => !submitting && setModalOpen(false)}
        title="Add Organisation"
      >
        <form onSubmit={handleCreate} className="vn-form">
          {formError && <Alert type="error" message={formError} />}

          <div className="vn-form-group">
            <label htmlFor="orgName">
              Organisation Name <span className="vn-required">*</span>
            </label>
            <input
              id="orgName"
              type="text"
              placeholder="e.g. Coastal Brands Group"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
            />
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
              {submitting ? "Saving…" : "Create Organisation"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

