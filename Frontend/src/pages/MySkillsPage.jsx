import { useEffect, useMemo, useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import StepCard from "../components/StepCard.jsx";
import JobSelect from "../components/JobSelect.jsx";
import SkillSelector from "../components/SkillSelector.jsx";
import SkillSummary from "../components/SkillSummary.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { fetchJson } from "../utils/postingApi.js";
import "./MySkillsPage.css";

const MAX_SELECTED_SKILLS = 10;

const formSteps = [
  { step: 1, label: "희망 직무 선택", id: "skills-job-step" },
  { step: 2, label: "보유 기술 선택", id: "skills-select-step" },
  { step: 3, label: "입력 내용 확인", id: "skills-confirm-step" },
];

function MySkillsPage({ profile, onProfileChange, onNavigateDashboard, onViewAnalysis, onViewTrends, onBrowseJobs }) {
  const [searchQuery, setSearchQuery] = useState("");
  const [analysisFeedback, setAnalysisFeedback] = useState("");
  const [selectorVersion, setSelectorVersion] = useState(0);
  const [apiJobs, setApiJobs] = useState([]);
  const [skillCatalog, setSkillCatalog] = useState([]);
  const [loadFailed, setLoadFailed] = useState(false);
  // 추천·인기 기술: 어느 직무(key)의 응답인지 함께 둔다. 지금 직무와 다르면 아직 불러오는 중
  const [topSkills, setTopSkills] = useState({ jobId: null, names: [] });
  const [popular, setPopular] = useState({ jobId: null, names: [] });

  // 직무·기술 목록: 처음 한 번 (직무는 id 를 jobId 문자열로 쓴다)
  useEffect(() => {
    fetchJson("/api/jobs").then(setApiJobs).catch((error) => { setLoadFailed(true); console.error("직무 목록 불러오기 실패", error); });
    fetchJson("/api/skills?limit=500").then((data) => setSkillCatalog(data.map((skill) => skill.name))).catch((error) => { setLoadFailed(true); console.error("기술 목록 불러오기 실패", error); });
  }, []);

  const skillsJobs = useMemo(() => apiJobs.map((job) => ({
    id: String(job.jobId),
    label: job.name,
    description: `${job.name} 직무입니다. 진행 중인 채용공고는 ${job.postingCount.toLocaleString("ko-KR")}건입니다.`,
  })), [apiJobs]);
  const selectedJob = skillsJobs.find((job) => job.id === String(profile.roleId));

  // 인기 기술: 공고가 가장 많은 직무의 상위 기술
  const biggestJobId = apiJobs.length ? apiJobs.reduce((best, job) => (job.postingCount > best.postingCount ? job : best)).jobId : null;
  useEffect(() => {
    if (biggestJobId === null) return undefined;
    const controller = new AbortController();
    fetchJson(`/api/jobs/${biggestJobId}/skills?limit=9`, controller.signal).then((data) => setPopular({ jobId: biggestJobId, names: data.items.map((item) => item.name) })).catch((error) => { if (error.name !== "AbortError") console.error("인기 기술 불러오기 실패", error); });
    return () => controller.abort();
  }, [biggestJobId]);
  const popularSkills = popular.jobId === biggestJobId ? popular.names : [];

  // 추천 기술: 선택한 직무의 상위 기술 (직무를 고르기 전에는 인기 기술)
  useEffect(() => {
    if (!profile.roleId) return undefined;
    const controller = new AbortController();
    fetchJson(`/api/jobs/${profile.roleId}/skills?limit=6`, controller.signal).then((data) => setTopSkills({ jobId: String(profile.roleId), names: data.items.map((item) => item.name) })).catch((error) => { if (error.name !== "AbortError") console.error("추천 기술 불러오기 실패", error); });
    return () => controller.abort();
  }, [profile.roleId]);
  const recommendations = profile.roleId ? (topSkills.jobId === String(profile.roleId) ? topSkills.names : []) : popularSkills;
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

    // 분석 화면이 profile(직무·기술)로 POST /api/analysis 를 호출합니다
    onViewAnalysis();
  }

  return (
    <div className="app-shell my-skills-page">
      <Header
        activePage="skills"
        onHomeAction={() => onNavigateDashboard()}
        onBrowseJobs={onBrowseJobs}
        onEditSkills={() => window.scrollTo(0, 0)}
        onViewAnalysis={onViewAnalysis}
        onViewTrends={onViewTrends}
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
            {loadFailed && <p className="skills-analysis-feedback" role="alert">직무·기술 목록을 불러오지 못했습니다. 서버가 켜져 있는지 확인해 주세요.</p>}
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
