# db

Oracle 21c XE (XEPDB1) 의 팀 스키마 `capstone`.

| 파일 | 내용 |
|---|---|
| `01-schema.sql` | 테이블·제약. 9/28 설계 초안 + 2차에서 `posting.is_excluded` 추가 |
| `api-표시규칙.sql` | API 가 쓸 조회와 화면 규칙: '이 직무만' 순위, 공고가 적은 직무 표시 |

- 기준 데이터(job·skill·skill_alias)는 여기서 넣지 않고 크롤러가 CSV 로 넣습니다 (한글 깨짐 방지).
- 한 번 적용한 스키마는 고치지 않고, 바꿀 것은 `ALTER TABLE` 로 새 파일에 적습니다 (Flyway 를 쓰면 V1, V2 …).

## job_skill_stat 읽는 법 (2차에서 바뀜)

- `ratio`: 여러 직무에 걸린 공고를 직무 수로 나눠(1/k) 센 가중 비율. 순위(`rank_no`)도 이 순서
- `posting_cnt`·`job_total`: 실제 공고 수. 그래서 `posting_cnt / job_total` 과 `ratio` 는 다름
- 분모(`job_total`): 진행 중 + 상세 받음 + 통계 제외 아님 + 기술 1개 이상인 공고
- `job_total` 100건 미만인 직무는 순위·비율 없이 공고 수만 보여 줌 (`api-표시규칙.sql`)
