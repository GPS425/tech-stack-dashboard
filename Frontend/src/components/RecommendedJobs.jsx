import { useEffect, useRef, useState } from "react";
import JobCard from "./JobCard.jsx";

function RecommendedJobs({ jobs }) {
  const [selectedJob, setSelectedJob] = useState(null);
  const dialogRef = useRef(null);

  useEffect(() => {
    if (selectedJob) dialogRef.current.showModal();
    else dialogRef.current.close();
  }, [selectedJob]);

  return (
    <>
      <div className="analysis-jobs-grid">{jobs.map((job) => <JobCard key={job.id} job={job} onViewJob={setSelectedJob} />)}</div>
      <dialog
        ref={dialogRef}
        className="dashboard-dialog analysis-job-dialog"
        aria-labelledby="analysis-job-dialog-title"
        onCancel={(event) => { event.preventDefault(); setSelectedJob(null); }}
        onClick={(event) => { if (event.target === event.currentTarget) setSelectedJob(null); }}
      >
        <div className="dialog-heading">
          <h2 id="analysis-job-dialog-title">{selectedJob?.company} · {selectedJob?.title}</h2>
          <button className="dialog-close" type="button" aria-label="공고 상세 닫기" onClick={() => setSelectedJob(null)}>×</button>
        </div>
        {selectedJob && <>
          <p className="dialog-description">{selectedJob.location} · {selectedJob.experience} · {selectedJob.employment}</p>
          <div className="analysis-tags">{selectedJob.skills.map((skill) => <span className="analysis-tag" key={skill}>{skill}</span>)}</div>
          <p className="analysis-job-dialog-description">{selectedJob.description}</p>
          <p className="dialog-description">화면 확인을 위한 샘플 채용공고입니다.</p>
        </>}
      </dialog>
    </>
  );
}

export default RecommendedJobs;
