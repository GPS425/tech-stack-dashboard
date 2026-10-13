import AnalysisIcon from "./AnalysisIcon.jsx";

function SkillGroup({ title, description, skills, icon, variant = "", emptyText }) {
  return (
    <article className={`analysis-skill-group ${variant ? `analysis-skill-group--${variant}` : ""}`}>
      <div className="analysis-skill-group-heading">
        <AnalysisIcon name={icon} />
        <div><h3>{title}</h3><p>{description}</p></div>
      </div>
      <div className="analysis-tags">{skills.map((skill) => <span className="analysis-tag" key={skill}>{skill}</span>)}</div>
      {skills.length === 0 && <p className="analysis-empty-text">{emptyText}</p>}
    </article>
  );
}

function SkillGapComparison({ analysis }) {
  return (
    <div className="analysis-comparison-grid">
      <SkillGroup title="시장 요구 기술" description="현재 채용시장에서 많이 요구되는 주요 기술입니다." skills={analysis.marketSkills} icon="chart" />
      <SkillGroup title="내 보유 기술" description="현재 보유하고 있는 기술입니다." skills={analysis.ownedSkills} icon="user" emptyText="아직 선택한 기술이 없습니다." />
      <SkillGroup title="부족 기술" description="추가로 학습이 필요한 기술입니다." skills={analysis.missingSkills} icon="alert" variant="missing" emptyText="시장 요구 기술을 모두 보유하고 있습니다." />
      <aside className="analysis-coverage-card" aria-labelledby="analysis-coverage-title">
        <h3 id="analysis-coverage-title"><AnalysisIcon name="chart" />기술 보유 현황</h3>
        <dl>
          <div><dt>보유 기술</dt><dd>{analysis.ownedSkills.length} 개</dd></div>
          <div><dt>부족 기술</dt><dd>{analysis.missingSkills.length} 개</dd></div>
          <div><dt>전체 인기 기술</dt><dd>{analysis.marketSkills.length} 개</dd></div>
        </dl>
        <div className="analysis-coverage-progress">
          <div className="analysis-progress-track" role="progressbar" aria-label="시장 요구 기술 보유 비율" aria-valuemin={0} aria-valuemax={100} aria-valuenow={analysis.coverage}>
            <span style={{ width: `${analysis.coverage}%` }} />
          </div>
          <span>{analysis.coverage}%</span>
        </div>
      </aside>
    </div>
  );
}

export default SkillGapComparison;
