import reactLogo from "../assets/react.svg";

export function TechnologyIcon({ icon }) {
  switch (icon) {
    case "java":
      return (
        <svg viewBox="0 0 28 36" aria-hidden="true">
          <g fill="none" strokeLinecap="round" strokeWidth="1.1">
            <path stroke="#f44336" d="M16 1c-7 5 5 5-2 10M20 5c-8 4 3 6-3 9" />
            <path stroke="#2487b7" d="M7 16c-4 2 13 3 15 0M6 20c-3 3 15 3 17 0M9 15l2 10c3 2 7 2 9 0l2-10M22 16c7-1 5 6 0 6M5 28c4 2 17 2 20-1M8 31h15" />
          </g>
          <text x="14" y="36" textAnchor="middle" fill="#ed3b32" fontSize="9" fontFamily="Arial, sans-serif">Java</text>
        </svg>
      );
    case "spring":
      return (
        <svg viewBox="0 0 28 28" aria-hidden="true">
          <circle cx="14" cy="14" r="12" fill="#6db33f" />
          <path d="M5 18C8 8 20 13 25 5c1 10-6 20-17 16 5 0 12-5 15-10-5 5-11 7-18 7Z" fill="#fff" />
        </svg>
      );
    case "aws":
      return (
        <svg viewBox="0 0 32 28" aria-hidden="true">
          <text x="1" y="17" fill="#263243" fontSize="17" fontFamily="Arial, sans-serif">aws</text>
          <path d="M3 22c8 5 18 5 26-1m-4-1 5 1-2 4" fill="none" stroke="#ff9900" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "sql":
      return (
        <svg viewBox="0 0 36 28" aria-hidden="true">
          <g fill="none" stroke="#f36b21" strokeWidth="1.4">
            <ellipse cx="9" cy="6" rx="7" ry="3" />
            <path d="M2 6v15c0 4 14 4 14 0V6M2 11c0 4 14 4 14 0M2 16c0 4 14 4 14 0" />
          </g>
          <text x="19" y="17" fill="#f36b21" fontSize="9" fontWeight="700" fontFamily="Arial, sans-serif">SQL</text>
        </svg>
      );
    case "python":
      return (
        <svg viewBox="0 0 28 28" aria-hidden="true">
          <path fill="#3776ab" d="M14 2c-6 0-6 2-6 5v3h8v1H5c-5 0-5 11 0 11h3v-5c0-3 2-5 5-5h6c3 0 4-2 4-5V6c0-3-4-4-9-4Z" />
          <path fill="#ffd343" d="M14 26c6 0 6-2 6-5v-3h-8v-1h11c5 0 5-11 0-11h-3v5c0 3-2 5-5 5H9c-3 0-4 2-4 5v1c0 3 4 4 9 4Z" />
          <circle cx="11" cy="6" r="1.2" fill="#fff" />
          <circle cx="17" cy="22" r="1.2" fill="#fff" />
        </svg>
      );
    case "react":
      return <img src={reactLogo} alt="" />;
    default:
      return <span className={`technology-monogram technology-monogram--${icon}`}>{({ javascript: "JS", typescript: "TS", node: "N", html: "5", kotlin: "K", swift: "S", flutter: "F", docker: "D", kubernetes: "K8", linux: "L", tensorflow: "TF", pytorch: "PT", spark: "SP", airflow: "AF", excel: "X", tableau: "T" })[icon] || icon.slice(0, 2).toUpperCase()}</span>;
  }
}

function TechStackList({ stacks, role }) {
  return (
    <ol
      className="tech-stack-list"
      aria-label={`${role} 인기 기술스택 TOP 5`}
      style={{ "--stack-name-width": stacks.some((stack) => stack.name.length > 6) ? "82px" : "44px" }}
    >
      {stacks.map((stack, index) => (
        <li className="tech-stack-row" key={stack.name}>
          <span className="stack-rank">{String(index + 1).padStart(2, "0")}</span>
          <span className={`technology-icon technology-icon--${stack.icon}`}><TechnologyIcon icon={stack.icon} /></span>
          <span className="stack-name">{stack.name}</span>
          <div className="stack-progress" role="progressbar" aria-label={`${stack.name} 채용공고 사용 비율`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={stack.percentage}>
            <span className="stack-progress-fill" style={{ width: `${stack.percentage}%` }} />
          </div>
          <span className="stack-percentage">{stack.percentage}%</span>
        </li>
      ))}
    </ol>
  );
}

export default TechStackList;
