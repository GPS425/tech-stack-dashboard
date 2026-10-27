import { useEffect, useState } from "react";
import Header from "../components/Header.jsx";
import SummaryCard from "../components/SummaryCard.jsx";
import TechStackList from "../components/TechStackList.jsx";
import Footer from "../components/Footer.jsx";
import { mockDashboard } from "../data/mockDashboard.js";

// 기술 이름 -> 아이콘 키 (TechStackList가 아는 것만 그림, 나머지는 글자 두 개로 표시됨)
const ICONS = {
  Java: "java", Spring: "spring", AWS: "aws", SQL: "sql", Python: "python", React: "react",
  JavaScript: "javascript", TypeScript: "typescript", "Node.js": "node",
  Docker: "docker", Kubernetes: "kubernetes", Linux: "linux",
};
const iconOf = (name) => ICONS[name] || name.toLowerCase().replace(/[^a-z0-9]/g, "");

function DashboardPage({ skillProfile, onBrowseJobs, onSearchJobs, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction, initialSection = null }) {
  const [jobs, setJobs] = useState([]);
  const [selectedJobId, setSelectedJobId] = useState(1);
  const [stacks, setStacks] = useState([]);
  const [summary, setSummary] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const role = jobs.find((item) => item.jobId === selectedJobId);

  // 요약 숫자: 화면이 처음 뜰 때 한 번만 불러온다
  useEffect(() => {
    fetch("/api/summary")
      .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
      .then((data) => setSummary(data))
      .catch((error) => console.error("요약 불러오기 실패", error));
  }, []);

  // 직무 목록: 화면이 처음 뜰 때 한 번만 불러온다
  useEffect(() => {
    fetch("/api/jobs")
      .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
      .then((data) => setJobs(data))
      .catch((error) => console.error("직무 목록 불러오기 실패", error));
  }, []);

  // 기술 TOP 5: 선택한 직무가 바뀔 때마다 다시 불러온다
  useEffect(() => {
    fetch(`/api/jobs/${selectedJobId}/skills?limit=5`)
      .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
      .then((data) => setStacks((data.items || []).map((item) => ({
        name: item.name,
        icon: iconOf(item.name),
        percentage: item.percentage,
      }))))
      .catch((error) => { setStacks([]); console.error("기술 순위 불러오기 실패", error); });
  }, [selectedJobId]);

  useEffect(() => {
    const section = initialSection || window.location.hash.slice(1);
    if (section !== "tech-stacks") return;
    const frame = requestAnimationFrame(() => document.getElementById(section)?.scrollIntoView());
    return () => cancelAnimationFrame(frame);
  }, [initialSection]);

  const summaryCards = [
    { id: "postings", label: "전체 채용공고", value: summary ? summary.postingCount.toLocaleString() : "-", unit: "건" },
    { id: "stacks", label: "분석 기술 스택", value: summary ? summary.skillCount.toLocaleString() : "-", unit: "개" },
    { id: "roles", label: "분석 직무", value: summary ? summary.jobCount.toLocaleString() : "-", unit: "개" },
  ];

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
            <div className="summary-cards">{summaryCards.map((card) => <SummaryCard key={card.id} {...card} />)}</div>
          </section>
          <section className="tech-section" id="tech-stacks" aria-labelledby="tech-stacks-title">
            <div className="tech-section-heading">
              <div>
                <h2 className="section-title" id="tech-stacks-title">직무별 인기 기술스택 TOP 5</h2>
                <p className="section-description">직무별로 가장 많이 사용되는 기술스택을 확인해보세요.</p>
              </div>
              <div className="role-select-field">
                <label className="sr-only" htmlFor="job-role">직무 선택</label>
                <select id="job-role" value={selectedJobId} onChange={(event) => setSelectedJobId(Number(event.target.value))}>{jobs.map((item) => <option key={item.jobId} value={item.jobId}>{item.name}</option>)}</select>
                <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m5 6 3 4 3-4" /></svg>
              </div>
            </div>
            <TechStackList stacks={stacks} role={role ? role.name : ""} />
          </section>
        </div>
      </main>
      <Footer {...mockDashboard.footer} onHomeAction={onHomeAction} />
    </div>
  );
}

export default DashboardPage;