import AnalysisIcon from "./AnalysisIcon.jsx";

function AnalysisSummary({ analysis }) {
  return (
    <div className="analysis-kpi-grid">
      <article className="analysis-kpi-card analysis-kpi-card--fit">
        <h3><span className="analysis-kpi-icon"><AnalysisIcon name="chart" /></span>직무 적합도</h3>
        <div className="analysis-fit-content">
          <div className="analysis-fit-ring" role="progressbar" aria-label="직무 적합도" aria-valuemin={0} aria-valuemax={100} aria-valuenow={analysis.fitScore}>
            <svg viewBox="0 0 100 100" aria-hidden="true">
              <circle className="analysis-ring-track" cx="50" cy="50" r="40" />
              <circle className="analysis-ring-fill" cx="50" cy="50" r="40" pathLength="100" strokeDasharray={`${analysis.fitScore} 100`} transform="rotate(-90 50 50)" />
            </svg>
            <strong>{analysis.fitScore}%</strong>
          </div>
          <p className="analysis-card-description">현재 보유 기술로 {analysis.jobLabel} 직무{analysis.fitScore >= 60 ? "에 적합한 수준입니다." : "의 역량을 더 넓혀갈 수 있습니다."}</p>
        </div>
      </article>

      <article className="analysis-kpi-card">
        <h3><span className="analysis-kpi-icon"><AnalysisIcon name="check" /></span>매칭 기술</h3>
        <p className="analysis-kpi-value" data-metric="matched">{analysis.matchedCount} <span>개</span></p>
        <p className="analysis-card-description">시장 요구 기술 중<br />보유한 기술 수</p>
      </article>

      <article className="analysis-kpi-card">
        <h3><span className="analysis-kpi-icon analysis-kpi-icon--warning"><AnalysisIcon name="alert" /></span>부족 기술</h3>
        <p className="analysis-kpi-value" data-metric="missing">{analysis.missingSkills.length} <span>개</span></p>
        <p className="analysis-card-description">추가로 학습이 필요한<br />기술 수</p>
      </article>

      <article className="analysis-kpi-card">
        <h3><span className="analysis-kpi-icon analysis-kpi-icon--bulb"><AnalysisIcon name="bulb" /></span>핵심 보완 기술</h3>
        <p className="analysis-priority-skills">{analysis.prioritySkills.length ? analysis.prioritySkills.join(" · ") : "필수 기술을 모두 보유했어요"}</p>
        <p className="analysis-card-description">{analysis.prioritySkills.length ? "가장 우선적으로 학습을 추천하는 기술입니다." : "보유 기술을 활용해 프로젝트 경험을 쌓아보세요."}</p>
      </article>
    </div>
  );
}

export default AnalysisSummary;
