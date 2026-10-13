function SummaryCard({ label, value, unit }) {
  return (
    <article className="summary-card">
      <h3>{label}</h3>
      <p className="summary-value">
        <span>{value}</span>
        <span className="summary-unit">{unit}</span>
      </p>
    </article>
  );
}

export default SummaryCard;
