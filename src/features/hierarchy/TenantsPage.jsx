import { useEffect, useState } from "react";
import { getTenants, createTenant, updateTenantStatus } from "../shared/apiClient";
import Modal from "../shared/components/Modal";
import ConfirmDialog from "../shared/components/ConfirmDialog";
import Alert from "../shared/components/Alert";
import StatusBadge from "../shared/components/StatusBadge";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";

export default function TenantsPage({ user, roles = [] }) {
  const isPlatformAdmin = roles.some(
    (r) => String(r).toUpperCase() === "PLATFORM_ADMIN" || String(r).toLowerCase() === "admin"
  );

  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");

  // Create Modal
  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  // Suspend/Reactivate confirmation
  const [confirmState, setConfirmState] = useState({
    isOpen: false,
    tenant: null,
    targetStatus: "SUSPENDED"
  });
  const [statusBusy, setStatusBusy] = useState(false);

  useEffect(() => {
    if (!isPlatformAdmin) {
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    getTenants()
      .then((data) => {
        if (!active) return;
        const items = Array.isArray(data) ? data : (data.items || []);
        setTenants(items);
      })
      .catch((err) => {
        if (!active) return;
        setError({
          message: err.message || "Failed to load tenants.",
          traceId: err.errorData?.traceId
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [isPlatformAdmin]);

  // Access Denied guard
  if (!isPlatformAdmin) {
    return (
      <div className="vn-feature-container">
        <div className="vn-page-card">
          <EmptyState
            title="Access Restricted"
            message="Tenant management is only accessible to Platform Administrators."
            icon="🔒"
          />
        </div>
      </div>
    );
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!name.trim()) {
      setFormError("Tenant name is required.");
      return;
    }

    setSubmitting(true);
    setFormError("");

    try {
      const created = await createTenant({ name: name.trim() });
      setTenants((prev) => [...prev, created]);
      setSuccessMsg(`Tenant "${created.name}" created successfully.`);
      setName("");
      setModalOpen(false);
    } catch (err) {
      setFormError(err.message || "Could not create tenant.");
    } finally {
      setSubmitting(false);
    }
  }

  function promptStatusChange(tenant, targetStatus) {
    setConfirmState({
      isOpen: true,
      tenant,
      targetStatus
    });
  }

  async function handleConfirmStatusChange() {
    const { tenant, targetStatus } = confirmState;
    if (!tenant) return;

    setStatusBusy(true);
    try {
      const updated = await updateTenantStatus(tenant.id, targetStatus);
      setTenants((prev) =>
        prev.map((t) => (t.id === tenant.id ? { ...t, status: updated.status } : t))
      );
      setSuccessMsg(
        `Tenant "${tenant.name}" has been ${targetStatus === "SUSPENDED" ? "suspended" : "reactivated"}.`
      );
      setConfirmState({ isOpen: false, tenant: null, targetStatus: "SUSPENDED" });
    } catch (err) {
      setError({
        message: err.message || "Failed to update tenant status.",
        traceId: err.errorData?.traceId
      });
    } finally {
      setStatusBusy(false);
    }
  }

  return (
    <div className="vn-feature-container">
      <div className="vn-feature-header">
        <div>
          <h2>Tenants</h2>
          <p className="muted">Platform-level enterprise accounts and tenant status management.</p>
        </div>
        <button
          type="button"
          className="primary-button"
          onClick={() => {
            setFormError("");
            setModalOpen(true);
          }}
        >
          + Add Tenant
        </button>
      </div>

      {successMsg && (
        <Alert type="success" message={successMsg} onDismiss={() => setSuccessMsg("")} />
      )}
      {error && (
        <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />
      )}

      {loading ? (
        <LoadingSkeleton message="Loading tenants…" />
      ) : tenants.length === 0 ? (
        <EmptyState
          title="No Tenants Found"
          message="No customer accounts exist on this platform instance yet."
          actionLabel="+ Add Tenant"
          onAction={() => setModalOpen(true)}
          icon="🏢"
        />
      ) : (
        <div className="vn-table-wrapper">
          <table className="vn-table">
            <thead>
              <tr>
                <th scope="col">Tenant Name</th>
                <th scope="col">Status</th>
                <th scope="col">Created Date</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((t) => (
                <tr key={t.id}>
                  <td><strong>{t.name}</strong></td>
                  <td><StatusBadge status={t.status || "ACTIVE"} /></td>
                  <td className="muted">
                    {t.createdAt ? new Date(t.createdAt).toLocaleDateString() : "—"}
                  </td>
                  <td>
                    {t.status === "ACTIVE" ? (
                      <button
                        type="button"
                        className="danger-button vn-btn-sm"
                        onClick={() => promptStatusChange(t, "SUSPENDED")}
                      >
                        Suspend
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="secondary-button vn-btn-sm"
                        onClick={() => promptStatusChange(t, "ACTIVE")}
                      >
                        Reactivate
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Tenant Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => !submitting && setModalOpen(false)}
        title="Add Tenant"
      >
        <form onSubmit={handleCreate} className="vn-form">
          {formError && <Alert type="error" message={formError} />}

          <div className="vn-form-group">
            <label htmlFor="tenantName">
              Tenant Business Name <span className="vn-required">*</span>
            </label>
            <input
              id="tenantName"
              type="text"
              placeholder="e.g. Apex Hospitality Group"
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
              {submitting ? "Saving…" : "Create Tenant"}
            </button>
          </div>
        </form>
      </Modal>

      {/* Status Change Confirmation */}
      <ConfirmDialog
        isOpen={confirmState.isOpen}
        onClose={() => !statusBusy && setConfirmState({ isOpen: false, tenant: null, targetStatus: "SUSPENDED" })}
        onConfirm={handleConfirmStatusChange}
        title={confirmState.targetStatus === "SUSPENDED" ? "Suspend Tenant" : "Reactivate Tenant"}
        message={
          confirmState.targetStatus === "SUSPENDED"
            ? `Are you sure you want to suspend "${confirmState.tenant?.name}"? Staff users from this tenant will immediately lose access.`
            : `Are you sure you want to reactivate "${confirmState.tenant?.name}"? Staff will be able to sign in and operate venue resources.`
        }
        confirmLabel={confirmState.targetStatus === "SUSPENDED" ? "Suspend Tenant" : "Reactivate Tenant"}
        variant={confirmState.targetStatus === "SUSPENDED" ? "danger" : "primary"}
        isBusy={statusBusy}
      />
    </div>
  );
}

