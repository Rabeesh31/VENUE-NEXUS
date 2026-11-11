import { useEffect, useRef } from "react";
import Modal from "../shared/components/Modal";

/**
 * QR Preview & Print Dialog
 *
 * CRITICAL SECURITY REQUIREMENT:
 * - qrData (token, SVG, pngBase64, qrUrl) is received strictly as a component prop in React state.
 * - Never stored in localStorage, sessionStorage, cookies, or history state.
 * - Never logged to console.
 * - Cleared as soon as this dialog closes.
 */
export default function QrPreviewDialog({ isOpen, onClose, resource, qrData }) {
  const printRef = useRef(null);

  // When closed, notify parent to wipe qrData from memory
  function handleClose() {
    onClose?.();
  }

  function handlePrint() {
    window.print();
  }

  function handleDownloadSvg() {
    if (!qrData?.svg) return;
    const blob = new Blob([qrData.svg], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `QR_${resource?.code || "resource"}_v${qrData.qrVersion || 1}.svg`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  if (!isOpen || !resource || !qrData) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={`QR Code Preview — ${resource.code}`}
      maxWidth="480px"
    >
      <div className="vn-qr-dialog-content">
        <div className="vn-qr-print-card" ref={printRef}>
          <div className="vn-qr-brand-header">
            <span className="vn-qr-brand-logo">VN</span>
            <span className="vn-qr-brand-name">VENUE NEXUS</span>
          </div>

          <div className="vn-qr-image-wrapper">
            {qrData.svg ? (
              <div
                className="vn-qr-svg-holder"
                dangerouslySetInnerHTML={{ __html: qrData.svg }}
              />
            ) : qrData.pngBase64 ? (
              <img
                src={`data:image/png;base64,${qrData.pngBase64}`}
                alt={`QR code for ${resource.code}`}
                className="vn-qr-img"
              />
            ) : (
              <div className="vn-qr-fallback">QR Generated (v{qrData.qrVersion})</div>
            )}
          </div>

          <div className="vn-qr-meta">
            <h3 className="vn-qr-code-text">{resource.code}</h3>
            <p className="vn-qr-name-text">{resource.displayName}</p>
            <p className="vn-qr-instruction muted">Scan at table to browse and order</p>
          </div>
        </div>

        <p className="vn-qr-security-note muted">
          ℹ This digital QR identity is generated for immediate preview and print. It is held in secure memory and is not stored in your browser storage.
        </p>

        <div className="vn-qr-actions">
          {qrData.svg && (
            <button
              type="button"
              className="secondary-button"
              onClick={handleDownloadSvg}
            >
              Download SVG
            </button>
          )}
          <button
            type="button"
            className="secondary-button"
            onClick={handlePrint}
          >
            🖨 Print QR
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={handleClose}
          >
            Done
          </button>
        </div>
      </div>
    </Modal>
  );
}

