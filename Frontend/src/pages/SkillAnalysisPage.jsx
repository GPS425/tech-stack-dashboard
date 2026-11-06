import { useEffect, useMemo, useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import AnalysisIcon from "../components/AnalysisIcon.jsx";
import AnalysisSummary from "../components/AnalysisSummary.jsx";
import SkillGapComparison from "../components/SkillGapComparison.jsx";
import LearningRoadmap from "../components/LearningRoadmap.jsx";
import RecommendedJobs from "../components/RecommendedJobs.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { fetchJson } from "../utils/postingApi.js";
import { postAnalysis, toAnalysis } from "../utils/analysisApi.js";
import { useApiJobs } from "../utils/useApiJobs.js";
import "./SkillAnalysisPage.css";

function SectionHeading({ id, icon, title, description, stacked = false, children }) {
  return (
    <div className={`analysis-section-heading${stacked ? " analysis-section-heading--stacked" : ""}`}>
      <div className="analysis-section-heading-text">
        <h2 className="section-title" id={id}><AnalysisIcon name={icon} />{title}</h2>
        {stacked && <p className="analysis-section-description">{description}</p>}
      </div>
      {!stacked && description && <p className="analysis-section-description">{description}</p>}
      {children}
    </div>
  );
}

function SkillAnalysisPage({ profile, onEditSkills, onNavigateDashboard, onViewTrends, onBrowseJobs, onViewJob }) {
  const [jobList, setJobList] = useState([]);
  const [selectedRole, setSelectedRole] = useState(profile.roleId ? String(profile.roleId) : "");
  const [result, setResult] = useState({ key: null, analysis: null, status: null });
  const [jobsFailed, setJobsFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const skills = useMemo(() => profile.skills.slice(0, 10), [profile.skills]);
  const hasSkills = skills.length > 0;

  useEffect(() => {
    fetchJson("/api/jobs").then(setJobList).catch((error) => { setJobsFailed(true); console.error("직무 목록 불러오기 실패", error); });
  }, [retry]);

  // 직무를 고르지 않았으면 목록의 첫 직무로 분석합니다
  const roleId = selectedRole || (jobList.length ? String(jobList[0].jobId) : "");
  const requestKey = hasSkills && roleId ? `${roleId}|${skills.join(",")}` : null;

  useEffect(() => {
    if (!requestKey) return undefined;
    const controller = new AbortController();
    postAnalysis(roleId, skills, controller.signal)
      .then((data) => setResult({ key: requestKey, analysis: toAnalysis(data, skills), status: "ok" }))
      .catch((error) => { if (error.name !== "AbortError") { console.error("분석 불러오기 실패", error); setResult({ key: requestKey, analysis: null, status: "error" }); } });
    return () => controller.abort();
  }, [requestKey, roleId, skills, retry]);

  const analysis = requestKey && result.key === requestKey ? result.analysis : null;
  const failed = (Boolean(requestKey) && result.key === requestKey && result.status === "error") || (hasSkills && !roleId && jobsFailed);

  function retryLoad() {
    setJobsFailed(false);
    setResult({ key: null, analysis: null, status: null });
    setRetry((current) => current + 1);
  }

  // 추천 공고: 선택한 직무에서 내 기술과 일치율이 높은 공고
  const recommendedUrl = requestKey ? `/api/postings?jobIds=${roleId}&sort=matching&mySkills=${encodeURIComponent(skills.join(","))}&size=9` : null;
  const recommended = useApiJobs(recommendedUrl, 3);

  return (
    <div className="app-shell skill-analysis-page">
      <Header
        activePage="analysis"
        onHomeAction={() => onNavigateDashboard()}
        onBrowseJobs={onBrowseJobs}
        onEditSkills={onEditSkills}
        onViewAnalysis={() => window.scrollTo(0, 0)}
        onViewTrends={onViewTrends}
      />
      <main id="main-content">
        <section className="introduction analysis-introduction" aria-labelledby="skill-analysis-title">
          <div className="content-container analysis-hero-inner">
            <div className="analysis-hero-copy">
              <h1 id="skill-analysis-title">내 기술 분석</h1>
              <p className="introduction-description">선택한 직무와 보유 기술을 바탕으로 맞춤형 분석 결과를 확인해보세요.</p>
              <p className="analysis-profile-note">{hasSkills ? "입력한 역량 기준" : "입력한 역량 없음"}<span aria-hidden="true"> · </span><button type="button" onClick={onEditSkills}>내 역량 {hasSkills ? "수정" : "입력"}하기 <span aria-hidden="true">→</span></button></p>
              {analysis?.unknownSkills.length > 0 && <p className="analysis-profile-note">분석에서 인식하지 못한 기술: {analysis.unknownSkills.join(", ")}</p>}
            </div>
            <div className="analysis-role-select">
              <label htmlFor="analysis-job-role">분석할 희망 직무</label>
              <div className="role-select-field">
                <select id="analysis-job-role" value={roleId} onChange={(event) => setSelectedRole(event.target.value)}>{jobList.map((job) => <option key={job.jobId} value={String(job.jobId)}>{job.name}</option>)}</select>
                <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m5 6 3 4 3-4" /></svg>
              </div>
            </div>
            <svg className="analysis-hero-illustration" viewBox="0 0 160 128" aria-hidden="true">
              <ellipse cx="88" cy="70" rx="65" ry="48" fill="#dff3e8" />
              <path d="m23 33-12-7m22-3-6-15" stroke="#a3dcb6" strokeWidth="4" strokeLinecap="round" />
              <rect x="52" y="13" width="80" height="106" rx="9" fill="#c6e8d7" />
              <rect x="48" y="10" width="80" height="106" rx="9" fill="#fff" />
              <path d="M59 29h54M59 42h35" stroke="#c6e8d7" strokeWidth="5" />
              <path d="M64 100V85m14 15V76m14 24V66" stroke="#77cba7" strokeWidth="8" />
              <circle cx="118" cy="72" r="18" fill="#fff" fillOpacity=".8" stroke="#078b65" strokeWidth="5" />
              <path d="m131 86 15 16" stroke="#078b65" strokeWidth="7" strokeLinecap="round" />
              <circle cx="151" cy="116" r="7" fill="#c6e8d7" />
            </svg>
          </div>
        </section>

        {!analysis && <section className="analysis-section" aria-live="polite">
          <div className="content-container">
            {!hasSkills ? <p className="analysis-jobs-note">내 역량(희망 직무와 보유 기술)을 입력하면 분석 결과를 볼 수 있어요. <button type="button" className="analysis-all-jobs" onClick={onEditSkills}>내 역량 입력하기 <span aria-hidden="true">→</span></button></p>
              : failed ? <p className="analysis-jobs-note" role="alert">분석 결과를 불러오지 못했습니다. 서버가 켜져 있는지 확인하세요. <button type="button" className="analysis-all-jobs" onClick={retryLoad}>다시 시도</button></p>
              : <p className="analysis-jobs-note" role="status">분석 중입니다…</p>}
          </div>
        </section>}

        {analysis && <>
        <section className="analysis-section" aria-labelledby="analysis-summary-title">
          <div className="content-container">
            <SectionHeading id="analysis-summary-title" icon="briefcase" title="한눈에 보는 분석 결과">
              <p className="analysis-growth-note">{analysis.fitScore === 100 ? "보유 기술을 프로젝트에서 펼쳐보세요!" : "당신의 성장 가능성이 충분합니다!"}<AnalysisIcon name="sprout" /></p>
            </SectionHeading>
            <AnalysisSummary analysis={analysis} />
          </div>
        </section>

        <section className="analysis-section" aria-labelledby="analysis-comparison-title">
          <div className="content-container">
            <SectionHeading id="analysis-comparison-title" icon="chart" title="채용시장 요구 기술과 내 기술 비교" description="시장에서 요구하는 기술과 내 기술을 비교하여 부족한 부분을 확인하세요." />
            <SkillGapComparison analysis={analysis} />
          </div>
        </section>

        <section className="analysis-section" aria-labelledby="analysis-learning-title">
          <div className="content-container">
            <SectionHeading id="analysis-learning-title" icon="book" title="우선 보완할 기술 및 학습 방향" description="부족한 기술 중 우선순위가 높은 순서로 학습 방향을 제시합니다." stacked />
            <LearningRoadmap items={analysis.learningRoadmap} />
          </div>
        </section>
        </>}

        <section className="analysis-section" aria-labelledby="analysis-jobs-title">
          <div className="content-container">
            <SectionHeading id="analysis-jobs-title" icon="file" title="내 역량에 맞는 채용 공고" description="지금 바로 지원할 수 있는 추천 채용 공고입니다." stacked>
              <button className="analysis-all-jobs" type="button" onClick={onBrowseJobs}>전체 채용공고 보기 <span aria-hidden="true">→</span></button>
            </SectionHeading>
            <RecommendedJobs jobs={recommended.jobs} loading={recommended.loading} failed={recommended.failed} onRetry={recommended.retry} onViewJob={onViewJob} emptyText={hasSkills ? "이 직무에서 표시할 추천 공고가 없습니다." : "내 역량을 입력하면 맞춤 공고를 추천해 드려요."} />
          </div>
        </section>
      </main>
      <Footer {...mockDashboard.footer} onHomeAction={() => onNavigateDashboard()} />
    </div>
  );
}

export default SkillAnalysisPage;
