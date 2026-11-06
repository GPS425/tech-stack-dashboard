import { useEffect, useState } from "react";
import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import { CompanyLogo, OriginalJobLink } from "../components/JobCard.jsx";
import { mockDashboard } from "../data/mockDashboard.js";
import { calculateMatchRate } from "../utils/jobMatching.js";
import { getToday } from "../utils/jobFilters.js";
import { fetchJson, toJob } from "../utils/postingApi.js";
import "./JobsPage.css";

// 마감까지 남은 날 (한국 날짜 기준). 날짜 마감이 아니면 null
function dDayLabel(job) {
  if (!job.deadline) return null;
  const days = Math.round((Date.parse(job.deadline) - Date.parse(getToday())) / 86400000);
  if (days < 0) return "마감";
  return days === 0 ? "오늘 마감" : `D-${days}`;
}

function splitSkills(skills, mine) {
  const owned = new Set(mine.map((skill) => skill.trim().toLowerCase()));
  return { have: skills.filter((skill) => owned.has(skill.trim().toLowerCase())), lack: skills.filter((skill) => !owned.has(skill.trim().toLowerCase())) };
}

const DEADLINE_TEXT = { UNTIL_FILLED: "채용시 마감", ALWAYS: "상시 채용", ROLLING: "수시 채용" };

function JobDetailPage({ jobId, profile, onBrowseJobs, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction }) {
  // key(요청한 공고 번호)와 결과를 함께 저장해, 번호가 바뀌면 자동으로 "불러오는 중"이 됩니다
  const [result, setResult] = useState({ key: null, job: null, status: null });
  const [retry, setRetry] = useState(0);
  // 내 기술을 같이 보내면 서버가 matchRate(별칭 인정)를 돌려줍니다. 서버가 아직 안 주면 화면에서 계산합니다
  const mySkillsParam = profile.skills.join(",");
  const requestKey = `${jobId}|${mySkillsParam}`;
  const loading = result.key !== requestKey;
  const job = !loading ? result.job : null;
  const closed = job?.status === "마감";
  const dDay = job ? dDayLabel(job) : null;
  const { have, lack } = job ? splitSkills(job.skills, profile.skills) : { have: [], lack: [] };
  const hasProfile = profile.skills.length > 0;

  useEffect(() => {
    const controller = new AbortController();
    const query = mySkillsParam ? `?mySkills=${encodeURIComponent(mySkillsParam)}` : "";
    fetchJson(`/api/postings/${encodeURIComponent(jobId)}${query}`, controller.signal)
      .then((data) => setResult({ key: requestKey, job: toJob(data), status: "ok" }))
      .catch((error) => { if (error.name !== "AbortError") setResult({ key: requestKey, job: null, status: error.status === 404 ? "notfound" : "error" }); });
    return () => controller.abort();
  }, [jobId, mySkillsParam, requestKey, retry]);

  function retryLoad() {
    setResult({ key: null, job: null, status: null });
    setRetry((current) => current + 1);
  }

  return (
    <div className="app-shell jobs-page">
      <Header activePage="jobs" onBrowseJobs={onBrowseJobs} onEditSkills={onEditSkills} onViewAnalysis={onViewAnalysis} onViewTrends={onViewTrends} onHomeAction={onHomeAction} />
      <main id="main-content" className="content-container jobs-detail-main">
        <button className="jobs-back-button" type="button" onClick={onBrowseJobs}><span aria-hidden="true">←</span> 채용공고 목록으로</button>
        {loading ? <p className="jobs-data-note" role="status">공고를 불러오는 중입니다…</p> : job ? <article className="jobs-detail-card">
          <div className="jobs-detail-heading">
            <div className="jobs-company"><CompanyLogo job={job} /><p>{job.company}</p></div>
            <span className={`jobs-status${closed ? " jobs-status--closed" : ""}`}>{job.status}{dDay && !closed ? ` · ${dDay}` : ""}</span>
          </div>
          <h1>{job.title}</h1>
          {job.role && <ul className="jobs-detail-roles" aria-label="직무">{job.role.split(", ").map((role) => <li key={role}>{role}</li>)}</ul>}
          <div className="jobs-detail-top-actions"><OriginalJobLink url={job.originalUrl} className="primary-button" /></div>
          {closed && <p className="jobs-closed-notice">마감된 공고입니다. 다른 채용공고도 확인해보세요.</p>}
          <dl className="jobs-detail-facts">
            <div><dt>직무</dt><dd>{job.role || "-"}</dd></div>
            <div><dt>근무 지역</dt><dd>{job.location}</dd></div>
            <div><dt>경력</dt><dd>{job.experience}</dd></div>
            <div><dt>고용 형태</dt><dd>{job.employmentType}</dd></div>
            <div><dt>학력</dt><dd>{job.education || "-"}</dd></div>
            <div><dt>접수 시작일</dt><dd>{job.postedAt ? <><time dateTime={job.postedAt}>{job.postedAt}</time>{job.postedAt > getToday() && " (접수 예정)"}</> : "-"}</dd></div>
            <div><dt>마감일</dt><dd>{job.deadline ? <time dateTime={job.deadline}>{job.deadline}</time> : DEADLINE_TEXT[job.deadlineType] || "-"}</dd></div>
          </dl>
          <section className="jobs-detail-section">
            <h2>요구 기술 <span className="jobs-detail-count">{job.skills.length}개</span></h2>
            {!job.skills.length ? <p>수집된 요구 기술이 없습니다.</p> : hasProfile ? <div className="jobs-skill-compare">
              <div><h3>내가 가진 기술 <strong>{have.length}</strong></h3>{have.length ? <div className="jobs-skill-tags jobs-skill-tags--have">{have.map((skill) => <span key={skill}>{skill}</span>)}</div> : <p>겹치는 기술이 없습니다.</p>}</div>
              <div><h3>부족한 기술 <strong>{lack.length}</strong></h3>{lack.length ? <div className="jobs-skill-tags jobs-skill-tags--lack">{lack.map((skill) => <span key={skill}>{skill}</span>)}</div> : <p>요구 기술을 모두 갖추고 있어요!</p>}</div>
            </div> : <div className="jobs-skill-tags">{job.skills.map((skill) => <span key={skill}>{skill}</span>)}</div>}
          </section>
          <p className="jobs-detail-notice">담당 업무와 자격 요건 등 공고 본문은 사람인 원문에서 확인해 주세요. 이 화면의 기술 정보는 공고에서 추출한 것이며, 내 기술과의 일치율은 기술 이름이 같은 것만 비교한 값입니다.</p>
          <div className="jobs-detail-footer">
            {profile.skills.length > 0 ? <p className="jobs-match-rate">내 기술과 {job.matchRate !== null ? job.matchRate : calculateMatchRate(job.skills, profile.skills)}% 일치</p> : <button type="button" className="jobs-back-button" onClick={onEditSkills}>내 역량을 입력하고 기술 일치율 확인하기 →</button>}
            <OriginalJobLink url={job.originalUrl} className="primary-button" />
          </div>
        </article> : <div className="jobs-empty-state" role={result.status === "error" ? "alert" : undefined}><h1>{result.status === "error" ? "공고를 불러오지 못했습니다." : "공고를 찾을 수 없습니다."}</h1><p>{result.status === "error" ? "서버가 켜져 있는지 확인하고 다시 시도해 주세요." : "주소를 확인하거나 채용공고 목록에서 다시 찾아보세요."}</p>{result.status === "error" ? <button className="primary-button" type="button" onClick={retryLoad}>다시 시도</button> : <button className="primary-button" type="button" onClick={onBrowseJobs}>채용공고 둘러보기</button>}</div>}
      </main>
      <Footer source="수집된 채용공고 데이터" copyright={mockDashboard.footer.copyright} onHomeAction={onHomeAction} />
    </div>
  );
}

export default JobDetailPage;
