import { useEffect, useState } from "react";
import {
  getResources,
  createResource,
  bulkCreateResources,
  getSpacesByLocation,
  getDepartments
} from "../shared/apiClient";
import Modal from "../shared/components/Modal";
import Alert from "../shared/components/Alert";
import StatusBadge from "../shared/components/StatusBadge";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";
import Pagination from "../shared/components/Pagination";

const RESOURCE_TYPES = [
  { value: "TABLE", label: "Table" },
  { value: "ROOM", label: "Room / Suite" },
  { value: "COUNTER", label: "Counter / Station" },
  { value: "SEAT", label: "Seat / Chair" },
  { value: "DESK", label: "Desk / Workspace" },
  { value: "BED", label: "Bed" },
  { value: "EQUIPMENT", label: "Equipment" },
  { value: "OTHER", label: "Other" }
];

const ACTIVATION_POLICIES = [
  {
    value: "STAFF_CONFIRM",
    label: "Staff Confirm",
    description: "Requires staff member approval before order/session activates."
  },
  {
    value: "STAY_VERIFY",
    label: "Stay Verify",
    description: "Requires matching guest reservation or room verification."
  },
  {
    value: "OPEN",
    label: "Open Access",
    description: "Direct guest session activation (low-risk resources only)."
  }
];

export default function ResourcesPage({ activeLocation, onNavigateToQr }) {
  const [resources, setResources] = useState([]);
  const [spaces, setSpaces] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");

  // Filters & Pagination
  const [typeFilter, setTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Single Create Modal State
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [code, setCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [resourceType, setResourceType] = useState("TABLE");
  const [selectedSpaceId, setSelectedSpaceId] = useState("");
  const [selectedDeptId, setSelectedDeptId] = useState("");
  const [activationPolicy, setActivationPolicy] = useState("STAFF_CONFIRM");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  // Bulk Create Modal State
  const [bulkModalOpen, setBulkModalOpen] = useState(false);
  const [bulkPrefix, setBulkPrefix] = useState("T");
  const [bulkStart, setBulkStart] = useState(1);
  const [bulkEnd, setBulkEnd] = useState(10);
  const [bulkPadWidth, setBulkPadWidth] = useState(2);
  const [bulkType, setBulkType] = useState("TABLE");
  const [bulkSpaceId, setBulkSpaceId] = useState("");
  const [bulkDeptId, setBulkDeptId] = useState("");
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [bulkError, setBulkError] = useState("");

  const locationId = activeLocation?.id;

  // Load supporting spaces and departments when activeLocation changes
  useEffect(() => {
    if (!locationId) {
      setSpaces([]);
      setDepartments([]);
      return;
    }

    Promise.all([
      getSpacesByLocation(locationId).catch(() => []),
      getDepartments(locationId).catch(() => ({ items: [] }))
    ]).then(([spacesData, deptsData]) => {
      setSpaces(Array.isArray(spacesData) ? spacesData : (spacesData.items || []));
      setDepartments(Array.isArray(deptsData) ? deptsData : (deptsData.items || []));
    });
  }, [locationId]);

  // Load resources
  useEffect(() => {
    if (!locationId) {
      setResources([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    getResources(locationId, {
      type: typeFilter,
      status: statusFilter,
      page,
      size: 20
    })
      .then((data) => {
        if (!active) return;
        const items = Array.isArray(data) ? data : (data.items || []);
        setResources(items);
        setTotalPages(data.totalPages || 1);
        setTotalElements(data.totalElements || items.length);
      })
      .catch((err) => {
        if (!active) return;
        setError({
          message: err.message || "Failed to load resources.",
          traceId: err.errorData?.traceId
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [locationId, typeFilter, statusFilter, page]);

  // Handle Single Resource Create
  async function handleCreate(e) {
    e.preventDefault();
    if (!code.trim()) {
      setFormError("Resource code is required (e.g. T12).");
      return;
    }
    if (!displayName.trim()) {
      setFormError("Display name is required (e.g. Table 12).");
      return;
    }

    setSubmitting(true);
    setFormError("");

    try {
      const created = await createResource({
        code: code.trim(),
        displayName: displayName.trim(),
        type: resourceType,
        locationId,
        spaceId: selectedSpaceId || null,
        departmentId: selectedDeptId || null,
        activationPolicy
      });

      setResources((prev) => [created, ...prev]);
      setTotalElements((prev) => prev + 1);
      setSuccessMsg(`Resource "${created.code} - ${created.displayName}" created successfully.`);
      setCode("");
      setDisplayName("");
      setCreateModalOpen(false);
    } catch (err) {
      setFormError(err.message || "Could not create resource.");
    } finally {
      setSubmitting(false);
    }
  }

  // Handle Bulk Resource Create
  async function handleBulkCreate(e) {
    e.preventDefault();
    const startNum = parseInt(bulkStart, 10);
    const endNum = parseInt(bulkEnd, 10);

    if (isNaN(startNum) || isNaN(endNum) || startNum < 0 || endNum < startNum) {
      setBulkError("Please enter valid start and end numbers (start ≤ end).");
      return;
    }
    if (endNum - startNum > 100) {
      setBulkError("Maximum batch range is 100 resources at a time.");
      return;
    }

    setBulkSubmitting(true);
    setBulkError("");

    try {
      const createdList = await bulkCreateResources({
        prefix: bulkPrefix.trim().toUpperCase(),
        start: startNum,
        end: endNum,
        padWidth: parseInt(bulkPadWidth, 10) || 0,
        type: bulkType,
        locationId,
        spaceId: bulkSpaceId || null,
        departmentId: bulkDeptId || null
      });

      setResources((prev) => [...createdList, ...prev]);
      setTotalElements((prev) => prev + createdList.length);
      setSuccessMsg(`Successfully batch created ${createdList.length} resources.`);
      setBulkModalOpen(false);
    } catch (err) {
      setBulkError(err.message || "Could not bulk create resources.");
    } finally {
      setBulkSubmitting(false);
    }
  }

  function getSpaceName(spId) {
    const sp = spaces.find((s) => s.id === spId);
    return sp ? `${sp.name} (${sp.spaceType})` : "—";
  }

  function getDepartmentName(deptId) {
    const dept = departments.find((d) => d.id === deptId);
    return dept ? dept.name : "—";
  }

  if (!locationId) {
    return (
      <div className="vn-feature-container">
        <div className="vn-page-card">
          <EmptyState
            title="No Location Selected"
            message="Please select an active location in the header above to view and manage resources."
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
          <h2>Physical Resources</h2>
          <p className="muted">
            Manage tables, rooms, counters, and stations for{" "}
            <strong>{activeLocation?.name || activeLocation?.location_name || "Active Location"}</strong>.
          </p>
        </div>
        <div className="vn-header-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setBulkError("");
              setBulkModalOpen(true);
            }}
          >
            + Bulk Create
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={() => {
              setFormError("");
              setCreateModalOpen(true);
            }}
          >
            + Create Resource
          </button>
        </div>
      </div>

      {successMsg && (
        <Alert type="success" message={successMsg} onDismiss={() => setSuccessMsg("")} />
      )}
      {error && (
        <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />
      )}

      {/* Filter Toolbar */}
      <div className="vn-filter-toolbar">
        <div className="vn-filter-item">
          <label htmlFor="filterType">Type:</label>
          <select
            id="filterType"
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All Types</option>
            {RESOURCE_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.label}</option>
            ))}
          </select>
        </div>

        <div className="vn-filter-item">
          <label htmlFor="filterStatus">Status:</label>
          <select
            id="filterStatus"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All Statuses</option>
            <option value="AVAILABLE">Available</option>
            <option value="OCCUPIED">Occupied</option>
            <option value="OUT_OF_SERVICE">Out of Service</option>
          </select>
        </div>
      </div>

      {loading ? (
        <LoadingSkeleton message="Loading physical resources…" />
      ) : resources.length === 0 ? (
        <EmptyState
          title="No Resources Found"
          message={
            typeFilter || statusFilter
              ? "No resources match your active filters. Try resetting the filters."
              : "No physical resources added to this location yet. Create tables, rooms or counters to issue QR codes."
          }
          actionLabel="+ Create Resource"
          onAction={() => setCreateModalOpen(true)}
          icon="🪑"
        />
      ) : (
        <>
          <div className="vn-table-wrapper">
            <table className="vn-table">
              <thead>
                <tr>
                  <th scope="col">Code</th>
                  <th scope="col">Display Name</th>
                  <th scope="col">Type</th>
                  <th scope="col">Space Location</th>
                  <th scope="col">Department</th>
                  <th scope="col">Status</th>
                  <th scope="col">QR Identity</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {resources.map((res) => (
                  <tr key={res.id}>
                    <td><code><strong>{res.code}</strong></code></td>
                    <td>{res.displayName}</td>
                    <td><span className="vn-pill">{res.type}</span></td>
                    <td className="muted">{getSpaceName(res.spaceId)}</td>
                    <td className="muted">{getDepartmentName(res.departmentId)}</td>
                    <td><StatusBadge status={res.status} /></td>
                    <td>
                      <StatusBadge status={res.qrStatus || "NONE"} />
                      {res.qrVersion && <span className="vn-version-pill">v{res.qrVersion}</span>}
                    </td>
                    <td>
                      {onNavigateToQr && (
                        <button
                          type="button"
                          className="secondary-button vn-btn-sm"
                          onClick={onNavigateToQr}
                          title="Open QR Management"
                        >
                          QR Actions →
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            currentPage={page}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={20}
            onPageChange={setPage}
          />
        </>
      )}

      {/* Create Resource Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => !submitting && setCreateModalOpen(false)}
        title="Create Physical Resource"
      >
        <form onSubmit={handleCreate} className="vn-form">
          {formError && <Alert type="error" message={formError} />}

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="resCode">
                Resource Code <span className="vn-required">*</span>
              </label>
              <input
                id="resCode"
                type="text"
                placeholder="e.g. T12, R305, BAR-01"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                required
                autoFocus
              />
              <span className="vn-form-hint">Unique alphanumeric code within location.</span>
            </div>

            <div className="vn-form-group">
              <label htmlFor="resName">
                Display Name <span className="vn-required">*</span>
              </label>
              <input
                id="resName"
                type="text"
                placeholder="e.g. Table 12, Ocean View Suite 305"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="resType">
                Resource Type <span className="vn-required">*</span>
              </label>
              <select
                id="resType"
                value={resourceType}
                onChange={(e) => setResourceType(e.target.value)}
                required
              >
                {RESOURCE_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>

            <div className="vn-form-group">
              <label htmlFor="resSpace">Space (Zone / Floor)</label>
              <select
                id="resSpace"
                value={selectedSpaceId}
                onChange={(e) => setSelectedSpaceId(e.target.value)}
              >
                <option value="">(No specific space)</option>
                {spaces.map((sp) => (
                  <option key={sp.id} value={sp.id}>
                    {sp.name} ({sp.spaceType})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="resDept">Department</label>
              <select
                id="resDept"
                value={selectedDeptId}
                onChange={(e) => setSelectedDeptId(e.target.value)}
              >
                <option value="">(No specific department)</option>
                {departments.map((dept) => (
                  <option key={dept.id} value={dept.id}>
                    {dept.name} ({dept.departmentType})
                  </option>
                ))}
              </select>
            </div>

            <div className="vn-form-group">
              <label htmlFor="resPolicy">
                Activation Policy <span className="vn-required">*</span>
              </label>
              <select
                id="resPolicy"
                value={activationPolicy}
                onChange={(e) => setActivationPolicy(e.target.value)}
                required
              >
                {ACTIVATION_POLICIES.map((p) => (
                  <option key={p.value} value={p.value}>{p.label}</option>
                ))}
              </select>
            </div>
          </div>

          <p className="vn-policy-help muted">
            {ACTIVATION_POLICIES.find((p) => p.value === activationPolicy)?.description}
          </p>

          <div className="vn-form-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={() => setCreateModalOpen(false)}
              disabled={submitting}
            >
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={submitting}>
              {submitting ? "Saving…" : "Create Resource"}
            </button>
          </div>
        </form>
      </Modal>

      {/* Bulk Create Modal */}
      <Modal
        isOpen={bulkModalOpen}
        onClose={() => !bulkSubmitting && setBulkModalOpen(false)}
        title="Bulk Create Resources"
      >
        <form onSubmit={handleBulkCreate} className="vn-form">
          {bulkError && <Alert type="error" message={bulkError} />}

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="bulkPrefix">
                Prefix <span className="vn-required">*</span>
              </label>
              <input
                id="bulkPrefix"
                type="text"
                placeholder="e.g. T, ROOM-, SEAT"
                value={bulkPrefix}
                onChange={(e) => setBulkPrefix(e.target.value.toUpperCase())}
                required
              />
            </div>
            <div className="vn-form-group">
              <label htmlFor="bulkType">Type</label>
              <select
                id="bulkType"
                value={bulkType}
                onChange={(e) => setBulkType(e.target.value)}
              >
                {RESOURCE_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="bulkStart">Start Number</label>
              <input
                id="bulkStart"
                type="number"
                min="0"
                max="99999"
                value={bulkStart}
                onChange={(e) => setBulkStart(e.target.value)}
                required
              />
            </div>

            <div className="vn-form-group">
              <label htmlFor="bulkEnd">End Number</label>
              <input
                id="bulkEnd"
                type="number"
                min="0"
                max="99999"
                value={bulkEnd}
                onChange={(e) => setBulkEnd(e.target.value)}
                required
              />
            </div>

            <div className="vn-form-group">
              <label htmlFor="bulkPad">Digit Padding</label>
              <select
                id="bulkPad"
                value={bulkPadWidth}
                onChange={(e) => setBulkPadWidth(e.target.value)}
              >
                <option value="0">None (1, 2, ...)</option>
                <option value="2">2 Digits (01, 02, ...)</option>
                <option value="3">3 Digits (001, 002, ...)</option>
                <option value="4">4 Digits (0001, ...)</option>
              </select>
            </div>
          </div>

          {/* Live Preview Box */}
          <div className="vn-preview-box">
            <span className="vn-preview-label">Batch Preview:</span>
            <strong>
              Will create {Math.max(0, parseInt(bulkEnd, 10) - parseInt(bulkStart, 10) + 1 || 0)} resources:{" "}
              <code>
                {bulkPrefix}
                {String(bulkStart).padStart(parseInt(bulkPadWidth, 10) || 0, "0")}
              </code>{" "}
              …{" "}
              <code>
                {bulkPrefix}
                {String(bulkEnd).padStart(parseInt(bulkPadWidth, 10) || 0, "0")}
              </code>
            </strong>
          </div>

          <div className="vn-form-row">
            <div className="vn-form-group">
              <label htmlFor="bulkSpace">Space Association</label>
              <select
                id="bulkSpace"
                value={bulkSpaceId}
                onChange={(e) => setBulkSpaceId(e.target.value)}
              >
                <option value="">(No specific space)</option>
                {spaces.map((sp) => (
                  <option key={sp.id} value={sp.id}>
                    {sp.name} ({sp.spaceType})
                  </option>
                ))}
              </select>
            </div>

            <div className="vn-form-group">
              <label htmlFor="bulkDept">Department</label>
              <select
                id="bulkDept"
                value={bulkDeptId}
                onChange={(e) => setBulkDeptId(e.target.value)}
              >
                <option value="">(No specific department)</option>
                {departments.map((dept) => (
                  <option key={dept.id} value={dept.id}>
                    {dept.name} ({dept.departmentType})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="vn-form-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={() => setBulkModalOpen(false)}
              disabled={bulkSubmitting}
            >
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={bulkSubmitting}>
              {bulkSubmitting ? "Generating Batch…" : "Create Resources"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

