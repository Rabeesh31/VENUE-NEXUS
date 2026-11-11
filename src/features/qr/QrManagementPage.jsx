import { useEffect, useState } from "react";
import { getResources, issueQr, reissueQr, revokeQr } from "../shared/apiClient";
import StatusBadge from "../shared/components/StatusBadge";
import ConfirmDialog from "../shared/components/ConfirmDialog";
import Alert from "../shared/components/Alert";
import EmptyState from "../shared/components/EmptyState";
import LoadingSkeleton from "../shared/components/LoadingSkeleton";
import Pagination from "../shared/components/Pagination";
import QrPreviewDialog from "./QrPreviewDialog";

export default function QrManagementPage({ activeLocation }) {
  const [resources, setResources] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState("");

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // In-memory one-time QR preview state (strictly in React state)
  const [previewResource, setPreviewResource] = useState(null);
  const [oneTimeQrData, setOneTimeQrData] = useState(null);

  // Confirmation dialog state for Revoke / Reissue
  const [confirmState, setConfirmState] = useState({
    isOpen: false,
    actionType: "REVOKE", // REVOKE or REISSUE
    resource: null
  });
  const [actionBusy, setActionBusy] = useState(false);

  const locationId = activeLocation?.id;

  // Clear in-memory token immediately if location changes
  useEffect(() => {
    setOneTimeQrData(null);
    setPreviewResource(null);
  }, [locationId]);

  // Load resources for the active location
  useEffect(() => {
    if (!locationId) {
      setResources([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    getResources(locationId, { page, size: 20 })
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
          message: err.message || "Failed to load resource QR statuses.",
          traceId: err.errorData?.traceId
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [locationId, page]);

  // 1. Issue QR Flow
  async function handleIssue(resource) {
    setError(null);
    try {
      const res = await issueQr(resource.id);
      // Update local table status
      setResources((prev) =>
        prev.map((r) =>
          r.id === resource.id
            ? { ...r, qrStatus: "ACTIVE", qrVersion: res.qrVersion }
            : r
        )
      );
      setSuccessMsg(`QR Identity successfully generated for ${resource.code}.`);
      // Hold in memory for preview
      setPreviewResource(resource);
      setOneTimeQrData(res);
    } catch (err) {
      setError({
        message: err.message || "Failed to issue QR identity.",
        traceId: err.errorData?.traceId
      });
    }
  }

  // 2. Open Confirm for Revoke / Reissue
  function promptAction(resource, actionType) {
    setConfirmState({
      isOpen: true,
      actionType,
      resource
    });
  }

  // 3. Confirm Handler
  async function handleConfirmAction() {
    const { resource, actionType } = confirmState;
    if (!resource) return;

    setActionBusy(true);
    setError(null);

    try {
      if (actionType === "REVOKE") {
        const revokedRecord = await revokeQr(resource.id);
        setResources((prev) =>
          prev.map((r) =>
            r.id === resource.id ? { ...r, qrStatus: "REVOKED" } : r
          )
        );
        setSuccessMsg(`QR Identity for ${resource.code} has been permanently revoked.`);
        setConfirmState({ isOpen: false, actionType: "REVOKE", resource: null });
      } else if (actionType === "REISSUE") {
        const reissuedRecord = await reissueQr(resource.id);
        setResources((prev) =>
          prev.map((r) =>
            r.id === resource.id
              ? { ...r, qrStatus: "ACTIVE", qrVersion: reissuedRecord.qrVersion }
              : r
          )
        );
        setSuccessMsg(`QR Identity for ${resource.code} reissued as version ${reissuedRecord.qrVersion}.`);
        setConfirmState({ isOpen: false, actionType: "REISSUE", resource: null });
        // Show new one-time representation
        setPreviewResource(resource);
        setOneTimeQrData(reissuedRecord);
      }
    } catch (err) {
      setError({
        message: err.message || `Failed to execute ${actionType.toLowerCase()} action.`,
        traceId: err.errorData?.traceId
      });
    } finally {
      setActionBusy(false);
    }
  }

  // Dismiss preview and wipe token from memory
  function handleClosePreview() {
    setPreviewResource(null);
    setOneTimeQrData(null);
  }

  if (!locationId) {
    return (
      <div className="vn-feature-container">
        <div className="vn-page-card">
          <EmptyState
            title="No Location Selected"
            message="Please select an active location in the header above to manage physical QR identities."
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
          <h2>QR Identity Management</h2>
          <p className="muted">
            Issue, preview, print, revoke, and reissue digital QR codes for physical resources in{" "}
            <strong>{activeLocation?.name || activeLocation?.location_name || "Active Location"}</strong>.
          </p>
        </div>
      </div>

      {successMsg && (
        <Alert type="success" message={successMsg} onDismiss={() => setSuccessMsg("")} />
      )}
      {error && (
        <Alert type="error" message={error.message} traceId={error.traceId} onDismiss={() => setError(null)} />
      )}

      {loading ? (
        <LoadingSkeleton message="Loading QR identity registry…" />
      ) : resources.length === 0 ? (
        <EmptyState
          title="No Resources Available"
          message="No resources exist at this location to issue QR codes for. Create tables or rooms in the Resources tab first."
          icon="📭"
        />
      ) : (
        <>
          <div className="vn-table-wrapper">
            <table className="vn-table">
              <thead>
                <tr>
                  <th scope="col">Resource Code</th>
                  <th scope="col">Name</th>
                  <th scope="col">Type</th>
                  <th scope="col">QR Status</th>
                  <th scope="col">QR Version</th>
                  <th scope="col">Available Actions</th>
                </tr>
              </thead>
              <tbody>
                {resources.map((res) => {
                  const hasActiveQr = res.qrStatus === "ACTIVE";

                  return (
                    <tr key={res.id}>
                      <td><code><strong>{res.code}</strong></code></td>
                      <td>{res.displayName}</td>
                      <td><span className="vn-pill">{res.type}</span></td>
                      <td><StatusBadge status={res.qrStatus || "NONE"} /></td>
                      <td>
                        {res.qrVersion ? (
                          <span className="vn-version-pill">v{res.qrVersion}</span>
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                      <td>
                        <div className="vn-row-actions">
                          {/* If active QR exists: NEVER offer Generate QR; offer Reissue and Revoke */}
                          {hasActiveQr ? (
                            <>
                              <button
                                type="button"
                                className="secondary-button vn-btn-sm"
                                onClick={() => promptAction(res, "REISSUE")}
                              >
                                Reissue QR
                              </button>
                              <button
                                type="button"
                                className="danger-button vn-btn-sm"
                                onClick={() => promptAction(res, "REVOKE")}
                              >
                                Revoke
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              className="primary-button vn-btn-sm"
                              onClick={() => handleIssue(res)}
                            >
                              Generate QR
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
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

      {/* QR Preview Dialog */}
      <QrPreviewDialog
        isOpen={Boolean(oneTimeQrData && previewResource)}
        onClose={handleClosePreview}
        resource={previewResource}
        qrData={oneTimeQrData}
      />

      {/* Confirmation Dialog for Revoke / Reissue */}
      <ConfirmDialog
        isOpen={confirmState.isOpen}
        onClose={() => !actionBusy && setConfirmState({ isOpen: false, actionType: "REVOKE", resource: null })}
        onConfirm={handleConfirmAction}
        title={confirmState.actionType === "REVOKE" ? "Revoke QR Identity" : "Reissue QR Identity"}
        message={
          confirmState.actionType === "REVOKE"
            ? `Are you sure you want to revoke the QR code for ${confirmState.resource?.code} (${confirmState.resource?.displayName})? Guests scanning this code will immediately be rejected. This action cannot be undone (a new QR must be issued).`
            : `Reissuing will immediately invalidate the existing QR code for ${confirmState.resource?.code} and generate a new version. You will need to print and replace the physical QR code at the venue table/room.`
        }
        confirmLabel={confirmState.actionType === "REVOKE" ? "Revoke QR" : "Reissue QR"}
        variant={confirmState.actionType === "REVOKE" ? "danger" : "warning"}
        isBusy={actionBusy}
      />
    </div>
  );
}

