function JobSelect({ jobs, value, onChange }) {
  return (
    <div className="role-select-field skills-job-select">
      <label className="sr-only" htmlFor="desired-job">희망 직무 선택</label>
      <select
        id="desired-job"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">희망 직무를 선택해주세요</option>
        {jobs.map((job) => (
          <option key={job.id} value={job.id}>{job.label}</option>
        ))}
      </select>
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <path d="m5 6 3 4 3-4" />
      </svg>
    </div>
  );
}

export default JobSelect;
