import { useEffect, useMemo, useRef, useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import AnalysisIcon from "../components/AnalysisIcon.jsx";
import JobCard from "../components/JobCard.jsx";
import JobsHero from "../components/jobs/JobsHero.jsx";
import JobFilterSidebar from "../components/jobs/JobFilterSidebar.jsx";
import JobPagination from "../components/jobs/JobPagination.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { createJobsState, jobFilterGroups, singleSelectKeys } from "../data/jobFilters.js";
import { buildPostingsUrl, fetchJson, toJob } from "../utils/postingApi.js";
import "./JobsPage.css";

const popularSkillNames = jobFilterGroups.find(({ key }) => key === "skills").options;

function JobsPage({ profile, state, onStateChange, onViewJob, onBrowseJobs, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction }) {
  const resultsRef = useRef(null);
  const [filterVersion, setFilterVersion] = useState(0);
  const [jobList, setJobList] = useState([]);
  const [skillNames, setSkillNames] = useState([]);
  const [result, setResult] = useState({ key: null, data: null, failed: false });
  const [retry, setRetry] = useState(0);
  const [jobsStatus, setJobsStatus] = useState("loading");   // 직무 목록: loading · done · error
  const hasProfile = profile.skills.length > 0;

  // 직무·기술 목록: 필터 선택지를 만들 때 한 번만 불러옵니다 (실패해도 목록은 그대로 동작)
  useEffect(() => {
    fetchJson("/api/jobs").then((data) => { setJobList(data); setJobsStatus("done"); }).catch((error) => { setJobsStatus("error"); console.error("직무 목록 불러오기 실패", error); });
    fetchJson("/api/skills?limit=200").then((data) => setSkillNames(data.map((skill) => skill.name))).catch((error) => console.error("기술 목록 불러오기 실패", error));
  }, []);

  const groups = useMemo(() => jobFilterGroups.map((group) => {
    // 직무 선택지는 /api/jobs 응답이 온 뒤에만 보여 줍니다 (DB 의 직무 이름과 같아야 필터가 걸림)
    if (group.key === "roles") return { ...group, options: jobList.map((job) => job.name), emptyText: jobsStatus === "error" ? "직무 목록을 불러오지 못했습니다." : jobList.length ? "" : "직무 목록을 불러오는 중…" };
    if (group.key === "skills" && skillNames.length) return { ...group, options: [...new Set([...popularSkillNames, ...skillNames])] };
    return group;
  }), [jobList, jobsStatus, skillNames]);

  // 직무 필터가 걸려 있으면 직무 목록(이름 -> 번호)이 있어야 요청을 만들 수 있습니다
  const jobsReady = state.filters.roles.length === 0 || jobList.length > 0;
  const url = useMemo(() => (jobsReady ? buildPostingsUrl(state, jobList, profile.skills) : null), [jobsReady, state, jobList, profile.skills]);

  useEffect(() => {
    if (!url) return undefined;
    const controller = new AbortController();
    fetchJson(url, controller.signal)
      .then((data) => setResult({ key: url, data, failed: false }))
      .catch((error) => { if (error.name !== "AbortError") { console.error("공고 불러오기 실패", error); setResult({ key: url, data: null, failed: true }); } });
    return () => controller.abort();
  }, [url, retry]);

  const loading = result.key !== url;
  const failed = !loading && result.failed;
  const jobs = useMemo(() => (result.data ? result.data.items.map(toJob) : []), [result.data]);
  const total = result.data?.total ?? 0;
  const pageCount = Math.ceil(total / state.pageSize);
  const pagination = {
    page: state.page,
    pageCount,
    start: total ? (state.page - 1) * state.pageSize + 1 : 0,
    end: Math.min(state.page * state.pageSize, total),
  };
  const chips = jobFilterGroups.flatMap(({ key }) => state.filters[key].map((value) => ({ key, value })));

  function retryLoad() {
    setResult({ key: null, data: null, failed: false });
    setRetry((current) => current + 1);
  }

  function updateCriteria(patch) {
    onStateChange((current) => ({ ...current, ...patch, page: 1 }));
  }

  function search(query) {
    updateCriteria({ query: query.trim(), draftQuery: query.trim() });
  }

  function changeFilter(key, values) {
    // 경력·고용 형태·지역은 API 가 하나만 받으므로 하나만 남깁니다
    const next = singleSelectKeys.includes(key) ? values.slice(-1) : values;
    onStateChange((current) => ({ ...current, filters: { ...current.filters, [key]: next }, page: 1 }));
  }

  function reset() {
    onStateChange(createJobsState());
    setFilterVersion((current) => current + 1);
  }

  function changePage(page) {
    onStateChange((current) => ({ ...current, page }));
    requestAnimationFrame(() => resultsRef.current?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth", block: "start" }));
  }

  return (
    <div className="app-shell jobs-page">
      <Header activePage="jobs" onBrowseJobs={onBrowseJobs} onEditSkills={onEditSkills} onViewAnalysis={onViewAnalysis} onViewTrends={onViewTrends} onHomeAction={onHomeAction} />
      <main id="main-content">
        <JobsHero query={state.draftQuery} onQueryChange={(draftQuery) => onStateChange((current) => ({ ...current, draftQuery }))} onSearch={search} />
        <div className="content-container jobs-layout">
          <JobFilterSidebar key={filterVersion} groups={groups} singleKeys={singleSelectKeys} filters={state.filters} onFilterChange={changeFilter} onReset={reset} />
          <section className="jobs-results" ref={resultsRef} aria-labelledby="jobs-results-title" aria-busy={loading}>
            <div className="jobs-applied-filters" aria-label="적용된 필터">
              <span className="jobs-applied-label">적용된 필터</span>
              {!chips.length && !state.query && <span className="jobs-filter-chip jobs-filter-chip--all">전체</span>}
              {state.query && <button className="jobs-filter-chip" type="button" onClick={() => search("")} aria-label={`검색어: ${state.query} 제거`}>검색어: {state.query}<span aria-hidden="true">×</span></button>}
              {chips.map(({ key, value }) => <button className="jobs-filter-chip" type="button" key={`${key}-${value}`} onClick={() => changeFilter(key, state.filters[key].filter((item) => item !== value))} aria-label={`${value} 필터 제거`}>{value}<span aria-hidden="true">×</span></button>)}
              {(chips.length > 0 || state.query) && <button className="jobs-clear-filters" type="button" onClick={reset}>전체 해제</button>}
            </div>
            <div className="jobs-toolbar">
              <h2 id="jobs-results-title" aria-live="polite" aria-atomic="true">검색 결과 <strong>{result.data ? total.toLocaleString("ko-KR") : "-"}</strong>건</h2>
              <label><span className="sr-only">공고 정렬</span><select value={!hasProfile && state.sort === "matching" ? "latest" : state.sort} onChange={(event) => updateCriteria({ sort: event.target.value })}><option value="latest">최신 등록순</option><option value="deadline">마감 임박순</option><option value="matching" disabled={!hasProfile}>내 역량 매칭순{!hasProfile ? " (역량 입력 필요)" : ""}</option></select></label>
            </div>
            <div className="jobs-recommendation">
              <AnalysisIcon name="bulb" />
              <div><p>{hasProfile ? "내 기술을 기준으로 공고를 비교하고 있어요." : "내 역량에 맞는 공고를 확인해보세요!"}</p><span>{hasProfile ? "내 역량 매칭순을 선택하면 기술 일치도가 높은 공고부터 볼 수 있습니다." : "내 역량을 입력하고 맞춤 공고를 추천받아보세요."}</span></div>
              <button type="button" onClick={onEditSkills}>내 역량 {hasProfile ? "수정" : "입력"}하기 <span aria-hidden="true">→</span></button>
            </div>
            {failed
              ? <div className="jobs-empty-state"><AnalysisIcon name="file" /><h3 role="alert">공고를 불러오지 못했습니다.</h3><p>서버가 켜져 있는지 확인하고 다시 시도해 주세요.</p><button className="primary-button" type="button" onClick={retryLoad}>다시 시도</button></div>
              : loading && !result.data
                ? <p className="jobs-data-note" role="status">공고를 불러오는 중입니다…</p>
                : jobs.length > 0
                  ? <div className="jobs-card-list" style={loading ? { opacity: 0.55 } : undefined}>{jobs.map((job) => <JobCard key={job.id} job={job} variant="listing" userSkills={profile.skills} onViewJob={onViewJob} />)}</div>
                  : <div className="jobs-empty-state"><AnalysisIcon name="file" /><h3>검색 조건에 맞는 공고가 없습니다.</h3><p>검색어나 필터 조건을 변경해서 다시 찾아보세요.</p><button className="primary-button" type="button" onClick={reset}>필터 초기화</button></div>}
            {!failed && result.data && <JobPagination pagination={pagination} total={total} pageSize={state.pageSize} onPageChange={changePage} onPageSizeChange={(pageSize) => updateCriteria({ pageSize })} />}
          </section>
        </div>
      </main>
      <Footer source="수집된 채용공고 데이터" copyright={mockDashboard.footer.copyright} onHomeAction={onHomeAction} />
    </div>
  );
}

export default JobsPage;
