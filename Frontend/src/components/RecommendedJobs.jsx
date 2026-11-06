import JobCard from "./JobCard.jsx";

// 추천·관련 공고 카드 목록. 카드를 누르면 공고 상세 화면으로 이동합니다.
function RecommendedJobs({ jobs, loading = false, failed = false, onRetry, onViewJob, emptyText = "조건에 맞는 공고가 없습니다." }) {
  if (loading) return <p className="analysis-jobs-note" role="status">공고를 불러오는 중입니다…</p>;
  if (failed) return <p className="analysis-jobs-note" role="alert">공고를 불러오지 못했습니다. 서버가 켜져 있는지 확인해 주세요. {onRetry && <button type="button" className="analysis-all-jobs" onClick={onRetry}>다시 시도</button>}</p>;
  if (!jobs.length) return <p className="analysis-jobs-note">{emptyText}</p>;
  return <div className="analysis-jobs-grid">{jobs.map((job) => <JobCard key={job.id} job={job} onViewJob={onViewJob} />)}</div>;
}

export default RecommendedJobs;
