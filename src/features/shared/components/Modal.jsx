import { useEffect, useRef } from "react";

export default function Modal({ title, isOpen, onClose, children, maxWidth = "540px" }) {
  const modalRef = useRef(null);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") {
        onClose?.();
      }
    }

    if (isOpen) {
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", handleKeyDown);
      modalRef.current?.focus();
    } else {
      document.body.style.overflow = "";
    }

    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="vn-modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="vn-modal-card"
        style={{ maxWidth }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="vn-modal-title"
        ref={modalRef}
        tabIndex="-1"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="vn-modal-header">
          <h2 id="vn-modal-title" className="vn-modal-title">{title}</h2>
          <button
            type="button"
            className="vn-modal-close"
            onClick={onClose}
            aria-label="Close dialog"
          >
            &times;
          </button>
        </div>
        <div className="vn-modal-body">{children}</div>
      </div>
    </div>
  );
}

