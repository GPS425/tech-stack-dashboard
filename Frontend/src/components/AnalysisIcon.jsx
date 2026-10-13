function AnalysisIcon({ name, className = "" }) {
  const shapes = {
    chart: <><path d="M5 20v-6m7 6V9m7 11V4" strokeWidth="4" /><path d="M4 5h3m5-2h3" /></>,
    check: <><circle cx="12" cy="12" r="9" /><path d="m7.5 12 3 3 6-6" /></>,
    alert: <><path d="m12 3 10 18H2L12 3Z" /><path d="M12 9v5m0 3h.01" /></>,
    bulb: <><path d="M8 15a7 7 0 1 1 8 0c-1 1-1 2-1 3H9c0-1 0-2-1-3Zm1 6h6" /><path d="M9 7a4 4 0 0 1 3-2" /></>,
    user: <><circle cx="12" cy="7" r="4" /><path d="M3 21v-2a9 9 0 0 1 18 0v2H3Z" /></>,
    briefcase: <><rect x="3" y="7" width="18" height="14" rx="2" /><path d="M8 7V3h8v4M3 12l9 4 9-4m-9 2v4" /></>,
    book: <><path d="M12 5C8 2 4 3 2 4v16c3-2 6-2 10 0 4-2 7-2 10 0V4c-2-1-6-2-10 1Zm0 0v15" /></>,
    file: <><path d="M5 2h10l4 4v16H5V2Zm10 0v5h4M8 11h8M8 15h8m-8 4h5" /></>,
    pin: <><path d="M19 9c0 5-7 12-7 12S5 14 5 9a7 7 0 1 1 14 0Z" /><circle cx="12" cy="9" r="2" /></>,
    sprout: <><path d="M12 22V12C6 12 3 9 3 4c6 0 9 3 9 8 0-7 3-10 9-10 0 7-3 10-9 10" /></>,
    database: <><ellipse cx="12" cy="5" rx="7" ry="3" /><path d="M5 5v14c0 4 14 4 14 0V5M5 12c0 4 14 4 14 0" /></>,
    code: <><path d="m7 7-5 5 5 5m10-10 5 5-5 5M14 4l-4 16" /></>,
    trophy: <><path d="M7 3h10v6a5 5 0 0 1-10 0V3Zm0 2H3v2a5 5 0 0 0 5 5m9-7h4v2a5 5 0 0 1-5 5m-4 2v7m-4 0h8" /></>,
    star: <><path d="m12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1 3-6Z" /></>,
    grid: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    link: <><path d="m9 15 6-6m-8 4-2 2a4 4 0 0 0 6 6l4-4a4 4 0 0 0 0-6m2 0 2-2a4 4 0 0 0-6-6L9 7a4 4 0 0 0 0 6" /></>,
  };

  return (
    <svg className={`analysis-icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {shapes[name] || shapes.chart}
    </svg>
  );
}

export default AnalysisIcon;
