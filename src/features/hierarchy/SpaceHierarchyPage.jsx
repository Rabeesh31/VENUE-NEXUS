import { useEffect, useState } from "react";
import { getSpacesByLocation, createSpace } from "../shared/apiClient";
import Modal from "../shared/components/Modal";
import Alert from "../shared/components/Alert";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";

export default function SpaceHierarchyPage({ activeLocation }) {
  const [spaces, setSpaces] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");
  const [expandedNodes, setExpandedNodes] = useState({});

  // Modal State for adding a space
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSpaceType, setModalSpaceType] = useState("BUILDING"); // BUILDING, FLOOR, ZONE
  const [parentSpace, setParentSpace] = useState(null);
  const [spaceName, setSpaceName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  const locationId = activeLocation?.id;

  // Load spaces when activeLocation changes
  useEffect(() => {
    if (!locationId) {
      setSpaces([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    getSpacesByLocation(locationId)
      .then((data) => {
        if (!active) return;
        const items = Array.isArray(data) ? data : (data.items || []);
        setSpaces(items);
        // Expand all buildings by default
        const initialExpanded = {};
        items.forEach((s) => {
          if (s.spaceType === "BUILDING" || s.spaceType === "FLOOR") {
            initialExpanded[s.id] = true;
          }
        });
        setExpandedNodes(initialExpanded);
      })
      .catch((err) => {
        if (!active) return;
        setError({
          message: err.message || "Failed to load spaces.",
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

  function toggleExpand(id) {
    setExpandedNodes((prev) => ({ ...prev, [id]: !prev[id] }));
  }

  function openAddModal(type, parent = null) {
    setModalSpaceType(type);
    setParentSpace(parent);
    setSpaceName("");
    setFormError("");
    setModalOpen(true);
  }

  async function handleCreateSpace(e) {
    e.preventDefault();
    if (!spaceName.trim()) {
      setFormError("Space name is required.");
      return;
    }

    setSubmitting(true);
    setFormError("");

    try {
      const created = await createSpace({
        locationId,
        parentSpaceId: parentSpace?.id || null,
        spaceType: modalSpaceType,
        name: spaceName.trim()
      });

      setSpaces((prev) => [...prev, created]);
      if (parentSpace?.id) {
        setExpandedNodes((prev) => ({ ...prev, [parentSpace.id]: true }));
      }
      setSuccessMsg(`Successfully created ${modalSpaceType.toLowerCase()} "${created.name}".`);
      setModalOpen(false);
    } catch (err) {
      setFormError(err.message || "Could not create space.");
    } finally {
      setSubmitting(false);
    }
  }

  // Filter hierarchy
  const buildings = spaces.filter((s) => s.spaceType === "BUILDING" && !s.parentSpaceId);

  function getFloorsForBuilding(buildingId) {
    return spaces.filter((s) => s.spaceType === "FLOOR" && s.parentSpaceId === buildingId);
  }

  function getZonesForFloor(floorId) {
    return spaces.filter((s) => s.spaceType === "ZONE" && s.parentSpaceId === floorId);
  }

  if (!locationId) {
    return (
      <div className="vn-page-card">
        <EmptyState
          title="No Location Selected"
          message="Please select an active location using the switcher in the header above to manage spaces."
          icon="📍"
        />
      </div>
    );
  }

  return (
    <div className="vn-feature-container">
      <div className="vn-feature-header">
        <div>
          <h2>Space Hierarchy</h2>
          <p className="muted">
            Manage physical architecture (Buildings &gt; Floors &gt; Zones) for{" "}
            <strong>{activeLocation?.name || activeLocation?.location_name || "Active Location"}</strong>.
          </p>
        </div>
        <button
          type="button"
          className="primary-button"
          onClick={() => openAddModal("BUILDING", null)}
        >
          + Add Building
        </button>
      </div>

      {successMsg && (
        <Alert
          type="success"
          message={successMsg}
          onDismiss={() => setSuccessMsg("")}
        />
      )}

      {error && (
        <Alert
          type="error"
          message={error.message}
          traceId={error.traceId}
          onDismiss={() => setError(null)}
        />
      )}

      {loading ? (
        <LoadingSkeleton message="Loading space hierarchy…" />
      ) : buildings.length === 0 ? (
        <EmptyState
          title="No Buildings Yet"
          message="This location does not have any buildings defined. Start by adding the primary building."
          actionLabel="+ Add Building"
          onAction={() => openAddModal("BUILDING", null)}
          icon="🏢"
        />
      ) : (
        <div className="vn-tree-container" role="tree" aria-label="Physical Space Hierarchy">
          {buildings.map((building) => {
            const isBldExpanded = !!expandedNodes[building.id];
            const floors = getFloorsForBuilding(building.id);

            return (
              <div key={building.id} className="vn-tree-node vn-tree-building" role="treeitem" aria-expanded={isBldExpanded}>
                <div className="vn-tree-row">
                  <button
                    type="button"
                    className="vn-tree-toggle"
                    onClick={() => toggleExpand(building.id)}
                    aria-label={`Toggle ${building.name}`}
                  >
                    {isBldExpanded ? "▼" : "▶"}
                  </button>
                  <span className="vn-tree-icon" aria-hidden="true">🏢</span>
                  <div className="vn-tree-info">
                    <strong className="vn-tree-name">{building.name}</strong>
                    <span className="vn-tree-tag">BUILDING</span>
                  </div>
                  <div className="vn-tree-actions">
                    <button
                      type="button"
                      className="secondary-button vn-btn-sm"
                      onClick={() => openAddModal("FLOOR", building)}
                    >
                      + Add Floor
                    </button>
                  </div>
                </div>

                {isBldExpanded && (
                  <div className="vn-tree-children" role="group">
                    {floors.length === 0 ? (
                      <p className="vn-tree-empty muted">No floors added to this building yet.</p>
                    ) : (
                      floors.map((floor) => {
                        const isFloorExpanded = !!expandedNodes[floor.id];
                        const zones = getZonesForFloor(floor.id);

                        return (
                          <div key={floor.id} className="vn-tree-node vn-tree-floor" role="treeitem" aria-expanded={isFloorExpanded}>
                            <div className="vn-tree-row">
                              <button
                                type="button"
                                className="vn-tree-toggle"
                                onClick={() => toggleExpand(floor.id)}
                                aria-label={`Toggle ${floor.name}`}
                              >
                                {isFloorExpanded ? "▼" : "▶"}
                              </button>
                              <span className="vn-tree-icon" aria-hidden="true">🪜</span>
                              <div className="vn-tree-info">
                                <span className="vn-tree-name">{floor.name}</span>
                                <span className="vn-tree-tag">FLOOR</span>
                              </div>
                              <div className="vn-tree-actions">
                                <button
                                  type="button"
                                  className="secondary-button vn-btn-sm"
                                  onClick={() => openAddModal("ZONE", floor)}
                                >
                                  + Add Zone
                                </button>
                              </div>
                            </div>

                            {isFloorExpanded && (
                              <div className="vn-tree-children" role="group">
                                {zones.length === 0 ? (
                                  <p className="vn-tree-empty muted">No zones in this floor yet.</p>
                                ) : (
                                  zones.map((zone) => (
                                    <div key={zone.id} className="vn-tree-node vn-tree-zone" role="treeitem">
                                      <div className="vn-tree-row">
                                        <span className="vn-tree-leaf-bullet" aria-hidden="true">•</span>
                                        <span className="vn-tree-icon" aria-hidden="true">📍</span>
                                        <div className="vn-tree-info">
                                          <span className="vn-tree-name">{zone.name}</span>
                                          <span className="vn-tree-tag">ZONE</span>
                                        </div>
                                      </div>
                                    </div>
                                  ))
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Add Space Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => !submitting && setModalOpen(false)}
        title={`Add ${modalSpaceType === "BUILDING" ? "Building" : modalSpaceType === "FLOOR" ? "Floor" : "Zone"}`}
      >
        <form onSubmit={handleCreateSpace} className="vn-form">
          {formError && <Alert type="error" message={formError} />}

          {parentSpace && (
            <div className="vn-form-group">
              <label>Parent Space</label>
              <input
                type="text"
                value={`${parentSpace.spaceType}: ${parentSpace.name}`}
                disabled
                className="vn-input-disabled"
              />
            </div>
          )}

          <div className="vn-form-group">
            <label htmlFor="spaceName">
              {modalSpaceType} Name <span className="vn-required">*</span>
            </label>
            <input
              id="spaceName"
              type="text"
              placeholder={
                modalSpaceType === "BUILDING"
                  ? "e.g. West Wing, Tower A"
                  : modalSpaceType === "FLOOR"
                  ? "e.g. Ground Floor, 2nd Floor"
                  : "e.g. Patio, VIP Dining, Bar Section"
              }
              value={spaceName}
              onChange={(e) => setSpaceName(e.target.value)}
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
              {submitting ? "Saving…" : `Create ${modalSpaceType}`}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

