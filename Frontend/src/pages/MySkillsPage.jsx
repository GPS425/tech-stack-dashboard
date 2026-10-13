import { useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import StepCard from "../components/StepCard.jsx";
import JobSelect from "../components/JobSelect.jsx";
import SkillSelector from "../components/SkillSelector.jsx";
import SkillSummary from "../components/SkillSummary.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { MAX_SELECTED_SKILLS, skillsJobs, popularSkills, skillCatalog, recommendedSkillsByRole } from "../data/mockSkills.js";
import "./MySkillsPage.css";

const formSteps = [
  { step: 1, label: "희망 직무 선택", id: "skills-job-step" },
  { step: 2, label: "보유 기술 선택", id: "skills-select-step" },
  { step: 3, label: "입력 내용 확인", id: "skills-confirm-step" },
];

function MySkillsPage({ profile, onProfileChange, onNavigateDashboard, onViewAnalysis, onViewTrends }) {
  const [searchQuery, setSearchQuery] = useState("");
  const [analysisFeedback, setAnalysisFeedback] = useState("");
  const [selectorVersion, setSelectorVersion] = useState(0);
  const selectedJob = skillsJobs.find((job) => job.id === profile.roleId);
  const recommendations = recommendedSkillsByRole[profile.roleId] || popularSkills;
  const selectionLimitReached = profile.skills.length >= MAX_SELECTED_SKILLS;
  const currentStep = !profile.roleId ? 1 : profile.skills.length === 0 ? 2 : 3;

  function changeJob(roleId) {
    onProfileChange((current) => ({ ...current, roleId }));
    setAnalysisFeedback("");
  }

  function toggleSkill(skill) {
    onProfileChange((current) => {
      if (current.skills.includes(skill)) {
        return { ...current, skills: current.skills.filter((item) => item !== skill) };
      }
      if (current.skills.length >= MAX_SELECTED_SKILLS) return current;
      return { ...current, skills: [...current.skills, skill] };
    });
    setAnalysisFeedback("");
  }

  function clearSkills() {
    onProfileChange((current) => ({ ...current, skills: [] }));
    setAnalysisFeedback("");
  }

  function resetForm() {
    onProfileChange({ roleId: "", skills: [] });
    setSearchQuery("");
    setAnalysisFeedback("");
    setSelectorVersion((current) => current + 1);
  }

  function handleAnalyzeSkills() {
    if (!profile.roleId) {
      setAnalysisFeedback("희망 직무를 선택해주세요.");
      document.getElementById("desired-job")?.focus();
      return;
    }
    if (profile.skills.length === 0) {
      setAnalysisFeedback("보유 기술 스택을 1개 이상 선택해주세요.");
      document.getElementById("skill-search")?.focus();
      return;
    }

    // 실제 분석 API를 연결할 때 이 함수에서 아래 입력 정보를 전달합니다.
    const analysisInput = { jobId: profile.roleId, skills: [...profile.skills] };
    onViewAnalysis();
    return analysisInput;
  }

  return (
    <div className="app-shell my-skills-page">
      <Header
        activePage="skills"
        onHomeAction={() => onNavigateDashboard()}
        onBrowseJobs={() => onNavigateDashboard("jobs")}
        onEditSkills={() => window.scrollTo(0, 0)}
        onViewAnalysis={onViewAnalysis}
        onViewTrends={onViewTrends}
        onAccountAction={(action) => onNavigateDashboard(action)}
      />

      <main id="main-content">
        <section className="introduction skills-introduction" aria-labelledby="my-skills-title">
          <div className="content-container">
            <h1 id="my-skills-title">내 역량을 입력하세요</h1>
            <p className="introduction-description">
              희망 직무와 보유 기술을 선택하면,<br />
              나의 역량을 시장 요구와 비교해 분석할 수 있습니다.
            </p>
          </div>
        </section>

        <div className="content-container skills-content">
          <nav className="skills-progress" aria-label="역량 입력 순서">
            <ol>
              {formSteps.map(({ step, label, id }) => (
                <li key={step} data-complete={step < currentStep}>
                  <a href={`#${id}`} aria-current={step === currentStep ? "step" : undefined}>
                    <span className="skills-progress-number" aria-hidden="true">{step}</span>
                    <span>{label}</span>
                  </a>
                </li>
              ))}
            </ol>
          </nav>

          <StepCard id="skills-job-step" step={1} title="희망 직무 선택" description="분석하고 싶은 희망 직무를 선택해주세요.">
            <div className="skills-job-layout">
              <JobSelect jobs={skillsJobs} value={profile.roleId} onChange={changeJob} />
              <aside className="skills-job-description" aria-labelledby="skills-job-description-title">
                <h3 id="skills-job-description-title">직무 안내</h3>
                <p>{selectedJob?.description || "희망 직무를 선택하면 어떤 일을 하는 직무인지 확인할 수 있어요."}</p>
              </aside>
            </div>
          </StepCard>

          <StepCard id="skills-select-step" step={2} title="보유 기술 선택" description="보유하고 있는 기술을 선택해주세요. (최대 10개)" className="skills-selection-step">
            <SkillSelector
              key={selectorVersion}
              skills={skillCatalog}
              popularSkills={popularSkills}
              recommendedSkills={recommendations}
              jobLabel={selectedJob?.label}
              selectedSkills={profile.skills}
              onToggleSkill={toggleSkill}
              onClearSkills={clearSkills}
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              maxSkills={MAX_SELECTED_SKILLS}
            />
            {selectionLimitReached && <p className="skills-limit-message" role="status">기술 스택은 최대 10개까지 선택할 수 있습니다. 다른 기술을 선택하려면 기존 기술을 해제해주세요.</p>}
          </StepCard>

          <StepCard id="skills-confirm-step" step={3} title="입력 내용 확인" description="선택한 내용을 확인하고 역량 분석을 진행해주세요." className="skills-confirm-step">
            <SkillSummary jobLabel={selectedJob?.label} skills={profile.skills} />
            <div className="skills-form-actions">
              <button className="skills-reset-button" type="button" onClick={resetForm}>초기화</button>
              <button className="primary-button skills-analysis-button" type="button" onClick={handleAnalyzeSkills}>역량 분석하기 <span aria-hidden="true">→</span></button>
            </div>
            <p className="skills-analysis-feedback" role="status" aria-live="polite">{analysisFeedback}</p>
          </StepCard>
        </div>
      </main>

      <Footer {...mockDashboard.footer} onHomeAction={() => onNavigateDashboard()} />
    </div>
  );
}

export default MySkillsPage;
