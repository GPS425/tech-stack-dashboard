-- "이 직무만 뽑는 공고" 기술 순위 — API 가 필요할 때 바로 계산 (9/29 결정: DB 에 따로 저장하지 않음)
-- 크롤러의 job_skill_stat(기본 순위: 1/k 가중)과 같은 기준으로 공고를 고른다:
--   진행 중 + 상세 받음 + 통계 제외(is_excluded) 아님 + 기술 1개 이상 + 그 직무 하나만 걸린 공고
-- :jobId 는 job.job_id (API 는 내부 번호만 쓴다)

WITH base AS (
    SELECT p.posting_id
      FROM posting p
      JOIN posting_job pj ON pj.posting_id = p.posting_id AND pj.job_id = :jobId
     WHERE p.is_closed = 0 AND p.detail_at IS NOT NULL AND p.is_excluded = 0
       AND EXISTS (SELECT 1 FROM posting_skill ps WHERE ps.posting_id = p.posting_id)
       AND (SELECT COUNT(*) FROM posting_job x WHERE x.posting_id = p.posting_id) = 1
), total AS (
    SELECT COUNT(*) AS n FROM base
)
SELECT s.skill_id, s.name, s.category,
       COUNT(DISTINCT ps.posting_id)                      AS posting_cnt,   -- 그 기술이 나온 공고 수
       (SELECT n FROM total)                              AS job_total,     -- 단독 공고 수 (보여 줄지 판단에도 씀)
       ROUND(COUNT(DISTINCT ps.posting_id) / NULLIF((SELECT n FROM total), 0), 4) AS ratio
  FROM base b
  JOIN posting_skill ps ON ps.posting_id = b.posting_id
  JOIN skill s ON s.skill_id = ps.skill_id
 GROUP BY s.skill_id, s.name, s.category
 ORDER BY posting_cnt DESC, s.skill_id
 FETCH FIRST 20 ROWS ONLY;

-- 화면 규칙 (9/29 결정)
-- 1. job_total 이 50건 미만이면 이 보기를 숨긴다.
--    공고 수는 매일 바뀌므로 켜는 기준 50, 끄는 기준 40 으로 달리 두거나 최근 7일 평균으로 판단한다
--    (48·52·49 처럼 오가며 날마다 보기가 사라졌다 나타나지 않게).
-- 2. 50~99건: 상위 5개만, 비율 대신 "80건 중 43건"처럼 건수로, 순위 번호 없이 순서만.
--    (공고 80건에서 40% 비율의 오차는 ±11%p 정도라 이웃 순위가 자주 뒤바뀐다)
-- 3. 100건 이상: 기본 순위처럼 상위 20개, 비율과 순위 번호.

-- ------------------------------------------------------------------
-- 기본 순위(job_skill_stat)의 작은 직무 표시 규칙 (9/29 3차 결정)
-- 통계 공고(job_total)가 적은 직무는 가중 비율(ratio)이 흔들리고 부풀려진다
--   (예: BI 엔지니어 VMware 실제 9/56건 = 16% 인데 ratio 34%. 단독 공고가 1, 여러 직무 공고가 1/k 로 들어가서)
-- 1. job_total 100건 이상: 지금처럼 rank_no 순서로 상위 20개, ratio(%)와 순위 번호.
-- 2. job_total 100건 미만: "공고가 적어 순위를 매기지 않습니다" 안내 + 상위 5개를
--    posting_cnt(실제 공고 수) 많은 순으로, "56건 중 21건"처럼 건수로만. 순위 번호와 % 는 보여 주지 않는다.
--    SELECT s.name, st.posting_cnt, st.job_total
--      FROM job_skill_stat st JOIN skill s ON s.skill_id = st.skill_id
--     WHERE st.calc_date = :calcDate AND st.job_id = :jobId
--     ORDER BY st.posting_cnt DESC, st.rank_no
--     FETCH FIRST 5 ROWS ONLY;
-- 3. 100건 근처에서 날마다 모드가 바뀌지 않게 켜는 기준 100, 끄는 기준 80 (또는 최근 7일 평균).
-- 헤드헌팅 공고는 빼지 않는다: 큰 직무 순위는 거의 그대로이고, 크게 흔들리는 작은 직무는 2번 규칙이 흡수한다.
