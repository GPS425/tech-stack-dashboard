# db

Oracle 21c XE (XEPDB1) 의 팀 스키마 `capstone`.

| 파일 | 내용 |
|---|---|
| `01-schema.sql` | 테이블·제약. 9/28 설계 초안 + 2차에서 `posting.is_excluded` 추가 |
| `02-review-fix.sql` | 10/1 크롤링 검수: `skill.is_active`, `posting_job.last_seen_at`, `posting.raw_deleted_at`, `crawl_run.heartbeat_at`, RUNNING 한 건만(`uk_run_running`). 새 크롤러보다 먼저 적용 |
| `03-dev-data.sql` | **개발용 데이터**. 기준 데이터와 집계 통계는 실제 값, 공고·회사는 지어낸 견본. 빈 DB 에만 넣음 |
| `api-표시규칙.sql` | API 가 쓸 조회와 화면 규칙: '이 직무만' 순위, 공고가 적은 직무 표시 |

- 기준 데이터(job·skill·skill_alias)는 여기서 넣지 않고 크롤러가 CSV 로 넣습니다 (한글 깨짐 방지).
- 이미 돌고 있는 DB 에 새 파일을 적용할 때: 크롤러를 멈추고 `docker exec -i <오라클 컨테이너> sqlplus -s / as sysdba < db/02-review-fix.sql` (02 는 다시 돌려도 됨)
- 한 번 적용한 스키마는 고치지 않고, 바꿀 것은 `ALTER TABLE` 로 새 파일에 적습니다 (Flyway 를 쓰면 V1, V2 …).

## 내 PC 오라클에 개발용 DB 만들기

수집한 실제 공고는 테스트 서버에만 있습니다. 각자 PC 에서는 아래 순서로 표와 개발용 데이터를 넣어 씁니다.

1. 오라클에 팀 계정을 만듭니다 (이미 있으면 건너뜀). 계정 이름이 `CAPSTONE` 이 아니면 세 파일 위쪽의 `CURRENT_SCHEMA = CAPSTONE` 을 자기 계정 이름으로 바꿉니다.
2. `db` 폴더에서 차례로 실행합니다.
   ```
   sqlplus -s / as sysdba @01-schema.sql
   sqlplus -s / as sysdba @02-review-fix.sql
   sqlplus -s / as sysdba @03-dev-data.sql
   ```
3. 마지막에 `job 17, skill 175, alias 446, stat 14307, company 30, posting 124, ...` 가 나오면 끝입니다 (10~20초).

- `03-dev-data.sql` 은 다시 돌려도 됩니다 (지우고 다시 넣음). 실제 수집 데이터가 있는 DB 에서는 지우지 않고 멈춥니다.
- **통계(`job_skill_stat`)는 실제 집계**입니다 (9/29 ~ 10/5, 7일 치). 순위·트렌드 화면은 이 값으로 만들면 됩니다.
- **공고(`posting`)·회사(`company`)는 지어낸 견본**입니다. 제목이 `[견본]` 으로 시작하고 공고번호가 900000001 부터입니다. 사람인 공고 데이터는 저장소에 올리지 않습니다. 그래서 통계의 건수와 견본 공고 수는 맞지 않습니다.
- 한글 값은 `UNISTR('\BC31...')` 로 적혀 있습니다. 어느 PC·도구에서 돌려도 한글이 깨지지 않게 하려는 것이니 손으로 고치지 않습니다.
- 주석은 문장 뒤 같은 줄(`...;  -- 주석`)에 두지 않습니다. sqlplus 가 문장 끝을 못 알아봅니다.

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
