// status: loading(불러오는 중) · error(불러오지 못함) · unavailable(DB 에 없는 직무) · done(받음)
const STATUS_MESSAGES = {
  loading: "불러오는 중입니다…",
  error: "기술 조합을 불러오지 못했습니다.",
  unavailable: "이 직무는 아직 데이터가 없습니다.",
};

function TechCombinationList({ combinations, status = "done" }) {
  const message = status !== "done" ? STATUS_MESSAGES[status] : combinations.length === 0 ? "표시할 기술 조합이 없습니다." : null;
  return (
    <div className="trend-combination-table-container">
      <table className="trend-combination-table" aria-busy={status === "loading"}>
        <caption className="sr-only">같은 채용공고에서 함께 언급된 기술 조합 TOP 5</caption>
        <thead><tr><th scope="col">순위</th><th scope="col">기술 조합</th><th scope="col">공고 수</th></tr></thead>
        <tbody>
          {message && <tr><td colSpan={3}>{message}</td></tr>}
          {status === "done" && combinations.map((combination, index) => (
            <tr key={combination.skills.join("+")}>
              <td><span className="trend-combination-rank">{String(index + 1).padStart(2, "0")}</span></td>
              <th scope="row">{combination.skills.join(" + ")}</th>
              <td>{combination.count.toLocaleString("ko-KR")}건</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default TechCombinationList;
