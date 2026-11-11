import { useEffect, useState } from "react";
import { getDepartments, createDepartment } from "../shared/apiClient";
import Modal from "../shared/components/Modal";
import Alert from "../shared/components/Alert";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";

const DEPARTMENT_TYPES = [
  { value: "FLOOR_SERVICE", label: "Floor Service" },
  { value: "KITCHEN", label: "Kitchen" },
  { value: "BAR", label: "Bar / Beverage" },
  { value: "HOUSEKEEPING", label: "Housekeeping" },
  { value: "MAINTENANCE", label: "Maintenance" },
  { value: "FRONT_DESK", label: "Front Desk" },
  { value: "OTHER", label: "Other" }
];

export default function DepartmentsPage({ activeLocation }) {
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [departmentType, setDepartmentType] = useState("KITCHEN");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  const locationId = activeLocation?.id;

  useEffect(() => {
    if (!locationId) {
      setDepartments([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    getDepartments(locationId)
      .then((data) => {
        if (!active) return;
        const items = Array.isArray(data) ? data : (data.items || []);
        setDepartments(items);
      })
      .catch((err) => {
        if (!active) return;
        setError({
          message: err.message || "Failed to load departments.",
          traceId: err.errorData?.traceId
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [locationId]);

  async function handleCreate(e) {
    e.preventDefault();
    if (!name.trim()) {
      setFormError("Department name is required.");
      return;
    }

    setSubmitting(true);
    setFormError("");

    try {
      const created = await createDepartment({
        locationId,
        name: name.trim(),
        departmentType
      });

      setDepartments((prev) => [...prev, created]);
      setSuccessMsg(`Department "${created.name}" created successfully.`);
      setName("");
      setModalOpen(false);
    } catch (err) {
      // Keep user's input on failure; display backend safe error
      setFormError(err.message || "Could not create department.");
    } finally {
      setSubmitting(false);
    }
  }

  function getTypeLabel(val) {
    const found = DEPARTMENT_TYPES.find((t) => t.value === val);
    return found ? found.label : val;
  }

  if (!locationId) {
    return (
      <div className="vn-feature-container">
        <div className="vn-page-card">
          <EmptyState
            title="No Location Selected"
            message="Please select an active location in the header above to manage departments."
            icon="📍"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="vn-feature-container">
      <div className="vn-feature-header">
        <div>
          <h2>Departments</h2>
          <p className="muted">
            Operational divisions for{" "}
            <strong>{activeLocation?.name || activeLocation?.location_name || "Active Location"}</strong>.
          </p>
        </div>
        <button
          type="button"
          className="primary-button"
          onClick={() => {
            setFormError("");
            setModalOpen(true);
          }}
        >
          + Add Department
        </button>
      </div>

      {successMsg && (
        <Alert type="success" message={successMsg} onDismiss={() => setSuccessMsg("")} />
      )}
      {error && (
        <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />
      )}

      {loading ? (
        <LoadingSkeleton message="Loading departments…" />
      ) : departments.length === 0 ? (
        <EmptyState
          title="No Departments Yet"
          message="No operational departments configured for this location. Add one to route orders and assign staff."
          actionLabel="+ Add Department"
          onAction={() => setModalOpen(true)}
          icon="🛎"
        />
      ) : (
        <div className="vn-table-wrapper">
          <table className="vn-table">
            <thead>
              <tr>
                <th scope="col">Department Name</th>
                <th scope="col">Type</th>
                <th scope="col">Created Date</th>
              </tr>
            </thead>
            <tbody>
              {departments.map((dept) => (
                <tr key={dept.id}>
                  <td><strong>{dept.name}</strong></td>
                  <td>
                    <span className="vn-pill vn-pill-dept">
                      {getTypeLabel(dept.departmentType)}
                    </span>
                  </td>
                  <td className="muted">
                    {dept.createdAt ? new Date(dept.createdAt).toLocaleDateString() : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Add Department Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => !submitting && setModalOpen(false)}
        title="Add Department"
      >
        <form onSubmit={handleCreate} className="vn-form">
          {formError && <Alert type="error" message={formError} />}

          <div className="vn-form-group">
            <label htmlFor="deptLoc">Location</label>
            <input
              id="deptLoc"
              type="text"
              value={activeLocation?.name || activeLocation?.location_name || "Current Location"}
              disabled
              className="vn-input-disabled"
            />
          </div>

          <div className="vn-form-group">
            <label htmlFor="deptName">
              Department Name <span className="vn-required">*</span>
            </label>
            <input
              id="deptName"
              type="text"
              placeholder="e.g. Main Kitchen, Pool Bar, Front Desk"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div className="vn-form-group">
            <label htmlFor="deptType">
              Department Type <span className="vn-required">*</span>
            </label>
            <select
              id="deptType"
              value={departmentType}
              onChange={(e) => setDepartmentType(e.target.value)}
              required
            >
              {DEPARTMENT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
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
              {submitting ? "Saving…" : "Create Department"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

