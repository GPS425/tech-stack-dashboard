import { useState } from "react";
import { jobFilterGroups } from "../../data/jobFilters.js";

function JobFilterSidebar({ filters, onFilterChange, onReset }) {
  const [isOpen, setIsOpen] = useState(false);
  const [skillQuery, setSkillQuery] = useState("");
  const [showAllSkills, setShowAllSkills] = useState(false);

  function reset() {
    setSkillQuery("");
    setShowAllSkills(false);
    onReset();
  }

  return (
    <aside className="jobs-filters" aria-label="채용공고 필터">
      <button type="button" className="jobs-mobile-filter-toggle" aria-expanded={isOpen} aria-controls="jobs-filter-panel" onClick={() => setIsOpen(!isOpen)}>필터 {isOpen ? "닫기" : "열기"} <span>{Object.values(filters).flat().length}개 적용 <span aria-hidden="true">{isOpen ? "−" : "+"}</span></span></button>
      <div id="jobs-filter-panel" className={`jobs-filter-panel${isOpen ? " is-open" : ""}`}>
        <div className="jobs-filter-heading">
          <h2>필터 조건</h2>
          <button type="button" onClick={reset}><span aria-hidden="true">⟳</span> 초기화</button>
        </div>
        <div className="jobs-filter-body">
          {jobFilterGroups.map(({ key, label, options }) => {
            const visibleOptions = key !== "skills" ? options : skillQuery.trim()
              ? options.filter((skill) => skill.toLowerCase().includes(skillQuery.trim().toLowerCase()))
              : showAllSkills ? options : options.slice(0, 6);
            return (
              <fieldset className="jobs-filter-group" key={key}>
                <legend>{label}</legend>
                {key === "skills" && <label className="jobs-skill-search"><span aria-hidden="true">⌕</span><span className="sr-only">기술스택 검색</span><input type="search" placeholder="기술스택 검색" value={skillQuery} onChange={(event) => setSkillQuery(event.target.value)} /></label>}
                {key !== "skills" && <label className="jobs-checkbox"><input type="checkbox" checked={filters[key].length === 0} onChange={() => onFilterChange(key, [])} /><span>전체</span></label>}
                {visibleOptions.map((option) => <label className="jobs-checkbox" key={option}><input type="checkbox" checked={filters[key].includes(option)} onChange={() => onFilterChange(key, filters[key].includes(option) ? filters[key].filter((value) => value !== option) : [...filters[key], option])} /><span>{option}</span></label>)}
                {key === "skills" && !visibleOptions.length && <p className="jobs-filter-no-options">일치하는 기술이 없습니다.</p>}
                {key === "skills" && !skillQuery.trim() && <button className="jobs-filter-more" type="button" aria-expanded={showAllSkills} onClick={() => setShowAllSkills(!showAllSkills)}>{showAllSkills ? "접기" : "더보기"} <span aria-hidden="true">{showAllSkills ? "⌃" : "⌄"}</span></button>}
              </fieldset>
            );
          })}
        </div>
      </div>
    </aside>
  );
}

export default JobFilterSidebar;
