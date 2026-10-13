import { useState } from "react";
import SelectedSkills from "./SelectedSkills.jsx";

function SkillSelector({
  skills,
  popularSkills,
  recommendedSkills,
  jobLabel,
  selectedSkills,
  onToggleSkill,
  onClearSkills,
  searchQuery,
  onSearchChange,
  maxSkills = 10,
}) {
  const [showAllSkills, setShowAllSkills] = useState(false);
  const query = searchQuery.trim().toLocaleLowerCase();
  const visibleSkills = query
    ? skills.filter((skill) => skill.toLocaleLowerCase().includes(query))
    : showAllSkills ? skills : popularSkills;
  const selectionIsFull = selectedSkills.length >= maxSkills;

  function renderSkillTag(skill) {
    const isSelected = selectedSkills.includes(skill);
    const isDisabled = selectionIsFull && !isSelected;

    return (
      <button
        key={skill}
        className={`skills-tag${isSelected ? " is-selected" : ""}`}
        type="button"
        aria-pressed={isSelected}
        disabled={isDisabled}
        title={isDisabled ? `기술 스택은 최대 ${maxSkills}개까지 선택할 수 있습니다.` : undefined}
        onClick={() => onToggleSkill(skill)}
      >
        {skill}
      </button>
    );
  }

  return (
    <div className="skills-selector">
      <div className="skills-recommendations">
        <div className="skills-recommendations-heading">
          <h3>추천 기술</h3>
          <p>{jobLabel ? `${jobLabel}에게 자주 요구되는 기술입니다.` : "직무를 선택하면 맞춤 추천 기술을 볼 수 있어요."}</p>
        </div>
        <div className="skills-tags">{recommendedSkills.map(renderSkillTag)}</div>
      </div>

      <div className="skills-selector-body">
        <div className="skills-picker">
          <label className="skills-search-field">
            <span className="sr-only">기술 스택 검색</span>
            <svg viewBox="0 0 20 20" aria-hidden="true">
              <circle cx="8.5" cy="8.5" r="5.5" />
              <path d="m12.5 12.5 4.5 4.5" />
            </svg>
            <input
              id="skill-search"
              type="search"
              value={searchQuery}
              placeholder="기술스택을 검색해주세요"
              onChange={(event) => onSearchChange(event.target.value)}
            />
          </label>
          <div className="skills-popular-heading">
            <h3>{query ? "검색 결과" : showAllSkills ? "전체 기술" : "인기 기술"}</h3>
            {!query && (
              <button
                type="button"
                onClick={() => setShowAllSkills((current) => !current)}
                aria-expanded={showAllSkills}
                aria-controls="selectable-skills"
              >
                {showAllSkills ? "인기 기술 보기" : "전체 기술 보기"}
              </button>
            )}
          </div>
          <div id="selectable-skills" className="skills-tags" aria-label="선택할 수 있는 기술 스택">
            {visibleSkills.map(renderSkillTag)}
          </div>
          {visibleSkills.length === 0 && (
            <p className="skills-search-empty" role="status">검색 결과가 없습니다.</p>
          )}
        </div>
        <SelectedSkills
          skills={selectedSkills}
          onRemove={onToggleSkill}
          onClear={onClearSkills}
          maxSkills={maxSkills}
        />
      </div>
    </div>
  );
}

export default SkillSelector;
