import AnalysisIcon from "./AnalysisIcon.jsx";

function MarketSummary({ items }) {
  return (
    <div className="trend-market-summary">
      {items.map((item) => (
        <article className="trend-summary-card" key={item.id}>
          <span className="trend-summary-icon"><AnalysisIcon name={item.icon} /></span>
          <div>
            <h3>{item.label}</h3>
            <p className={`trend-summary-value${typeof item.value === "string" ? " trend-summary-value--text" : ""}`} data-trend-metric={item.id}>{typeof item.value === "number" ? item.value.toLocaleString("ko-KR") : item.value}{item.unit}</p>
            <p className="trend-summary-description">{item.description}</p>
          </div>
        </article>
      ))}
    </div>
  );
}

export default MarketSummary;
