// 기술별 학습 방향·예상 기간 (DB 에 없어서 프론트가 가진 안내 문구)
export const learningGuides = {
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

// 안내 문구가 없는 기술은 기본 문구를 씁니다
export function getLearningGuide(skill) {
  return learningGuides[skill] || {
    icon: skill.toLowerCase().replace(/[^a-z0-9]/g, ""),
    direction: `${skill}의 기본 개념을 익히고 간단한 예제를 직접 만들어 보기`,
    duration: "2주",
  };
}
