// 역량 입력 화면을 위한 샘플 데이터입니다. 실제 API는 사용하지 않습니다.
export const MAX_SELECTED_SKILLS = 10;

export const skillsJobs = [
  { id: "backend", label: "백엔드 개발자", description: "백엔드 개발자는 서버, 데이터베이스, API 등을 개발하고 서비스의 핵심 로직을 구현합니다." },
  { id: "frontend", label: "프론트엔드 개발자", description: "프론트엔드 개발자는 웹 화면과 사용자 인터페이스를 구현하고 편리한 사용 경험을 만듭니다." },
  { id: "data-engineer", label: "데이터 엔지니어", description: "데이터 엔지니어는 데이터를 수집하고 처리하는 파이프라인과 데이터 저장 시스템을 구축합니다." },
  { id: "data-analyst", label: "데이터 분석가", description: "데이터 분석가는 데이터를 분석하고 시각화하여 서비스 개선과 의사결정에 필요한 정보를 찾습니다." },
  { id: "ai", label: "AI/ML 엔지니어", description: "AI/ML 엔지니어는 데이터를 바탕으로 머신러닝 모델을 개발하고 서비스에 적용합니다." },
];

export const popularSkills = [
  "Java", "Spring", "AWS", "SQL", "Docker",
  "React", "JavaScript", "Python", "Git",
];

export const skillCatalog = [
  ...popularSkills,
  "TypeScript", "Node.js", "HTML/CSS", "Kubernetes", "Linux",
  "MongoDB", "Redis", "Spark", "Airflow", "Tableau", "Excel",
  "PyTorch", "TensorFlow", "Pandas", "NumPy", "Scikit-learn",
];

export const recommendedSkillsByRole = {
  backend: ["Java", "Spring", "AWS", "SQL", "Docker", "Git"],
  frontend: ["React", "JavaScript", "TypeScript", "HTML/CSS", "Git", "Node.js"],
  "data-engineer": ["Python", "SQL", "Spark", "Airflow", "AWS", "Docker"],
  "data-analyst": ["SQL", "Python", "Pandas", "Tableau", "Excel", "NumPy"],
  ai: ["Python", "PyTorch", "TensorFlow", "Scikit-learn", "NumPy", "AWS"],
};
