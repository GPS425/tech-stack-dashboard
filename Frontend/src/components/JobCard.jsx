import AnalysisIcon from "./AnalysisIcon.jsx";
import { isJobClosed } from "../utils/jobFilters.js";
import { isScheduled } from "../utils/postingApi.js";

export function CompanyLogo({ job }) {
  return <span className={`jobs-company-logo${job.companyInitial.length > 2 ? " jobs-company-logo--word" : ""}${job.companyColor === "#fee500" || job.companyColor === "#edf2f0" ? " jobs-company-logo--dark" : ""}`} style={{ backgroundColor: job.companyColor }} aria-hidden="true">{job.companyInitial}</span>;
}

export function OriginalJobLink({ url, className = "jobs-outline-button" }) {
  return url
    ? <a className={className} href={url} target="_blank" rel="noreferrer">원문 보기 <span aria-hidden="true">↗</span><span className="sr-only"> (새 탭)</span></a>
    : <button className={className} type="button" disabled>원문 없음</button>;
}

function JobCard({ job, onViewJob, variant, userSkills = [] }) {
  if (variant === "listing") {
    const closed = isJobClosed(job);
    return (
      <article className="jobs-card">
        <div className="jobs-company">
          <CompanyLogo job={job} />
          <p>{job.company}</p>
        </div>
        <div className="jobs-card-content">
          <div className="jobs-card-heading">
            <h3><button type="button" onClick={() => onViewJob(job)}>{job.title}</button></h3>
            <span className={`jobs-status${closed ? " jobs-status--closed" : ""}`}>{closed ? "마감" : job.status}</span>
          </div>
          <ul className="jobs-card-meta" aria-label="근무 조건">
            <li><AnalysisIcon name="pin" />{job.location}</li>
            <li>{job.experience}</li>
            <li>{job.employmentType}</li>
          </ul>
          <div className="jobs-skill-tags" aria-label="요구 기술">
            {job.skills.slice(0, 3).map((skill) => <span key={skill}>{skill}</span>)}
            {job.skills.length > 3 && <span className="jobs-extra-skills" title={job.skills.slice(3).join(", ")}><span className="sr-only">추가 기술: {job.skills.slice(3).join(", ")}. </span>+{job.skills.length - 3}</span>}
          </div>
        </div>
        <div className="jobs-card-actions">
          <p className={userSkills.length && job.matchRate !== null ? "jobs-match-rate" : "jobs-no-profile"}>{userSkills.length && job.matchRate !== null ? `내 기술과 ${job.matchRate}% 일치` : "역량 입력 후 확인"}</p>
          <p className="jobs-posted-at">접수 시작 {job.postedAt ? <time dateTime={job.postedAt}>{job.postedAt.replaceAll("-", ".")}</time> : "-"}{isScheduled(job) && <> · <strong>접수 예정</strong></>}</p>
          <button type="button" className="jobs-outline-button jobs-detail-button" onClick={() => onViewJob(job)}>상세 보기 <span aria-hidden="true">→</span></button>
          <OriginalJobLink url={job.originalUrl} />
        </div>
      </article>
    );
  }
  const viewButton = <button className="analysis-job-button" type="button" onClick={() => onViewJob(job)} aria-label={`${job.company} ${job.title} 공고 보기`}>공고 보기 <span aria-hidden="true">→</span></button>;
  const deadlineLabel = job.deadline ? `마감 ${job.deadline.replaceAll("-", ".")}` : job.status;

  return (
    <article className="analysis-job-card">
      <div className="analysis-job-heading">
        <span className="analysis-company-logo" style={{ backgroundColor: job.companyColor, color: "#fff", fontSize: 14 }} aria-hidden="true">{job.companyInitial}</span>
        <p className="analysis-job-company">{job.company}</p>
        {job.matchRate !== null && job.matchRate !== undefined && <span className="analysis-job-match">매칭률 {job.matchRate}%</span>}
      </div>
      <h3>{job.title}</h3>
      <ul className="analysis-job-meta" aria-label="근무 조건">
        <li><AnalysisIcon name="pin" />{job.location}</li><li>{job.experience}</li><li>{job.employmentType}</li>
      </ul>
      <div className="analysis-tags">
        {job.skills.slice(0, 5).map((skill) => <span className="analysis-tag" key={skill}>{skill}</span>)}
        {job.skills.length > 5 && <span className="analysis-tag" title={job.skills.slice(5).join(", ")}>+{job.skills.length - 5}</span>}
      </div>
      <div className="analysis-job-actions"><p>{deadlineLabel}</p>{viewButton}</div>
    </article>
  );
}

export default JobCard;
