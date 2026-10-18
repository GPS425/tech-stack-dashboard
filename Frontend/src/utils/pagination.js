export function paginate(items, requestedPage, pageSize) {
  const pageCount = Math.ceil(items.length / pageSize);
  const page = Math.min(Math.max(1, requestedPage), Math.max(1, pageCount));
  const offset = (page - 1) * pageSize;
  return {
    items: items.slice(offset, offset + pageSize),
    page,
    pageCount,
    start: items.length ? offset + 1 : 0,
    end: Math.min(offset + pageSize, items.length),
  };
}

export function getPageNumbers(currentPage, pageCount) {
  if (pageCount <= 5) return Array.from({ length: pageCount }, (_, index) => index + 1);
  const start = Math.max(2, Math.min(currentPage - 1, pageCount - 3));
  const end = Math.min(pageCount - 1, Math.max(currentPage + 1, 4));
  const pages = [1];
  if (start > 2) pages.push("start-gap");
  for (let page = start; page <= end; page += 1) pages.push(page);
  if (end < pageCount - 1) pages.push("end-gap");
  pages.push(pageCount);
  return pages;
}
