function TechCombinationList({ combinations }) {
  return (
    <div className="trend-combination-table-container">
      <table className="trend-combination-table">
        <caption className="sr-only">같은 채용공고에서 함께 언급된 기술 조합 TOP 5</caption>
        <thead><tr><th scope="col">순위</th><th scope="col">기술 조합</th><th scope="col">공고 수</th></tr></thead>
        <tbody>
          {combinations.map((combination, index) => (
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
