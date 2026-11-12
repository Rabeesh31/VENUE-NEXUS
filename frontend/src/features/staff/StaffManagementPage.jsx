import { useEffect, useState } from "react";
import {
  createStaffUser,
  deactivateStaffUser,
  getStaffUsers,
  reactivateStaffUser
} from "../shared/apiClient";
import Alert from "../shared/components/Alert";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";
import Modal from "../shared/components/Modal";
import StatusBadge from "../shared/components/StatusBadge";

const ROLES = ["TENANT_ADMIN", "VENUE_MANAGER", "SUPERVISOR", "STAFF"];

function itemsFrom(response) {
  return Array.isArray(response) ? response : (response?.items || []);
}

export default function StaffManagementPage({ activeLocation }) {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({
    email: "", password: "", displayName: "", role: "STAFF"
  });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  async function loadUsers() {
    setLoading(true);
    setError(null);
    try {
      const response = await getStaffUsers(activeLocation?.id ? { locationId: activeLocation.id } : {});
      setUsers(itemsFrom(response));
    } catch (err) {
      setError({ message: err.message || "Failed to load staff users.", traceId: err.errorData?.traceId });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadUsers();
  }, [activeLocation?.id]);

  function updateField(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  }

  async function handleCreate(event) {
    event.preventDefault();
    setSubmitting(true);
    setFormError("");
    try {
      const created = await createStaffUser({
        ...form,
        ...(activeLocation?.id ? { locationId: activeLocation.id } : {})
      });
      setUsers((current) => [created, ...current]);
      setSuccess(`Staff user "${created.displayName}" created successfully.`);
      setModalOpen(false);
      setForm({ email: "", password: "", displayName: "", role: "STAFF" });
    } catch (err) {
      setFormError(err.message || "Could not create staff user.");
    } finally {
      setSubmitting(false);
    }
  }

  async function changeStatus(user) {
    const active = String(user.status || "").toUpperCase() === "ACTIVE";
    try {
      const updated = active
        ? await deactivateStaffUser(user.id)
        : await reactivateStaffUser(user.id);
      setUsers((current) => current.map((item) => item.id === user.id ? updated : item));
      setSuccess(`${user.displayName} is now ${active ? "inactive" : "active"}.`);
    } catch (err) {
      setError({ message: err.message || "Could not update staff status.", traceId: err.errorData?.traceId });
    }
  }

  return (
    <div className="vn-feature-container">
      <div className="vn-feature-header">
        <div>
          <h2>Staff Management</h2>
          <p className="muted">Manage authenticated staff access and role assignments.</p>
        </div>
        <button type="button" className="primary-button" onClick={() => setModalOpen(true)}>
          + Add Staff User
        </button>
      </div>
      {success && <Alert type="success" message={success} onDismiss={() => setSuccess("")} />}
      {error && <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />}
      {loading ? <LoadingSkeleton message="Loading staff users…" /> : users.length === 0 ? (
        <EmptyState title="No Staff Users Yet" message="Create a staff account to grant access to this venue." actionLabel="+ Add Staff User" onAction={() => setModalOpen(true)} icon="👥" />
      ) : (
        <div className="vn-table-wrapper">
          <table className="vn-table">
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{users.map((user) => (
              <tr key={user.id}>
                <td><strong>{user.displayName}</strong></td>
                <td>{user.email}</td>
                <td><span className="vn-pill">{user.role}</span></td>
                <td><StatusBadge status={user.status || "ACTIVE"} /></td>
                <td><button type="button" className="secondary-button" onClick={() => changeStatus(user)}>
                  {String(user.status || "").toUpperCase() === "ACTIVE" ? "Deactivate" : "Reactivate"}
                </button></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      <Modal isOpen={modalOpen} onClose={() => !submitting && setModalOpen(false)} title="Add Staff User">
        <form onSubmit={handleCreate} className="vn-form">
          {formError && <Alert type="error" message={formError} />}
          <div className="vn-form-group"><label htmlFor="staffDisplayName">Display name</label><input id="staffDisplayName" name="displayName" value={form.displayName} onChange={updateField} required /></div>
          <div className="vn-form-group"><label htmlFor="staffEmail">Email</label><input id="staffEmail" name="email" type="email" value={form.email} onChange={updateField} required /></div>
          <div className="vn-form-group"><label htmlFor="staffPassword">Temporary password</label><input id="staffPassword" name="password" type="password" minLength="8" value={form.password} onChange={updateField} required /></div>
          <div className="vn-form-group"><label htmlFor="staffRole">Role</label><select id="staffRole" name="role" value={form.role} onChange={updateField}>{ROLES.map((role) => <option key={role} value={role}>{role}</option>)}</select></div>
          <div className="vn-form-actions"><button type="button" className="secondary-button" onClick={() => setModalOpen(false)} disabled={submitting}>Cancel</button><button type="submit" className="primary-button" disabled={submitting}>{submitting ? "Saving…" : "Create Staff User"}</button></div>
        </form>
      </Modal>
    </div>
  );
}
