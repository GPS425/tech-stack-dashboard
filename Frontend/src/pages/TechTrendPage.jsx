import { useEffect, useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import AnalysisIcon from "../components/AnalysisIcon.jsx";
import TechStackList from "../components/TechStackList.jsx";
import RecommendedJobs from "../components/RecommendedJobs.jsx";
import { useApiJobs } from "../utils/useApiJobs.js";
import MarketSummary from "../components/MarketSummary.jsx";
import JobDemandOverview from "../components/JobDemandOverview.jsx";
import JobSkillHeatmap from "../components/JobSkillHeatmap.jsx";
import TechCombinationList from "../components/TechCombinationList.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { trendJobs, getMockTechTrend } from "../data/mockTechTrend.js";
import "./TechTrendPage.css";

// 기술 이름 -> 아이콘 키 (TechStackList가 아는 것만 그림, 나머지는 글자 두 개로 표시됨)
const ICONS = {
  Java: "java", Spring: "spring", AWS: "aws", SQL: "sql", Python: "python", React: "react",
  JavaScript: "javascript", TypeScript: "typescript", "Node.js": "node",
  Docker: "docker", Kubernetes: "kubernetes", Linux: "linux",
};
// 위쪽 직무 필터 id -> 실제 직무 이름(/api/jobs 의 name). AI 엔지니어는 DB 에 없는 직무라 넣지 않는다
const FILTER_JOB_NAME = { backend: "백엔드/서버개발", frontend: "프론트엔드", "data-analyst": "데이터분석가", fullstack: "풀스택" };
const iconOf = (name) => ICONS[name] || name.toLowerCase().replace(/[^a-z0-9]/g, "");

function TrendHeading({ id, icon, title, description, children }) {
  return (
    <div className="trend-panel-heading">
      <div className="trend-heading-copy">
        <h2 className="section-title" id={id}><AnalysisIcon name={icon} />{title}</h2>
        <p>{description}</p>
      </div>
      {children}
    </div>
  );
}

function TechTrendPage({ onEditSkills, onViewAnalysis, onNavigateDashboard, onBrowseJobs, onViewJob }) {
  const [selectedJob, setSelectedJob] = useState("all");
  const mock = getMockTechTrend(selectedJob);
  const [summary, setSummary] = useState(null);
  const [demand, setDemand] = useState({ calcDate: "", items: [] });
  const [jobs, setJobs] = useState([]);
  const [rankingJobId, setRankingJobId] = useState(1);
  const [stacks, setStacks] = useState([]);
  const [matrix, setMatrix] = useState({ calcDate: "", jobs: [], rows: [] });
  const [jobsStatus, setJobsStatus] = useState("loading");   // 직무 목록: loading · done · error
  // 기술 조합: 어느 필터(jobId)의 응답인지 함께 둔다. 지금 필터와 다르면 아직 불러오는 중
  const [combinations, setCombinations] = useState({ jobId: undefined, status: "loading", items: [] });

  // 관련 공고: 선택한 직무의 마감 임박 공고 (전체면 직무 조건 없이). 직무 번호는 /api/jobs 에서 이름으로 찾는다
  const relatedJobName = FILTER_JOB_NAME[selectedJob];
  const relatedJobId = relatedJobName ? jobs.find((item) => item.name === relatedJobName)?.jobId : undefined;
  const relatedUrl = selectedJob === "all" ? "/api/postings?size=9&sort=deadline" : relatedJobId !== undefined ? `/api/postings?jobIds=${relatedJobId}&size=9&sort=deadline` : null;
  const related = useApiJobs(relatedUrl, 3);
  const relatedLoading = related.loading || (Boolean(relatedJobName) && jobsStatus === "loading");

  // 요약 숫자 / 직무별 공고 수 / 직무 목록: 화면이 처음 뜰 때 한 번만 불러온다
  useEffect(() => {
    fetch("/api/summary").then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); }).then(setSummary).catch((e) => console.error("요약 불러오기 실패", e));
    fetch("/api/trends/job-demand").then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); }).then(setDemand).catch((e) => console.error("직무 수요 불러오기 실패", e));
    fetch("/api/jobs").then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); }).then((data) => { setJobs(data); setJobsStatus("done"); }).catch((e) => { setJobsStatus("error"); console.error("직무 목록 불러오기 실패", e); });
  }, []);

  // 인기 기술 TOP 5: 선택한 직무가 바뀔 때마다 다시 불러온다
  useEffect(() => {
    fetch(`/api/jobs/${rankingJobId}/skills?limit=5`)
      .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
      .then((data) => setStacks((data.items || []).map((item) => ({ name: item.name, icon: iconOf(item.name), percentage: item.percentage }))))
      .catch((e) => { setStacks([]); console.error("기술 순위 불러오기 실패", e); });
  }, [rankingJobId]);

  // 직무별 핵심 기술 비교: 백엔드·프론트엔드·데이터분석가 3개 직무로 고정(임시), 화면이 처음 뜰 때 한 번만 불러온다
  useEffect(() => {
    fetch("/api/trends/matrix?jobIds=1,23,35&limit=6")
      .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
      .then(setMatrix)
      .catch((e) => { setMatrix({ calcDate: "", jobs: [], rows: [] }); console.error("핵심 기술 비교 불러오기 실패", e); });
  }, []);

  // 기술 조합 TOP 5: 직무 필터가 바뀔 때마다 다시 불러온다.
  // filterJobId: null = 전체(jobId 없이), 숫자 = 그 직무, undefined = 직무 목록이 아직 없거나 DB 에 없는 직무(AI 엔지니어)
  const filterJobId = selectedJob === "all" ? null : jobs.find((item) => item.name === FILTER_JOB_NAME[selectedJob])?.jobId;
  // 위쪽 직무 탭을 바꾸면 인기 기술 TOP 5 의 직무도 같이 바꾼다(전체일 때는 그대로 둔다). 아래 드롭다운으로 다른 직무를 직접 고를 수도 있다
  useEffect(() => { if (typeof filterJobId === "number") setRankingJobId(filterJobId); }, [filterJobId]);
  // filterJobId 가 undefined 일 때 표에 보일 상태: 직무 목록을 받는 중 / 못 받음 / 받았는데 그 직무가 없음(unavailable)
  const combinationsBlocked = filterJobId !== undefined ? null : jobsStatus === "loading" ? "loading" : jobsStatus === "error" ? "error" : "unavailable";
  useEffect(() => {
    if (filterJobId === undefined) return;
    let cancelled = false;   // 필터를 또 바꿔서 이 요청이 낡아지면 결과를 버린다
    const url = filterJobId === null ? "/api/trends/combinations?limit=5" : `/api/trends/combinations?limit=5&jobId=${filterJobId}`;
    fetch(url)
      .then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); })
      .then((data) => { if (!cancelled) setCombinations({ jobId: filterJobId, status: "done", items: (data.items || []).map((item) => ({ skills: item.skills, count: item.postingCount })) }); })
      .catch((e) => { if (!cancelled) setCombinations({ jobId: filterJobId, status: "error", items: [] }); console.error("기술 조합 불러오기 실패", e); });
    return () => { cancelled = true; };
  }, [filterJobId]);
  const combinationsStatus = combinationsBlocked || (combinations.jobId === filterJobId ? combinations.status : "loading");

  // 수요 1위 직무의 1위 기술 (요약 카드용)
  const [topSkill, setTopSkill] = useState("-");
  const topJobId = demand.items.reduce((best, item) => (!best || item.postingCount > best.postingCount ? item : best), null)?.jobId;
  useEffect(() => {
    if (!topJobId) return;
    fetch(`/api/jobs/${topJobId}/skills?limit=1`).then((res) => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return res.json(); }).then((data) => setTopSkill(data.items?.[0]?.name || "-")).catch((e) => console.error(e));
  }, [topJobId]);

  // 응답(jobs, rows)을 표 컴포넌트가 기대하는 모양(columns, rows)으로 바꾼다
  const heatmap = {
    columns: matrix.jobs.map((job) => ({ id: job.jobId, label: job.name })),
    rows: matrix.rows.map((row) => ({ skill: row.name, values: row.values })),
  };
  const jobDemand = demand.items.map((item) => ({ id: item.jobId, label: item.name, count: item.postingCount, percentage: item.percentage }));
  const topDemand = demand.items.reduce((best, item) => (!best || item.postingCount > best.postingCount ? item : best), null);
  const rankingJob = jobs.find((item) => item.jobId === rankingJobId);
  const analysisDate = (summary && summary.calcDate) || mock.metadata.analysisDate;
  const marketSummary = [
    { id: "postings", label: "분석 채용공고", value: summary ? summary.postingCount : "-", unit: "건", icon: "file", description: "현재 보유한 채용공고 수입니다." },
    { id: "skills", label: "분석 기술스택", value: summary ? summary.skillCount : "-", unit: "개", icon: "code", description: "채용공고에서 추출한 기술스택 수입니다." },
    { id: "job", label: "수요 1위 직무", value: topDemand ? topDemand.name : "-", unit: "", icon: "trophy", description: "가장 많은 채용공고가 등록된 직무입니다." },
    { id: "technology", label: "요구 1위 기술", value: topSkill, unit: "", icon: "star", description: "수요 1위 직무에서 가장 많이 요구되는 기술입니다." },
  ];
  const trend = mock;
  const dateLabel = `${analysisDate.replaceAll("-", ". ")}.`;

  return (
    <div className="app-shell tech-trend-page">
      <Header
        activePage="trends"
        onHomeAction={() => onNavigateDashboard()}
        onBrowseJobs={onBrowseJobs}
        onEditSkills={onEditSkills}
        onViewAnalysis={onViewAnalysis}
        onViewTrends={() => window.scrollTo(0, 0)}
      />
      <main id="main-content">
        <section className="introduction trend-introduction" aria-labelledby="tech-trend-title">
          <div className="content-container trend-hero-inner">
            <div className="trend-hero-copy">
              <h1 id="tech-trend-title">기술 트렌드</h1>
              <p className="introduction-description">수집된 채용공고 데이터를 기반으로<br />직무별 기술 수요와 주요 기술스택을 확인해보세요.</p>
            </div>
            <div className="trend-data-context">
              <div className="trend-data-card">
                <div><AnalysisIcon name="database" /><dl><dt>분석 기준</dt><dd><time dateTime={analysisDate}>{dateLabel}</time></dd></dl></div>
                <div><AnalysisIcon name="file" /><dl><dt>분석 채용공고</dt><dd>{summary ? summary.postingCount.toLocaleString("ko-KR") : "-"}건</dd></dl></div>
              </div>
              <p>※ 현재 보유한 채용공고 데이터를 기반으로 분석한 결과입니다.</p>
            </div>
            <svg className="trend-hero-illustration" viewBox="0 0 150 120" aria-hidden="true">
              <circle cx="90" cy="45" r="43" fill="#e0f4e8" />
              <path d="M70 99h25m-12-9v9" stroke="#b6e3cd" strokeWidth="7" strokeLinecap="round" />
              <rect x="35" y="18" width="91" height="68" rx="6" fill="#b6e3cd" />
              <rect x="40" y="23" width="81" height="58" rx="3" fill="#fff" />
              <path d="M50 35h30m-30 7h18" stroke="#d1efe1" strokeWidth="4" />
              <path d="M56 69V58m15 11V51m15 18V43" stroke="#7acda8" strokeWidth="7" />
              <circle cx="115" cy="70" r="15" fill="#fff" fillOpacity=".8" stroke="#078b65" strokeWidth="4" />
              <path d="m126 81 12 13" stroke="#078b65" strokeWidth="6" strokeLinecap="round" />
              <path d="m21 39 4 4m-4 0 4-4m8-30 3 3m-3 0 3-3m99 25 3 3m-3 0 3-3" stroke="#b6e3cd" strokeWidth="3" strokeLinecap="round" />
            </svg>
          </div>
        </section>

        <section className="trend-filter-section" aria-labelledby="trend-filter-title">
          <div className="content-container trend-filter-inner">
            <h2 id="trend-filter-title">직무 필터링</h2>
            <div className="trend-job-filters" role="group" aria-labelledby="trend-filter-title">
              {trendJobs.filter((job) => job.id === "all" || FILTER_JOB_NAME[job.id]).map((job) => <button type="button" className={job.id === selectedJob ? "is-selected" : undefined} key={job.id} aria-pressed={job.id === selectedJob} onClick={() => setSelectedJob(job.id)}>{job.label}</button>)}
            </div>
          </div>
        </section>

        <section className="trend-summary-section" aria-labelledby="trend-summary-title">
          <div className="content-container">
            <TrendHeading id="trend-summary-title" icon="chart" title="시장 현황 요약" description="현재 수집된 채용공고 데이터에서 확인할 수 있는 주요 지표입니다.">
              <span className="trend-global-label">전체 수집 데이터 기준</span>
            </TrendHeading>
            <MarketSummary items={marketSummary} />
          </div>
        </section>

        <div className="content-container trend-panels">
          <section className="trend-panel" aria-labelledby="trend-demand-title">
            <TrendHeading id="trend-demand-title" icon="chart" title="직무별 채용공고 현황" description="각 직무별 채용공고 수와 전체 대비 비율을 확인할 수 있습니다." />
            <JobDemandOverview jobs={jobDemand} selectedJob={selectedJob} />
          </section>
          <section className="trend-panel trend-ranking-panel" aria-labelledby="trend-ranking-title">
            <TrendHeading id="trend-ranking-title" icon="trophy" title="직무별 인기 기술 TOP 5" description="선택한 직무에서 가장 많이 요구되는 기술스택을 확인해보세요.">
              <div className="role-select-field trend-ranking-select">
                <label className="sr-only" htmlFor="trend-ranking-role">인기 기술을 확인할 직무</label>
                <select id="trend-ranking-role" value={rankingJobId} onChange={(event) => setRankingJobId(Number(event.target.value))}>{jobs.map((job) => <option key={job.jobId} value={job.jobId}>{job.name}</option>)}</select>
                <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m5 6 3 4 3-4" /></svg>
              </div>
            </TrendHeading>
            <TechStackList stacks={stacks} role={rankingJob ? rankingJob.name : ""} />
          </section>
          <section className="trend-panel" aria-labelledby="trend-heatmap-title">
            <TrendHeading id="trend-heatmap-title" icon="grid" title="직무별 핵심 기술 비교" description="주요 기술이 각 직무에서 얼마나 요구되는지 비교할 수 있습니다." />
            <JobSkillHeatmap matrix={heatmap} selectedJob={selectedJob} />
          </section>
          <section className="trend-panel" aria-labelledby="trend-combination-title">
            <TrendHeading id="trend-combination-title" icon="link" title="기술 조합 TOP 5" description="채용공고에서 함께 언급되는 기술 조합을 확인해보세요." />
            <TechCombinationList status={combinationsStatus} combinations={combinationsStatus === "done" ? combinations.items : []} />
          </section>
        </div>

        <section className="trend-related-section" aria-labelledby="trend-related-title">
          <div className="content-container">
            <TrendHeading id="trend-related-title" icon="briefcase" title="관련 채용 공고" description="선택한 직무의 주요 기술스택과 관련된 채용 공고를 마감이 임박한 순서로 확인해보세요.">
              <button className="trend-all-jobs" type="button" onClick={onBrowseJobs}>전체 채용공고 보기 <span aria-hidden="true">→</span></button>
            </TrendHeading>
            <p className="sr-only" role="status" aria-live="polite">{selectedJob === "all" ? "전체 직무" : trend.selectedJob.label} 기준 기술 조합과 관련 공고, {rankingJob ? rankingJob.name : ""} 인기 기술을 표시합니다.</p>
            <RecommendedJobs jobs={related.jobs} loading={relatedLoading} failed={related.failed} onRetry={related.retry} onViewJob={onViewJob} emptyText="이 직무로 표시할 채용공고가 없습니다." />
          </div>
        </section>
      </main>
      <Footer source={trend.metadata.source} copyright={mockDashboard.footer.copyright} onHomeAction={() => onNavigateDashboard()} />
    </div>
  );
}

export default TechTrendPage;
