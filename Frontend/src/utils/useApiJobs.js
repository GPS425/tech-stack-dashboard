import { useEffect, useState } from "react";
import { fetchJson, isScheduled, toJob } from "./postingApi.js";

// 추천·관련 공고용: url 을 불러와 카드용 공고로 바꾸고, 접수 예정 공고는 빼고 limit 개만 돌려줍니다.
// (url 이 null 이면 아무것도 하지 않습니다)
export function useApiJobs(url, limit = 3) {
  const [result, setResult] = useState({ key: null, jobs: [], failed: false });
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!url) return undefined;
    const controller = new AbortController();
    fetchJson(url, controller.signal)
      .then((data) => setResult({ key: url, jobs: data.items.map(toJob).filter((job) => !isScheduled(job)).slice(0, limit), failed: false }))
      .catch((error) => { if (error.name !== "AbortError") { console.error("공고 불러오기 실패", error); setResult({ key: url, jobs: [], failed: true }); } });
    return () => controller.abort();
  }, [url, limit, retryCount]);

  const current = Boolean(url) && result.key === url;
  function retry() {
    setResult({ key: null, jobs: [], failed: false });
    setRetryCount((count) => count + 1);
  }
  return { jobs: current ? result.jobs : [], loading: Boolean(url) && !current, failed: current && result.failed, retry };
}
