import Header from "../components/Header.jsx";
import Footer from "../components/Footer.jsx";
import { CompanyLogo, OriginalJobLink } from "../components/JobCard.jsx";
import { mockJobs } from "../data/mockJobs.js";
import { mockDashboard } from "../data/mockDashboard.js";
import { calculateMatchRate } from "../utils/jobMatching.js";
import { isJobClosed } from "../utils/jobFilters.js";
import "./JobsPage.css";

function JobDetailPage({ jobId, profile, onBrowseJobs, onEditSkills, onViewAnalysis, onViewTrends, onHomeAction }) {
  const job = mockJobs.find((item) => String(item.id) === String(jobId));
  const closed = job && isJobClosed(job);

  return (
    <div className="app-shell jobs-page">
      <Header activePage="jobs" onBrowseJobs={onBrowseJobs} onEditSkills={onEditSkills} onViewAnalysis={onViewAnalysis} onViewTrends={onViewTrends} onHomeAction={onHomeAction} />
      <main id="main-content" className="content-container jobs-detail-main">
        <button className="jobs-back-button" type="button" onClick={onBrowseJobs}><span aria-hidden="true">←</span> 채용공고 목록으로</button>
        {job ? <article className="jobs-detail-card">
          <div className="jobs-detail-heading">
            <div className="jobs-company"><CompanyLogo job={job} /><p>{job.company}</p></div>
            <span className={`jobs-status${closed ? " jobs-status--closed" : ""}`}>{closed ? "마감" : job.status}</span>
          </div>
          <h1>{job.title}</h1>
          <p className="jobs-data-note">화면 확인을 위한 mock 공고입니다. 원문 링크는 예시 주소입니다.</p>
          {closed && <p className="jobs-closed-notice">마감된 공고입니다. 다른 채용공고도 확인해보세요.</p>}
          <dl className="jobs-detail-facts">
            <div><dt>직무</dt><dd>{job.role}</dd></div>
            <div><dt>근무 지역</dt><dd>{job.location}</dd></div>
            <div><dt>경력</dt><dd>{job.experience}</dd></div>
            <div><dt>고용 형태</dt><dd>{job.employmentType}</dd></div>
            <div><dt>등록일</dt><dd><time dateTime={job.postedAt}>{job.postedAt}</time></dd></div>
            <div><dt>마감일</dt><dd><time dateTime={job.deadline}>{job.deadline}</time></dd></div>
          </dl>
          <section className="jobs-detail-section"><h2>요구 기술</h2><div className="jobs-skill-tags">{job.skills.map((skill) => <span key={skill}>{skill}</span>)}</div></section>
          <section className="jobs-detail-section"><h2>공고 소개</h2><p>{job.summary}</p></section>
          <div className="jobs-detail-footer">
            {profile.skills.length > 0 ? <p className="jobs-match-rate">내 기술과 {calculateMatchRate(job.skills, profile.skills)}% 일치</p> : <button type="button" className="jobs-back-button" onClick={onEditSkills}>내 역량을 입력하고 기술 일치율 확인하기 →</button>}
            <OriginalJobLink url={job.originalUrl} className="primary-button" />
          </div>
        </article> : <div className="jobs-empty-state"><h1>공고를 찾을 수 없습니다.</h1><p>주소를 확인하거나 채용공고 목록에서 다시 찾아보세요.</p><button className="primary-button" type="button" onClick={onBrowseJobs}>채용공고 둘러보기</button></div>}
      </main>
      <Footer source="수집된 채용공고 데이터" copyright={mockDashboard.footer.copyright} onHomeAction={onHomeAction} />
    </div>
  );
}

export default JobDetailPage;
