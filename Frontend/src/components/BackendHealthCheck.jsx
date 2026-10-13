import { useState } from "react";

function BackendHealthCheck() {
  const [result, setResult] = useState("아직 요청 안 함");

  async function checkBackend() {
    setResult("연결 확인 중...");

    try {
      const response = await fetch("/api/health");

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      setResult(JSON.stringify(data, null, 2));
    } catch (error) {
      setResult(`연결 실패: ${error.message}`);
    }
  }

  return (
    <main className="health-check">
      <a className="brand" href="./">JOBSTACK</a>
      <h1>백엔드 연결 테스트</h1>
      <button className="primary-button" onClick={checkBackend}>연결 확인</button>
      <pre aria-live="polite">{result}</pre>
      <a className="text-link" href="./">메인 화면으로 돌아가기</a>
    </main>
  );
}

export default BackendHealthCheck;
