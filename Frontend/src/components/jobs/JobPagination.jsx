import { getPageNumbers } from "../../utils/pagination.js";

function JobPagination({ pagination, total, pageSize, onPageChange, onPageSizeChange }) {
  return (
    <div className="jobs-pagination-area">
      {pagination.pageCount > 0 && <nav className="jobs-pagination" aria-label="채용공고 페이지">
        <button type="button" aria-label="이전 페이지" disabled={pagination.page === 1} onClick={() => onPageChange(pagination.page - 1)}>‹</button>
        {getPageNumbers(pagination.page, pagination.pageCount).map((page) => typeof page === "number"
          ? <button type="button" key={page} aria-label={`${page}페이지`} aria-current={page === pagination.page ? "page" : undefined} onClick={() => onPageChange(page)}>{page}</button>
          : <span key={page} aria-hidden="true">…</span>)}
        <button type="button" aria-label="다음 페이지" disabled={pagination.page === pagination.pageCount} onClick={() => onPageChange(pagination.page + 1)}>›</button>
      </nav>}
      <div className="jobs-pagination-summary">
        <p>전체 {total}건 중 {pagination.start}–{pagination.end}건 표시</p>
        <label>페이지당 <select aria-label="페이지당 공고 수" value={pageSize} onChange={(event) => onPageSizeChange(Number(event.target.value))}><option value={10}>10개</option><option value={20}>20개</option></select></label>
      </div>
    </div>
  );
}

export default JobPagination;
