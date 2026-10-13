function JobDemandOverview({ jobs, selectedJob }) {
  return (
    <ul className="trend-job-demand-list" aria-label="전체 데이터의 직무별 채용공고 수와 비율">
      {jobs.map((job) => (
        <li className={job.id === selectedJob ? "is-selected" : undefined} key={job.id}>
          <span className="trend-demand-job">{job.label}</span>
          <span className="trend-demand-count">{job.count.toLocaleString("ko-KR")}건</span>
          <div className="trend-demand-track" role="progressbar" aria-label={`${job.label} 채용공고 비율`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={job.percentage}>
            <span style={{ width: `${job.percentage}%` }} />
          </div>
          <span className="trend-demand-percentage">{job.percentage}%</span>
        </li>
      ))}
    </ul>
  );
}

export default JobDemandOverview;
