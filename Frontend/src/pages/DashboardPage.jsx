import { useEffect, useState } from "react";
import Header from "../components/Header.jsx";
import SummaryCard from "../components/SummaryCard.jsx";
import TechStackList from "../components/TechStackList.jsx";
import Footer from "../components/Footer.jsx";
import { jobRoles, mockDashboard, techStacksByRole } from "../data/mockDashboard.js";

function DashboardPage({ skillProfile, onBrowseJobs, onSearchJobs, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction, initialSection = null }) {
  const [selectedRole, setSelectedRole] = useState(skillProfile.roleId || "backend");
  const [searchQuery, setSearchQuery] = useState("");
  const role = jobRoles.find((item) => item.id === selectedRole);
  const stacks = techStacksByRole[selectedRole];

  useEffect(() => {
    const section = initialSection || window.location.hash.slice(1);
    if (section !== "tech-stacks") return;
    const frame = requestAnimationFrame(() => document.getElementById(section)?.scrollIntoView());
    return () => cancelAnimationFrame(frame);
  }, [initialSection]);

  return (
    <div className="app-shell">
      <Header onBrowseJobs={onBrowseJobs} onEditSkills={onEditSkills} onViewAnalysis={onViewAnalysis} onViewTrends={onViewTrends} onHomeAction={onHomeAction} />
      <main id="main-content">
        <section className="introduction" aria-labelledby="introduction-title">
          <div className="content-container">
            <h1 id="introduction-title">{mockDashboard.introduction.title.map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</h1>
            <p className="introduction-description">{mockDashboard.introduction.description.map((line, index) => <span key={line}>{index > 0 && <br />}{line}</span>)}</p>
            <form className="job-search" role="search" onSubmit={(event) => { event.preventDefault(); onSearchJobs(searchQuery.trim()); }}>
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
    </div>
  );
}

export default DashboardPage;