function StepCard({ step, title, description, children, className = "", id }) {
  const titleId = `skills-step-${step}-title`;

  return (
    <section
      id={id}
      className={`skills-step-card ${className}`.trim()}
      aria-labelledby={titleId}
    >
      <div className="skills-step-heading">
        <span className="skills-step-number" aria-hidden="true">{step}</span>
        <div className="skills-step-heading-text">
          <h2 id={titleId}>{title}</h2>
          {description && <p className="skills-step-description">{description}</p>}
        </div>
      </div>
      <div className="skills-step-body">{children}</div>
    </section>
  );
}

export default StepCard;
