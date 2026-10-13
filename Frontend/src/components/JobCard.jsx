import AnalysisIcon from "./AnalysisIcon.jsx";

function JobCard({ job, onViewJob }) {
  const viewButton = <button className="analysis-job-button" type="button" onClick={() => onViewJob(job)} aria-label={`${job.company} ${job.title} 공고 보기`}>공고 보기 <span aria-hidden="true">→</span></button>;

  return (
    <article className="analysis-job-card">
      <div className="analysis-job-heading">
        <span className={`analysis-company-logo analysis-company-logo--${job.logoTone}`} aria-hidden="true">{job.logoText}</span>
        <p className="analysis-job-company">{job.company}</p>
        {job.matchRate !== undefined && <span className="analysis-job-match">매칭률 {job.matchRate}%</span>}
      </div>
      <h3>{job.title}</h3>
      <ul className="analysis-job-meta" aria-label="근무 조건">
        <li><AnalysisIcon name="pin" />{job.location}</li><li>{job.experience}</li><li>{job.employment}</li>
      </ul>
      <div className="analysis-tags">{job.skills.map((skill) => <span className="analysis-tag" key={skill}>{skill}</span>)}</div>
      <p className="analysis-job-description">{job.description}</p>
      {job.publishedLabel ? <div className="analysis-job-actions"><p>{job.publishedLabel}</p>{viewButton}</div> : viewButton}
    </article>
  );
}

export default JobCard;
