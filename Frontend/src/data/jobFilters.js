export const popularJobKeywords = ["Java", "Spring", "React", "Python", "AWS", "Docker"];

export const jobFilterGroups = [
  { key: "roles", label: "직무", options: ["백엔드 개발자", "프론트엔드 개발자", "풀스택 개발자", "데이터 분석가", "AI 엔지니어"] },
  { key: "skills", label: "기술스택", options: ["Java", "Spring", "React", "TypeScript", "JavaScript", "Python", "AWS", "Docker", "SQL", "Git", "Kubernetes", "Node.js"] },
  { key: "experiences", label: "경력", options: ["신입", "요구 경력 1년 이상", "요구 경력 3년 이상", "요구 경력 5년 이상"] },
  { key: "employmentTypes", label: "고용 형태", options: ["정규직", "계약직", "인턴", "프리랜서"] },
  { key: "regions", label: "근무 지역", options: ["서울", "경기", "인천", "부산", "대전"] },
];

// API 가 값을 하나만 받는 필터 (경력·고용 형태·지역)
export const singleSelectKeys = ["experiences", "employmentTypes", "regions"];

export function createJobsState(query = "") {
  return {
    draftQuery: query,
    query: query.trim(),
    filters: { roles: [], skills: [], experiences: [], employmentTypes: [], regions: [] },
    sort: "latest",
    page: 1,
    pageSize: 10,
  };
}
