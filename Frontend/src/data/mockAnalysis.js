import { skillsJobs } from "./mockSkills.js";

export const analysisJobs = skillsJobs;

// 기본 백엔드 결과의 수치는 목업에 제시된 고정 샘플입니다.
// 사용자가 역량을 입력한 경우에는 아래 직무별 샘플 시장 데이터와 실제 선택을 비교합니다.
const roleSamples = {
  backend: {
    marketSkills: ["Java", "Spring", "AWS", "SQL", "Kubernetes", "React", "JavaScript", "Linux", "Git", "Docker"],
    ownedSkills: ["Java", "Spring", "AWS", "SQL", "Python"],
    prioritySkills: ["Docker", "React", "Kubernetes", "JavaScript", "Linux", "Git", "Java", "Spring", "AWS", "SQL"],
    sampleMissingSkills: ["Docker", "React", "Kubernetes", "JavaScript", "Linux"],
    sampleFitScore: 68,
    sampleMatchedCount: 5,
    weights: { Java: 20, Spring: 20, AWS: 12, SQL: 12, Kubernetes: 8, React: 5, JavaScript: 5, Linux: 7, Git: 4, Docker: 7 },
  },
  frontend: {
    marketSkills: ["React", "JavaScript", "TypeScript", "HTML/CSS", "Node.js", "Git", "SQL", "AWS", "Docker", "Linux"],
    ownedSkills: ["React", "JavaScript", "HTML/CSS", "Git", "Python"],
    prioritySkills: ["TypeScript", "Node.js", "Docker", "AWS", "SQL", "Linux", "React", "JavaScript", "HTML/CSS", "Git"],
    sampleFitScore: 72,
  },
  "data-engineer": {
    marketSkills: ["Python", "SQL", "Spark", "Airflow", "AWS", "Docker", "Linux", "Git", "MongoDB", "Redis"],
    ownedSkills: ["Python", "SQL", "AWS", "Git", "Pandas"],
    prioritySkills: ["Spark", "Airflow", "Docker", "Linux", "MongoDB", "Redis", "Python", "SQL", "AWS", "Git"],
    sampleFitScore: 66,
  },
  "data-analyst": {
    marketSkills: ["SQL", "Python", "Pandas", "Tableau", "Excel", "NumPy", "Git", "AWS", "MongoDB", "Linux"],
    ownedSkills: ["SQL", "Python", "Pandas", "Excel", "Git"],
    prioritySkills: ["Tableau", "NumPy", "AWS", "MongoDB", "Linux", "SQL", "Python", "Pandas", "Excel", "Git"],
    sampleFitScore: 75,
  },
  ai: {
    marketSkills: ["Python", "PyTorch", "TensorFlow", "Scikit-learn", "NumPy", "Pandas", "AWS", "Docker", "Linux", "Git"],
    ownedSkills: ["Python", "SQL", "NumPy", "AWS", "Git"],
    prioritySkills: ["PyTorch", "TensorFlow", "Scikit-learn", "Pandas", "Docker", "Linux", "Python", "NumPy", "AWS", "Git"],
    sampleFitScore: 61,
  },
};

const learningGuides = {
  Docker: { icon: "docker", direction: "컨테이너 기본 개념과 이미지/컨테이너 실행 실습, Spring Boot 배포 경험", duration: "2주" },
  React: { icon: "react", direction: "컴포넌트/상태관리 기초와 간단한 웹 애플리케이션 구현", duration: "3주" },
  Kubernetes: { icon: "kubernetes", direction: "오케스트레이션 기본 개념과 배포 방식 이해", duration: "4주" },
  JavaScript: { icon: "javascript", direction: "ES6+ 문법, 비동기 처리, DOM/브라우저 동작 이해", duration: "2주" },
  Linux: { icon: "linux", direction: "기본 명령어, 파일 시스템, 서버 환경 기초", duration: "2주" },
  Java: { icon: "java", direction: "객체 지향 프로그래밍과 컬렉션 기초, 간단한 API 구현", duration: "3주" },
  Spring: { icon: "spring", direction: "Spring Boot 기본 구조, 의존성 주입과 REST API 구현", duration: "3주" },
  AWS: { icon: "aws", direction: "클라우드 기본 개념과 EC2, S3를 활용한 서비스 배포", duration: "2주" },
  SQL: { icon: "sql", direction: "조회와 JOIN, 집계 쿼리 및 관계형 데이터베이스 기초", duration: "2주" },
  Git: { icon: "git", direction: "버전 관리, 브랜치와 병합, 협업 워크플로 실습", duration: "1주" },
  TypeScript: { icon: "typescript", direction: "타입과 인터페이스 기초, 기존 JavaScript 코드에 타입 적용", duration: "2주" },
  "HTML/CSS": { icon: "html", direction: "시맨틱 HTML, Flex와 Grid를 활용한 반응형 화면 구현", duration: "2주" },
  "Node.js": { icon: "node", direction: "비동기 처리와 간단한 서버, REST API 구현", duration: "3주" },
  Python: { icon: "python", direction: "기본 문법과 자료구조, 데이터 처리 실습", duration: "2주" },
  Spark: { icon: "spark", direction: "분산 데이터 처리 개념과 DataFrame 기반 배치 작업 실습", duration: "3주" },
  Airflow: { icon: "airflow", direction: "DAG 구성과 스케줄링, 데이터 파이프라인 자동화", duration: "2주" },
  MongoDB: { icon: "mongodb", direction: "문서형 데이터 모델과 CRUD, 인덱스 활용", duration: "2주" },
  Redis: { icon: "redis", direction: "자료구조와 캐시 전략, 서비스에 캐시 적용", duration: "1주" },
  Tableau: { icon: "tableau", direction: "데이터 연결과 시각화, 분석 대시보드 구성", duration: "2주" },
  Excel: { icon: "excel", direction: "함수와 피벗 테이블을 활용한 데이터 요약 및 분석", duration: "1주" },
  NumPy: { icon: "numpy", direction: "배열 연산과 브로드캐스팅, 수치 데이터 처리", duration: "1주" },
  Pandas: { icon: "pandas", direction: "DataFrame 기초, 데이터 정제와 집계 분석", duration: "2주" },
  PyTorch: { icon: "pytorch", direction: "텐서 연산과 신경망 학습, 간단한 모델 구현", duration: "3주" },
  TensorFlow: { icon: "tensorflow", direction: "Keras 모델 구성과 학습, 모델 평가 및 저장", duration: "3주" },
  "Scikit-learn": { icon: "scikit-learn", direction: "전처리와 분류·회귀 모델 학습, 교차 검증", duration: "2주" },
};

const backendJobs = [
  {
    id: "naver-backend", company: "네이버클라우드", logoText: "N", logoTone: "naver",
    title: "백엔드 개발자", location: "경기도 성남시", experience: "경력 무관", employment: "정규직",
    skills: ["Java", "Spring Boot", "Docker"], matchRate: 87,
    description: "클라우드 기반의 대규모 서비스를 함께 만들어갈 백엔드 개발자를 찾습니다.",
  },
  {
    id: "kakao-backend", company: "카카오엔터프라이즈", logoText: "kakao", logoTone: "kakao",
    title: "서버 개발자", location: "경기도 판교", experience: "경력 3년 이상", employment: "정규직",
    skills: ["AWS", "Kubernetes", "Java"], matchRate: 82,
    description: "안정적이고 확장 가능한 서비스를 함께 만들어갈 서버 개발자를 모집합니다.",
  },
  {
    id: "woowa-backend", company: "우아한형제들", logoText: "우아한", logoTone: "woowa",
    title: "플랫폼 백엔드 개발자", location: "서울시 송파구", experience: "경력 2년 이상", employment: "정규직",
    skills: ["Linux", "Docker", "SQL"], matchRate: 79,
    description: "더 나은 서비스 경험을 만드는 플랫폼 팀에서 함께 성장할 백엔드 개발자를 찾습니다.",
  },
];

const roleJobSkills = {
  frontend: [["React", "TypeScript", "Git"], ["JavaScript", "React", "AWS"], ["HTML/CSS", "Node.js", "Docker"]],
  "data-engineer": [["Python", "SQL", "Spark"], ["Airflow", "AWS", "Docker"], ["SQL", "MongoDB", "Linux"]],
  "data-analyst": [["SQL", "Python", "Tableau"], ["Pandas", "Excel", "SQL"], ["Python", "NumPy", "AWS"]],
  ai: [["Python", "PyTorch", "AWS"], ["TensorFlow", "Docker", "Python"], ["Scikit-learn", "Pandas", "NumPy"]],
};

function includesSkill(skills, requiredSkill) {
  const normalized = requiredSkill === "Spring Boot" ? "spring" : requiredSkill.toLowerCase();
  return skills.some((skill) => skill.toLowerCase() === normalized);
}

// 데이터 교체 지점: 이후 API 응답을 이 반환 형식으로 전달하면 UI는 그대로 사용할 수 있습니다.
export function getMockAnalysis(roleId, selectedSkills) {
  const job = analysisJobs.find((item) => item.id === roleId) || analysisJobs[0];
  const sample = roleSamples[job.id];
  const isSample = selectedSkills === undefined;
  const ownedSkills = isSample ? sample.ownedSkills : [...selectedSkills];
  const matchingSkills = sample.marketSkills.filter((skill) => includesSkill(ownedSkills, skill));
  const missingSkills = isSample && sample.sampleMissingSkills
    ? sample.sampleMissingSkills
    : sample.prioritySkills.filter((skill) => !includesSkill(ownedSkills, skill));
  const matchedCount = isSample && sample.sampleMatchedCount !== undefined ? sample.sampleMatchedCount : matchingSkills.length;
  const fitScore = isSample ? sample.sampleFitScore : sample.weights
    ? matchingSkills.reduce((score, skill) => score + sample.weights[skill], 0)
    : Math.round(matchingSkills.length / sample.marketSkills.length * 100);
  const jobs = job.id === "backend" ? backendJobs : backendJobs.map((posting, index) => ({
    ...posting,
    id: `${job.id}-${posting.id}`,
    title: job.label,
    skills: roleJobSkills[job.id][index],
    description: `${posting.company}에서 함께 성장하며 서비스를 발전시킬 ${job.label}를 찾습니다.`,
  }));

  return {
    jobId: job.id,
    jobLabel: job.label,
    isSample,
    fitScore,
    matchedCount,
    marketSkills: sample.marketSkills,
    ownedSkills,
    missingSkills,
    prioritySkills: missingSkills.slice(0, 3),
    coverage: Math.round(matchedCount / sample.marketSkills.length * 100),
    learningRoadmap: missingSkills.map((skill, index) => ({ priority: index + 1, skill, ...learningGuides[skill] })),
    recommendedJobs: jobs.map((posting) => ({
      ...posting,
      matchRate: isSample ? posting.matchRate : Math.round(posting.skills.filter((skill) => includesSkill(ownedSkills, skill)).length / posting.skills.length * 100),
    })).sort((first, second) => second.matchRate - first.matchRate),
  };
}
