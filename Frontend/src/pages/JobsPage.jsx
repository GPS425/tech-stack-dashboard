import { useMemo, useRef, useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import AnalysisIcon from "../components/AnalysisIcon.jsx";
import JobCard from "../components/JobCard.jsx";
import JobsHero from "../components/jobs/JobsHero.jsx";
import JobFilterSidebar from "../components/jobs/JobFilterSidebar.jsx";
import JobPagination from "../components/jobs/JobPagination.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { mockJobs } from "../data/mockJobs.js";
import { createJobsState, jobFilterGroups } from "../data/jobFilters.js";
import { filterJobs, sortJobs } from "../utils/jobFilters.js";
import { paginate } from "../utils/pagination.js";
import "./JobsPage.css";

function JobsPage({ profile, state, onStateChange, onViewJob, onBrowseJobs, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction }) {
  const resultsRef = useRef(null);
  const [filterVersion, setFilterVersion] = useState(0);
  const hasProfile = profile.skills.length > 0;
  const jobs = useMemo(() => sortJobs(filterJobs(mockJobs, state.query, state.filters), state.sort, profile.skills), [state.query, state.filters, state.sort, profile.skills]);
  const pagination = paginate(jobs, state.page, state.pageSize);
  const chips = jobFilterGroups.flatMap(({ key }) => state.filters[key].map((value) => ({ key, value })));

  function updateCriteria(patch) {
    onStateChange((current) => ({ ...current, ...patch, page: 1 }));
  }

  function search(query) {
    updateCriteria({ query: query.trim(), draftQuery: query.trim() });
  }

  function changeFilter(key, values) {
    onStateChange((current) => ({ ...current, filters: { ...current.filters, [key]: values }, page: 1 }));
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
          <JobFilterSidebar key={filterVersion} filters={state.filters} onFilterChange={changeFilter} onReset={reset} />
          <section className="jobs-results" ref={resultsRef} aria-labelledby="jobs-results-title">
            <div className="jobs-applied-filters" aria-label="적용된 필터">
              <span className="jobs-applied-label">적용된 필터</span>
              {!chips.length && !state.query && <span className="jobs-filter-chip jobs-filter-chip--all">전체</span>}
              {state.query && <button className="jobs-filter-chip" type="button" onClick={() => search("")} aria-label={`검색어: ${state.query} 제거`}>검색어: {state.query}<span aria-hidden="true">×</span></button>}
              {chips.map(({ key, value }) => <button className="jobs-filter-chip" type="button" key={`${key}-${value}`} onClick={() => changeFilter(key, state.filters[key].filter((item) => item !== value))} aria-label={`${value} 필터 제거`}>{value}<span aria-hidden="true">×</span></button>)}
              {(chips.length > 0 || state.query) && <button className="jobs-clear-filters" type="button" onClick={reset}>전체 해제</button>}
            </div>
            <div className="jobs-toolbar">
              <h2 id="jobs-results-title" aria-live="polite" aria-atomic="true">검색 결과 <strong>{jobs.length}</strong>건</h2>
              <label><span className="sr-only">공고 정렬</span><select value={!hasProfile && state.sort === "matching" ? "latest" : state.sort} onChange={(event) => updateCriteria({ sort: event.target.value })}><option value="latest">최신 등록순</option><option value="deadline">마감 임박순</option><option value="matching" disabled={!hasProfile}>내 역량 매칭순{!hasProfile ? " (역량 입력 필요)" : ""}</option></select></label>
            </div>
            <div className="jobs-recommendation">
              <AnalysisIcon name="bulb" />
              <div><p>{hasProfile ? "내 기술을 기준으로 공고를 비교하고 있어요." : "내 역량에 맞는 공고를 확인해보세요!"}</p><span>{hasProfile ? "내 역량 매칭순을 선택하면 기술 일치도가 높은 공고부터 볼 수 있습니다." : "내 역량을 입력하고 맞춤 공고를 추천받아보세요."}</span></div>
              <button type="button" onClick={onEditSkills}>내 역량 {hasProfile ? "수정" : "입력"}하기 <span aria-hidden="true">→</span></button>
            </div>
            <p className="jobs-data-note">화면 확인을 위한 mock 공고입니다. 원문 링크는 예시 주소입니다.</p>
            {jobs.length > 0 ? <div className="jobs-card-list">{pagination.items.map((job) => <JobCard key={job.id} job={job} variant="listing" userSkills={profile.skills} onViewJob={onViewJob} />)}</div> : <div className="jobs-empty-state"><AnalysisIcon name="file" /><h3>검색 조건에 맞는 공고가 없습니다.</h3><p>검색어나 필터 조건을 변경해서 다시 찾아보세요.</p><button className="primary-button" type="button" onClick={reset}>필터 초기화</button></div>}
            <JobPagination pagination={pagination} total={jobs.length} pageSize={state.pageSize} onPageChange={changePage} onPageSizeChange={(pageSize) => updateCriteria({ pageSize })} />
          </section>
        </div>
      </main>
      <Footer source="수집된 채용공고 데이터" copyright={mockDashboard.footer.copyright} onHomeAction={onHomeAction} />
    </div>
  );
}

export default JobsPage;
