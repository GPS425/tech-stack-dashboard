import { getToday } from "./jobFilters.js";

// 공고 API(/api/postings) 요청 주소를 만들고, 응답 한 줄을 화면(JobCard)이 쓰는 모양으로 바꿉니다.

// 화면의 경력 문구 -> API career 값
export const CAREER_CODES = { "신입": "NEW", "요구 경력 1년 이상": "EXP1", "요구 경력 3년 이상": "EXP3", "요구 경력 5년 이상": "EXP5" };

const DEADLINE_LABELS = { DATE: "채용중", UNTIL_FILLED: "채용시", ALWAYS: "상시", ROLLING: "수시" };
const LOGO_COLORS = ["#2f6fed", "#008864", "#e8590c", "#7048e8", "#c2255c", "#0b7285", "#5c7cfa", "#2b8a3e"];

function logoInitial(company) {
  const name = company.replace(/^\(주\)|^㈜|^주식회사\s*/, "").trim();
  return Array.from(name)[0] || "?";
}

function logoColor(company) {
  let hash = 0;
  for (const char of company) hash = (hash * 31 + char.codePointAt(0)) % 100000;
  return LOGO_COLORS[hash % LOGO_COLORS.length];
}

export function toJob(item) {
  return {
    id: item.postingId,
    company: item.company,
    companyInitial: logoInitial(item.company),
    companyColor: logoColor(item.company),
    title: item.title,
    role: (item.jobs || []).join(", "),
    location: item.region || "지역 미정",
    experience: item.career,
    employmentType: item.employmentType || "미정",
    skills: item.skills || [],
    postedAt: item.postedOn || "",
    // 채용시·상시·수시 공고는 서버가 마감일을 9999-01-01 로 보내므로 날짜 마감일 때만 씁니다
    deadline: item.deadlineType === "DATE" ? item.deadlineOn || null : null,
    deadlineType: item.deadlineType,
    originalUrl: item.url,
    education: item.education,
    matchRate: item.matchRate ?? null,
    status: item.closed ? "마감" : DEADLINE_LABELS[item.deadlineType] || "채용중",
  };
}

// 화면 상태(state) -> /api/postings 주소. jobs 는 /api/jobs 응답(직무 이름 -> jobId 변환용)
export function buildPostingsUrl(state, jobs, mySkills) {
  const { filters } = state;
  const params = new URLSearchParams();
  if (state.query) params.set("q", state.query);
  const jobIds = filters.roles.map((name) => jobs.find((job) => job.name === name)?.jobId).filter((id) => id !== undefined);
  if (jobIds.length) params.set("jobIds", jobIds.join(","));
  if (filters.skills.length) params.set("skills", filters.skills.join(","));
  if (filters.experiences[0]) params.set("career", CAREER_CODES[filters.experiences[0]]);
  if (filters.employmentTypes[0]) params.set("employment", filters.employmentTypes[0]);
  if (filters.regions[0]) params.set("region", filters.regions[0]);
  const useMatching = state.sort === "matching" && mySkills.length > 0;
  params.set("sort", useMatching ? "matching" : state.sort === "deadline" ? "deadline" : "latest");
  if (mySkills.length) params.set("mySkills", mySkills.join(","));
  params.set("page", String(state.page));
  params.set("size", String(state.pageSize));
  return `/api/postings?${params}`;
}

export async function fetchJson(url, signal) {
  const response = await fetch(url, { signal });
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return response.json();
}

// 접수 시작일이 오늘보다 미래인 공고(미리 올려 둔 공고)
export function isScheduled(job) {
  return Boolean(job.postedAt) && job.postedAt > getToday();
}
