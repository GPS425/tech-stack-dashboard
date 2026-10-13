function SkillSummary({ jobLabel, skills }) {
  return (
    <div className="skills-confirmation">
      <div className="skills-summary">
        <div className="skills-summary-column">
          <h3>희망 직무</h3>
          <p className="skills-summary-value">{jobLabel || "선택된 직무 없음"}</p>
        </div>
        <div className="skills-summary-column">
          <h3>보유 기술{skills.length > 0 && ` (${skills.length}개)`}</h3>
          <p className="skills-summary-value">{skills.length > 0 ? skills.join(" · ") : "선택된 기술 없음"}</p>
        </div>
      </div>
      <p className="skills-summary-notice">입력한 정보는 내 기술 분석과 맞춤 채용공고 추천 등에 활용됩니다.</p>
    </div>
  );
}

export default SkillSummary;
