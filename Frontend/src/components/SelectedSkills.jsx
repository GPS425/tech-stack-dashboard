function SelectedSkills({ skills, onRemove, onClear, maxSkills = 10 }) {
  return (
    <aside className="skills-selected-panel" aria-labelledby="selected-skills-title">
      <div className="skills-selected-heading">
        <h3 id="selected-skills-title">
          선택한 기술 (<span className="skills-selection-count" aria-live="polite" aria-atomic="true">{skills.length}/{maxSkills}</span>)
        </h3>
        <button className="skills-clear-button" type="button" onClick={onClear} disabled={skills.length === 0}>모두 삭제</button>
      </div>
      <div className="skills-selected-content">
        {skills.length === 0 ? (
          <p className="skills-selected-empty">보유한 기술을 선택해주세요.</p>
        ) : (
          skills.map((skill) => (
            <button
              key={skill}
              className="skills-selected-tag"
              type="button"
              aria-label={`${skill} 제거`}
              onClick={() => onRemove(skill)}
            >
              <span>{skill}</span>
              <span aria-hidden="true">×</span>
            </button>
          ))
        )}
      </div>
      <p className="skills-selected-help">최대 {maxSkills}개까지 선택할 수 있습니다.</p>
    </aside>
  );
}

export default SelectedSkills;
