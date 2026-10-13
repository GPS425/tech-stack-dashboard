import { useEffect, useRef, useState } from "react";
import Header from "../components/Header.jsx";
import SummaryCard from "../components/SummaryCard.jsx";
import TechStackList from "../components/TechStackList.jsx";
import Footer from "../components/Footer.jsx";
import { jobRoles, mockDashboard, mockJobPostings, techStacksByRole } from "../data/mockDashboard.js";

const dialogTitles = {
  jobs: "채용공고 둘러보기",
  analysis: "내 기술 분석",
  login: "로그인",
  signup: "회원가입",
};

function DashboardPage({ skillProfile, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction, initialDialog = null, initialSection = null }) {
  const [selectedRole, setSelectedRole] = useState(skillProfile.roleId || "backend");
  const [searchQuery, setSearchQuery] = useState("");
  const [jobQuery, setJobQuery] = useState("");
  const [dialog, setDialog] = useState(initialDialog);
  const mySkills = skillProfile.skills;
  const dialogRef = useRef(null);
  const role = jobRoles.find((item) => item.id === selectedRole);
  const stacks = techStacksByRole[selectedRole];
  const filteredJobs = mockJobPostings.filter((job) =>
    [job.company, job.title, job.role, ...job.stacks].join(" ").toLowerCase().includes(jobQuery.toLowerCase()),
  );
  const matchingStacks = stacks.filter((stack) =>
    mySkills.some((skill) => skill.toLowerCase() === stack.name.toLowerCase()),
  );

  useEffect(() => {
    if (dialog) {
      dialogRef.current.showModal();
    } else {
      dialogRef.current.close();
    }
  }, [dialog]);

  useEffect(() => {
    const section = initialSection || window.location.hash.slice(1);
    if (section !== "tech-stacks") return;

    const frame = requestAnimationFrame(() => {
      document.getElementById(section)?.scrollIntoView();
    });
    return () => cancelAnimationFrame(frame);
  }, [initialSection]);

  function openJobs(query = "") {
    setJobQuery(query.trim());
    setDialog("jobs");
  }

  return (
    <div className="app-shell">
      <Header
        onBrowseJobs={() => openJobs()}
        onEditSkills={onEditSkills}
        onViewAnalysis={onViewAnalysis}
        onViewTrends={onViewTrends}
        onAccountAction={setDialog}
        onHomeAction={onHomeAction}
      />

      <main id="main-content">
        <section className="introduction" aria-labelledby="introduction-title">
          <div className="content-container">
            <h1 id="introduction-title">{mockDashboard.introduction.title.map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1>
            <p className="introduction-description">{mockDashboard.introduction.description.map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</p>
            <form className="job-search" role="search" onSubmit={(event) => { event.preventDefault(); openJobs(searchQuery); }}>
              <label className="search-field">
                <span className="sr-only">채용공고 검색</span>
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10" cy="10" r="6.5" /><path d="m15 15 5 5" /></svg>
                <input type="search" placeholder={mockDashboard.introduction.searchPlaceholder} value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} />
              </label>
              <button className="primary-button browse-button" type="submit">채용공고 둘러보기 <span aria-hidden="true">→</span></button>
            </form>
          </div>
        </section>

        <div className="dashboard-content content-container">
          <section className="summary-section" aria-labelledby="summary-title">
            <h2 className="section-title" id="summary-title">분석 현황 요약</h2>
            <p className="section-description">지금까지의 분석한 채용공고와 기술스택, 직무 현황을 한번에 비교하세요.</p>
            <div className="summary-cards">{mockDashboard.summary.map((card) => <SummaryCard key={card.id} {...card} />)}</div>
          </section>

          <section className="tech-section" id="tech-stacks" aria-labelledby="tech-stacks-title">
            <div className="tech-section-heading">
              <div>
                <h2 className="section-title" id="tech-stacks-title">직무별 인기 기술스택 TOP 5</h2>
                <p className="section-description">직무별로 가장 많이 사용되는 기술스택을 확인해보세요.</p>
              </div>
              <div className="role-select-field">
                <label className="sr-only" htmlFor="job-role">직무 선택</label>
                <select id="job-role" value={selectedRole} onChange={(event) => setSelectedRole(event.target.value)}>{jobRoles.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}</select>
                <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m5 6 3 4 3-4" /></svg>
              </div>
            </div>
            <TechStackList stacks={stacks} role={role.label} />
          </section>
        </div>
      </main>

      <Footer {...mockDashboard.footer} onHomeAction={onHomeAction} />

      <dialog
        ref={dialogRef}
        className="dashboard-dialog"
        aria-labelledby="dialog-title"
        onCancel={(event) => { event.preventDefault(); setDialog(null); }}
        onClick={(event) => { if (event.target === event.currentTarget) setDialog(null); }}
      >
        <div className="dialog-heading">
          <h2 id="dialog-title">{dialogTitles[dialog]}</h2>
          <button type="button" className="dialog-close" aria-label="닫기" onClick={() => setDialog(null)}>×</button>
        </div>

        {dialog === "jobs" && (
          <div>
            <p className="dialog-description">{jobQuery ? `“${jobQuery}” 검색 결과` : "전체 채용공고"} · {filteredJobs.length}개의 샘플 공고</p>
            <div className="job-results">
              {filteredJobs.map((job) => (
                <article className="job-result" key={job.id}>
                  <p className="job-company">{job.company} · {job.role}</p>
                  <h3>{job.title}</h3>
                  <div className="skill-tags">{job.stacks.map((stack) => <span key={stack}>{stack}</span>)}</div>
                </article>
              ))}
              {filteredJobs.length === 0 && <p className="empty-state">검색 결과가 없습니다. 다른 직무, 기술스택 또는 기업명으로 검색해보세요.</p>}
            </div>
          </div>
        )}

        {dialog === "analysis" && (
          <div className="personal-analysis">
            <p className="dialog-description">{role.label}의 인기 기술스택과 나의 역량을 비교합니다.</p>
            {mySkills.length > 0 ? (
              <>
                <p className="analysis-score">TOP 5 중 <strong>{matchingStacks.length}개</strong>의 기술을 보유하고 있어요.</p>
                <div className="skill-tags">{stacks.map((stack) => <span className={matchingStacks.includes(stack) ? "skill-tag--matched" : ""} key={stack.name}>{stack.name}{matchingStacks.includes(stack) ? " ✓" : ""}</span>)}</div>
                <p className="input-help">입력한 기술: {mySkills.join(", ")}</p>
              </>
            ) : <p className="empty-state">나의 기술스택을 입력하면 비교 결과를 확인할 수 있어요.</p>}
            <button className="primary-button" type="button" onClick={onEditSkills}>내 역량 {mySkills.length ? "수정" : "입력"}하기</button>
          </div>
        )}

        {(dialog === "login" || dialog === "signup") && (
          <div>
            <p className="dialog-description">계정 기능은 준비 중입니다. 지금은 로그인 없이 채용공고와 기술 트렌드를 둘러볼 수 있어요.</p>
            <button className="primary-button" type="button" onClick={() => openJobs()}>채용공고 둘러보기</button>
          </div>
        )}
      </dialog>
    </div>
  );
}

export default DashboardPage;
