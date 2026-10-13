// 메인 화면의 수치와 백엔드 TOP 5는 제공된 목업을 기준으로 합니다.
// 다른 직무별 순위와 채용공고는 화면 동작 확인을 위한 샘플 데이터입니다.
export const jobRoles = [
  { id: "backend", label: "백엔드 개발자" },
  { id: "frontend", label: "프론트엔드 개발자" },
  { id: "fullstack", label: "풀스택 개발자" },
  { id: "mobile", label: "모바일 개발자" },
  { id: "data-engineer", label: "데이터 엔지니어" },
  { id: "data-analyst", label: "데이터 분석가" },
  { id: "devops", label: "DevOps 엔지니어" },
  { id: "ai", label: "AI/ML 엔지니어" },
];

export const mockDashboard = {
  introduction: {
    title: ["개발자 채용공고와", "나의 기술 역량을 한눈에"],
    description: ["채용공고에서 요구하는 기술스택을 분석하고", "나의 역량과 비교해보세요."],
    searchPlaceholder: "직무, 기술스택, 기업을 검색해보세요",
  },
  summary: [
    { id: "postings", label: "전체 채용공고", value: 1002, unit: "건" },
    { id: "stacks", label: "분석 기술 스택", value: 46, unit: "개" },
    { id: "roles", label: "분석 직무", value: jobRoles.length, unit: "개" },
  ],
  footer: {
    source: "",
    copyright: "© 2026 JOBSTACK. All rights reserved.",
  },
};

export const techStacksByRole = {
  backend: [
    { name: "Java", icon: "java", percentage: 82 },
    { name: "Spring", icon: "spring", percentage: 76 },
    { name: "AWS", icon: "aws", percentage: 68 },
    { name: "SQL", icon: "sql", percentage: 61 },
    { name: "Python", icon: "python", percentage: 58 },
  ],
  frontend: [
    { name: "React", icon: "react", percentage: 88 },
    { name: "TypeScript", icon: "typescript", percentage: 81 },
    { name: "JavaScript", icon: "javascript", percentage: 77 },
    { name: "HTML/CSS", icon: "html", percentage: 69 },
    { name: "Node.js", icon: "node", percentage: 43 },
  ],
  fullstack: [
    { name: "JavaScript", icon: "javascript", percentage: 84 },
    { name: "React", icon: "react", percentage: 79 },
    { name: "Node.js", icon: "node", percentage: 71 },
    { name: "SQL", icon: "sql", percentage: 65 },
    { name: "AWS", icon: "aws", percentage: 59 },
  ],
  mobile: [
    { name: "Kotlin", icon: "kotlin", percentage: 78 },
    { name: "Swift", icon: "swift", percentage: 74 },
    { name: "Flutter", icon: "flutter", percentage: 62 },
    { name: "React", icon: "react", percentage: 55 },
    { name: "Java", icon: "java", percentage: 49 },
  ],
  "data-engineer": [
    { name: "Python", icon: "python", percentage: 89 },
    { name: "SQL", icon: "sql", percentage: 85 },
    { name: "Spark", icon: "spark", percentage: 72 },
    { name: "AWS", icon: "aws", percentage: 68 },
    { name: "Airflow", icon: "airflow", percentage: 57 },
  ],
  "data-analyst": [
    { name: "SQL", icon: "sql", percentage: 91 },
    { name: "Python", icon: "python", percentage: 83 },
    { name: "Tableau", icon: "tableau", percentage: 66 },
    { name: "Excel", icon: "excel", percentage: 61 },
    { name: "AWS", icon: "aws", percentage: 42 },
  ],
  devops: [
    { name: "AWS", icon: "aws", percentage: 90 },
    { name: "Docker", icon: "docker", percentage: 85 },
    { name: "Kubernetes", icon: "kubernetes", percentage: 78 },
    { name: "Linux", icon: "linux", percentage: 73 },
    { name: "Python", icon: "python", percentage: 59 },
  ],
  ai: [
    { name: "Python", icon: "python", percentage: 95 },
    { name: "PyTorch", icon: "pytorch", percentage: 82 },
    { name: "TensorFlow", icon: "tensorflow", percentage: 74 },
    { name: "SQL", icon: "sql", percentage: 63 },
    { name: "AWS", icon: "aws", percentage: 56 },
  ],
};

export const mockJobPostings = [
  { id: 1, company: "그린랩스", title: "Java / Spring 백엔드 개발자", role: "백엔드 개발자", stacks: ["Java", "Spring", "AWS", "SQL"] },
  { id: 2, company: "스택웍스", title: "React 프론트엔드 개발자", role: "프론트엔드 개발자", stacks: ["React", "TypeScript", "JavaScript", "HTML/CSS"] },
  { id: 3, company: "넥스트테크", title: "서비스 풀스택 개발자", role: "풀스택 개발자", stacks: ["React", "Node.js", "SQL", "AWS"] },
  { id: 4, company: "모바일스튜디오", title: "모바일 앱 개발자", role: "모바일 개발자", stacks: ["Kotlin", "Swift", "Flutter"] },
  { id: 5, company: "데이터브릿지", title: "데이터 파이프라인 엔지니어", role: "데이터 엔지니어", stacks: ["Python", "SQL", "Spark", "Airflow"] },
  { id: 6, company: "인사이트랩", title: "서비스 데이터 분석가", role: "데이터 분석가", stacks: ["SQL", "Python", "Tableau", "Excel"] },
  { id: 7, company: "클라우드웍스", title: "클라우드 DevOps 엔지니어", role: "DevOps 엔지니어", stacks: ["AWS", "Docker", "Kubernetes", "Linux"] },
  { id: 8, company: "비전AI", title: "AI / ML 엔지니어", role: "AI/ML 엔지니어", stacks: ["Python", "PyTorch", "TensorFlow", "AWS"] },
];
