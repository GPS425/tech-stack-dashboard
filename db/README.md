# db

Oracle 21c XE (XEPDB1) 의 팀 스키마 `capstone`.

| 파일 | 내용 |
|---|---|
| `01-schema.sql` | 테이블·제약. 9/28 설계 초안 + 2차에서 `posting.is_excluded` 추가 |
| `02-review-fix.sql` | 10/1 크롤링 검수: `skill.is_active`, `posting_job.last_seen_at`, `posting.raw_deleted_at`, `crawl_run.heartbeat_at`, RUNNING 한 건만(`uk_run_running`). 새 크롤러보다 먼저 적용 |
| `api-표시규칙.sql` | API 가 쓸 조회와 화면 규칙: '이 직무만' 순위, 공고가 적은 직무 표시 |

- 기준 데이터(job·skill·skill_alias)는 여기서 넣지 않고 크롤러가 CSV 로 넣습니다 (한글 깨짐 방지).
- 이미 돌고 있는 DB 에 새 파일을 적용할 때: 크롤러를 멈추고 `docker exec -i <오라클 컨테이너> sqlplus -s / as sysdba < db/02-review-fix.sql` (02 는 다시 돌려도 됨)
- 한 번 적용한 스키마는 고치지 않고, 바꿀 것은 `ALTER TABLE` 로 새 파일에 적습니다 (Flyway 를 쓰면 V1, V2 …).

## job_skill_stat 읽는 법 (2차에서 바뀜)

- `ratio`: 여러 직무에 걸린 공고를 직무 수로 나눠(1/k) 센 가중 비율. 순위(`rank_no`)도 이 순서
- `posting_cnt`·`job_total`: 실제 공고 수. 그래서 `posting_cnt / job_total` 과 `ratio` 는 다름
- 분모(`job_total`): 진행 중 + 상세 받음 + 통계 제외 아님 + 기술 1개 이상인 공고
- `job_total` 100건 미만인 직무는 순위·비율 없이 공고 수만 보여 줌. 날마다 오가지 않게 켜는 기준 100, 끄는 기준 80 (`api-표시규칙.sql`)
- API 는 날짜를 오늘로 넘기지 말고 `(SELECT MAX(calc_date) FROM job_skill_stat)` 를 씁니다. 그날 ⑤가 끝나기 전(04시대)이나 수집이 실패한 날에도 빈 화면이 안 나옵니다
- `skill.is_active = 0` 인 기술(skill.csv 에서 뺀 것)은 ⑤에 안 들어갑니다. 기술 목록을 보여 줄 때도 거릅니다
- `posting_job` 은 공고가 지금 걸린 직무입니다. 끝까지 받은 직무의 목록에서 빠진 진행 중 공고는 ②가 연결을 끊습니다(10/1 부터)

## 아직 안 쓰는 칸

- `posting.is_mass_hiring`: 계산하는 코드가 없어 늘 0 입니다. 기준을 정하기 전에는 화면·API 에서 쓰지 않습니다
