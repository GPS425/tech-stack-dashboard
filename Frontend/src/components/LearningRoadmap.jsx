import { TechnologyIcon } from "./TechStackList.jsx";

function LearningRoadmap({ items }) {
  if (items.length === 0) {
    return <p className="analysis-roadmap-empty">추가 학습이 필요한 시장 요구 기술이 없습니다. 보유 기술을 프로젝트에 적용하며 경험을 넓혀보세요.</p>;
  }

  return (
    <div className="analysis-roadmap-container">
      <table className="analysis-roadmap-table">
        <caption className="sr-only">부족 기술의 우선순위, 추천 학습 방향과 예상 소요 시간</caption>
        <thead><tr><th scope="col">우선순위</th><th scope="col">기술명</th><th scope="col">추천 학습 방향</th><th scope="col">예상 소요 시간</th></tr></thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.skill}>
              <td data-label="우선순위"><span className="analysis-learning-priority">{item.priority}</span></td>
              <th scope="row" className="analysis-learning-skill" data-label="기술명">
                <span className="analysis-learning-skill-content"><span className={`technology-icon technology-icon--${item.icon}`} aria-hidden="true"><TechnologyIcon icon={item.icon} /></span><span className="analysis-learning-skill-name">{item.skill}</span></span>
              </th>
              <td className="analysis-learning-direction" data-label="추천 학습 방향">{item.direction}</td>
              <td className="analysis-learning-duration" data-label="예상 소요 시간">{item.duration}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default LearningRoadmap;
