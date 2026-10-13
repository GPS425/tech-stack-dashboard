function JobSkillHeatmap({ matrix, selectedJob }) {
  return (
    <>
      <div className="trend-table-scroll" role="region" aria-label="직무별 핵심 기술 비교 표" tabIndex={0}>
        <table className="trend-heatmap-table">
          <caption className="sr-only">각 직무의 채용공고 중 해당 기술을 요구하는 비율</caption>
          <thead>
            <tr><th scope="col">기술</th>{matrix.columns.map((column) => <th scope="col" key={column.id} className={column.id === selectedJob ? "is-selected" : undefined}>{column.label}</th>)}</tr>
          </thead>
          <tbody>
            {matrix.rows.map((row) => (
              <tr key={row.skill}>
                <th scope="row">{row.skill}</th>
                {row.values.map((value, index) => <td key={matrix.columns[index].id} className={matrix.columns[index].id === selectedJob ? "is-selected" : undefined} style={{ backgroundColor: `hsl(151 36% ${98 - value * 0.32}%)` }}>{value}%</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="trend-heatmap-note">진한 색일수록 기술 요구 비율이 높습니다.</p>
    </>
  );
}

export default JobSkillHeatmap;
