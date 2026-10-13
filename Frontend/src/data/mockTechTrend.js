// 한 시점의 데이터 분포를 보여주는 화면용 샘플입니다. 기간별 변화 데이터는 포함하지 않습니다.
export const trendMetadata = {
  analysisDate: "2026-10-12",
  postingCount: 1248,
  source: "수집된 채용공고 데이터",
};

export const trendJobs = [
  { id: "all", label: "전체" },
  { id: "backend", label: "백엔드 개발자" },
  { id: "frontend", label: "프론트엔드 개발자" },
  { id: "data-analyst", label: "데이터 분석가" },
  { id: "ai", label: "AI 엔지니어" },
  { id: "fullstack", label: "풀스택 개발자" },
];

export const trendSummary = [
  { id: "postings", label: "분석 채용공고", value: trendMetadata.postingCount, unit: "건", icon: "file", description: "현재 보유한 채용공고 수입니다." },
  { id: "skills", label: "분석 기술스택", value: 286, unit: "개", icon: "code", description: "채용공고에서 추출한 기술스택 수입니다." },
  { id: "job", label: "수요 1위 직무", value: "백엔드 개발자", icon: "trophy", description: "가장 많은 채용공고가 등록된 직무입니다." },
  { id: "technology", label: "요구 1위 기술", value: "Java", icon: "star", description: "채용공고에서 가장 많이 언급된 기술입니다." },
];

// 수와 비율은 목업에 제시된 독립적인 예시 값이며 실제 집계값으로 교체할 필드입니다.
export const jobDemand = [
  { id: "backend", label: "백엔드 개발자", count: 1006, percentage: 27.4 },
  { id: "frontend", label: "프론트엔드 개발자", count: 968, percentage: 23.9 },
  { id: "data-analyst", label: "데이터 분석가", count: 553, percentage: 13.8 },
  { id: "ai", label: "AI 엔지니어", count: 367, percentage: 11.1 },
  { id: "fullstack", label: "풀스택 개발자", count: 312, percentage: 9.6 },
  { id: "other", label: "기타", count: 284, percentage: 7.7 },
];

export const popularSkillsByJob = {
  backend: [
    { name: "Java", icon: "java", percentage: 82 },
    { name: "Spring", icon: "spring", percentage: 76 },
    { name: "AWS", icon: "aws", percentage: 68 },
    { name: "SQL", icon: "sql", percentage: 61 },
    { name: "Docker", icon: "docker", percentage: 58 },
  ],
  frontend: [
    { name: "React", icon: "react", percentage: 79 },
    { name: "JavaScript", icon: "javascript", percentage: 77 },
    { name: "TypeScript", icon: "typescript", percentage: 72 },
    { name: "HTML/CSS", icon: "html", percentage: 69 },
    { name: "Node.js", icon: "node", percentage: 43 },
  ],
  "data-analyst": [
    { name: "Python", icon: "python", percentage: 71 },
    { name: "SQL", icon: "sql", percentage: 68 },
    { name: "Pandas", icon: "pandas", percentage: 64 },
    { name: "Tableau", icon: "tableau", percentage: 58 },
    { name: "Excel", icon: "excel", percentage: 52 },
  ],
  ai: [
    { name: "Python", icon: "python", percentage: 83 },
    { name: "PyTorch", icon: "pytorch", percentage: 76 },
    { name: "TensorFlow", icon: "tensorflow", percentage: 68 },
    { name: "SQL", icon: "sql", percentage: 54 },
    { name: "AWS", icon: "aws", percentage: 48 },
  ],
  fullstack: [
    { name: "JavaScript", icon: "javascript", percentage: 84 },
    { name: "React", icon: "react", percentage: 79 },
    { name: "Java", icon: "java", percentage: 68 },
    { name: "Spring", icon: "spring", percentage: 61 },
    { name: "SQL", icon: "sql", percentage: 57 },
  ],
};

export const jobSkillMatrix = {
  columns: [
    { id: "backend", label: "백엔드" },
    { id: "frontend", label: "프론트엔드" },
    { id: "data-analyst", label: "데이터 분석" },
    { id: "ai", label: "AI 엔지니어" },
  ],
  rows: [
    { skill: "Java", values: [82, 12, 8, 5] },
    { skill: "Spring", values: [76, 10, 6, 4] },
    { skill: "React", values: [5, 79, 8, 3] },
    { skill: "Python", values: [12, 18, 71, 83] },
    { skill: "SQL", values: [61, 20, 68, 54] },
    { skill: "Docker", values: [58, 22, 24, 26] },
  ],
};

export const techCombinationsByJob = {
  all: [
    { skills: ["Java", "Spring"], count: 324 },
    { skills: ["Spring", "AWS"], count: 287 },
    { skills: ["React", "JavaScript"], count: 221 },
    { skills: ["Python", "SQL"], count: 198 },
    { skills: ["Docker", "Kubernetes"], count: 176 },
  ],
  backend: [
    { skills: ["Java", "Spring"], count: 324 },
    { skills: ["Spring", "AWS"], count: 287 },
    { skills: ["Docker", "Kubernetes"], count: 176 },
    { skills: ["Java", "SQL"], count: 165 },
    { skills: ["AWS", "Docker"], count: 143 },
  ],
  frontend: [
    { skills: ["React", "JavaScript"], count: 221 },
    { skills: ["React", "TypeScript"], count: 196 },
    { skills: ["JavaScript", "TypeScript"], count: 160 },
    { skills: ["React", "Node.js"], count: 131 },
    { skills: ["HTML/CSS", "JavaScript"], count: 117 },
  ],
  "data-analyst": [
    { skills: ["Python", "SQL"], count: 198 },
    { skills: ["SQL", "Tableau"], count: 155 },
    { skills: ["Python", "Pandas"], count: 136 },
    { skills: ["Pandas", "NumPy"], count: 104 },
    { skills: ["SQL", "Excel"], count: 87 },
  ],
  ai: [
    { skills: ["Python", "PyTorch"], count: 174 },
    { skills: ["Python", "TensorFlow"], count: 168 },
    { skills: ["Python", "NumPy"], count: 144 },
    { skills: ["TensorFlow", "Docker"], count: 99 },
    { skills: ["AWS", "PyTorch"], count: 84 },
  ],
  fullstack: [
    { skills: ["React", "JavaScript"], count: 221 },
    { skills: ["Java", "Spring"], count: 186 },
    { skills: ["React", "Node.js"], count: 174 },
    { skills: ["Spring", "AWS"], count: 160 },
    { skills: ["Docker", "Kubernetes"], count: 132 },
  ],
};

const defaultRelatedJobs = [
  {
    id: "trend-naver", company: "네이버클라우드", logoText: "N", logoTone: "naver",
    title: "백엔드 개발자", location: "경기도 성남시", experience: "경력 무관", employment: "정규직",
    skills: ["Java", "Spring", "AWS"], publishedLabel: "1일 전 등록",
    description: "클라우드 기반의 대규모 서비스를 함께 만들어갈 백엔드 개발자를 찾습니다.",
  },
  {
    id: "trend-kakao", company: "카카오엔터프라이즈", logoText: "kakao", logoTone: "kakao",
    title: "서버 개발자", location: "경기도 판교", experience: "경력 3년 이상", employment: "정규직",
    skills: ["Java", "Kubernetes", "Docker"], publishedLabel: "2일 전 등록",
    description: "안정적이고 확장 가능한 서비스를 함께 만들어갈 서버 개발자를 모집합니다.",
  },
  {
    id: "trend-woowa", company: "우아한형제들", logoText: "우아한", logoTone: "woowa",
    title: "플랫폼 백엔드 개발자", location: "서울시 송파구", experience: "경력 2년 이상", employment: "정규직",
    skills: ["Python", "SQL", "AWS"], publishedLabel: "3일 전 등록",
    description: "더 나은 서비스 경험을 만드는 플랫폼 팀에서 함께 성장할 개발자를 찾습니다.",
  },
];

const relatedJobSkills = {
  frontend: [["React", "JavaScript", "TypeScript"], ["React", "HTML/CSS", "Git"], ["JavaScript", "Node.js", "AWS"]],
  "data-analyst": [["Python", "SQL", "Pandas"], ["SQL", "Tableau", "Excel"], ["Python", "Pandas", "AWS"]],
  ai: [["Python", "PyTorch", "AWS"], ["Python", "TensorFlow", "Docker"], ["Python", "Scikit-learn", "NumPy"]],
  fullstack: [["React", "Java", "SQL"], ["JavaScript", "Spring", "AWS"], ["React", "Node.js", "Docker"]],
};

export const relatedJobsByJob = Object.fromEntries(trendJobs.map((job) => [job.id,
  job.id === "all" || job.id === "backend" ? defaultRelatedJobs : defaultRelatedJobs.map((posting, index) => ({
    ...posting,
    id: `${job.id}-${posting.id}`,
    title: job.label,
    skills: relatedJobSkills[job.id][index],
    description: `${posting.company}에서 함께 서비스를 발전시키고 성장할 ${job.label}를 찾습니다.`,
  })),
]));

// API 응답 교체 지점. 요약/직무 분포/비교 표는 전체 데이터, 순위/조합/공고는 선택 직무 기준입니다.
export function getMockTechTrend(jobId) {
  const selectedJob = trendJobs.find((job) => job.id === jobId) || trendJobs[0];
  const rankingJob = selectedJob.id === "all" ? trendJobs[1] : selectedJob;

  return {
    metadata: trendMetadata,
    summary: trendSummary,
    jobDemand,
    jobSkillMatrix,
    selectedJob,
    rankingJob,
    popularSkills: popularSkillsByJob[rankingJob.id],
    techCombinations: techCombinationsByJob[selectedJob.id],
    relatedJobs: relatedJobsByJob[selectedJob.id],
  };
}
