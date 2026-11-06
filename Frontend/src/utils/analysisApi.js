import { getLearningGuide } from "../data/learningGuides.js";

// POST /api/analysis 응답 -> 분석 화면 컴포넌트가 쓰는 모양
export function toAnalysis(response, ownedSkills) {
  const { prioritySkills, missingSkills, marketSkills, matchedSkills } = response;
  // 우선순위: 서버가 정한 핵심 보완 기술(상위 3) 다음에 나머지 부족 기술 (시장 비율 순)
  const ordered = [...prioritySkills, ...missingSkills.filter((skill) => !prioritySkills.includes(skill))];
  return {
    jobId: response.jobId,
    jobLabel: response.name,
    fitScore: response.fitScore,
    matchedCount: matchedSkills.length,
    marketSkills,
    ownedSkills,
    missingSkills,
    prioritySkills,
    unknownSkills: response.unknownSkills || [],
    coverage: marketSkills.length ? Math.round(matchedSkills.length / marketSkills.length * 100) : 0,
    learningRoadmap: ordered.map((skill, index) => ({ priority: index + 1, skill, ...getLearningGuide(skill) })),
  };
}

export async function postAnalysis(jobId, skills, signal) {
  const response = await fetch("/api/analysis", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ jobId: Number(jobId), skills }),
    signal,
  });
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return response.json();
}
