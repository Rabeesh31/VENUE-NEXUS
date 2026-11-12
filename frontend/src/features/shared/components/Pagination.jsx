export default function Pagination({
  currentPage = 0,
  totalPages = 1,
  totalElements = 0,
  pageSize = 20,
  onPageChange
}) {
  if (totalPages <= 1) return null;

  return (
    <nav className="vn-pagination" aria-label="Table pagination">
      <span className="vn-pagination-summary">
        Showing Page {currentPage + 1} of {totalPages} ({totalElements} total items)
      </span>
      <div className="vn-pagination-buttons">
        <button
          type="button"
          className="secondary-button vn-page-btn"
          disabled={currentPage <= 0}
          onClick={() => onPageChange(currentPage - 1)}
          aria-label="Previous page"
        >
          Previous
        </button>
        <button
          type="button"
          className="secondary-button vn-page-btn"
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
          aria-label="Next page"
        >
          Next
        </button>
      </div>
    </nav>
  );
}

