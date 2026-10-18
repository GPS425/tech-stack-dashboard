import { calculateMatchRate } from "./jobMatching.js";

export function getToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
}

export function isJobClosed(job, today = getToday()) {
  return job.status === "마감" || Boolean(job.deadline && job.deadline < today);
}

export function filterJobs(jobs, query, filters) {
  const keyword = query.trim().toLowerCase();
  return jobs.filter((job) => {
    const text = [job.company, job.title, job.role, job.summary, ...job.skills].join(" ").toLowerCase();
    const jobSkills = new Set(job.skills.map((skill) => skill.toLowerCase()));
    const matchesExperience = filters.experiences.length === 0 || filters.experiences.some((value) => {
      if (job.minExperience === null) return true; // 경력 무관 공고는 모든 경력 조건에 지원 가능
      return value === "신입" ? job.minExperience === 0 : job.minExperience >= Number(value.match(/\d+/)[0]);
    });
    return text.includes(keyword)
      && (!filters.roles.length || filters.roles.includes(job.role))
      && filters.skills.every((skill) => jobSkills.has(skill.toLowerCase()))
      && matchesExperience
      && (!filters.employmentTypes.length || filters.employmentTypes.includes(job.employmentType))
      && (!filters.regions.length || filters.regions.includes(job.region));
  });
}

export function sortJobs(jobs, sort, userSkills = [], today = getToday()) {
  return [...jobs].sort((a, b) => {
    // 어떤 정렬에서든 마감 공고는 하단에 배치합니다.
    const closedOrder = Number(isJobClosed(a, today)) - Number(isJobClosed(b, today));
    if (closedOrder) return closedOrder;
    const latest = b.postedAt.localeCompare(a.postedAt) || a.id - b.id;
    if (sort === "deadline") return (a.deadline || "9999-12-31").localeCompare(b.deadline || "9999-12-31") || latest;
    if (sort === "matching" && userSkills.length) return calculateMatchRate(b.skills, userSkills) - calculateMatchRate(a.skills, userSkills) || latest;
    return latest;
  });
}
