# jobtrend-crawler

사람인 개발 직무 채용공고를 모아 직무별 기술 통계를 만드는 크롤러입니다. API 서버와 따로 도는 앱이고, 결과는 DB 에만 씁니다.

- 대상: 사람인 개발 직무 17개 (`crawler.jobs`, 사람인 직무 코드)
- 흐름: ① 목록 → ② 마감 처리 → ③ 상세(자격요건·우대·태그) → ④ 기술 추출 → ⑤ 직무별 기술 통계(`job_skill_stat`)
- 한 번 도는 데 약 6분 (이미 받은 공고는 상세를 다시 받지 않음)
- 자세한 과정·결과·트러블슈팅은 노션 "크롤링" 아래 1~3차 결과 페이지

## 실행

Java 21 이 필요합니다. DB 에는 `db/01-schema.sql` 다음에 `db/02-review-fix.sql` 이 적용돼 있어야 합니다 (10/1 검수 뒤 크롤러가 새 칸을 씀).

```bash
./gradlew test        # 테스트
./gradlew bootJar     # build/libs/jobtrend-crawler-0.0.1.jar
```

DB 접속은 환경 변수로 줍니다. 비밀번호는 저장소에 올리지 않습니다.

| 변수 | 기본값 | 뜻 |
|---|---|---|
| `DB_HOST` · `DB_PORT` · `DB_SERVICE` | 테스트 서버 · 1521 · XEPDB1 | 오라클 접속 |
| `DB_USER` | capstone | 팀 사용자 |
| `APP_USER_PASSWORD` | (없음) | 팀 사용자 비밀번호. `.env` 에 적고 `CRAWLER_ENV_FILE=file:경로/.env` 로 줄 수도 있음 |

## 실행 모드

| 설정 | 동작 |
|---|---|
| `CRAWLER_RUN_ONCE=true` | 한 번 수집하고 끝남 (지금 테스트 서버 설정) |
| `CRAWLER_ENABLED=true` | 켜 둔 채 매일 04:00(서울) 수집 |
| `CRAWLER_LOCAL_ONLY=true` | 사람인에 요청하지 않고 ④·⑤만 한 번 다시 하고 끝남 (기술 표·규칙을 바꾼 뒤). `CRAWLER_ENABLED` 와 같이 주면 매일 ④·⑤만 |
| `--crawler.report-file=out.json` | DB 숫자를 JSON 으로 뽑고 끝남 (읽기만) |

## 기술 표 고치기

`src/main/resources/seed/` 의 CSV 를 고치면 다음 실행 때 DB 에 반영됩니다.

- `skill.csv`: 기술 이름·코드·분류. 이름으로 찾아 코드·분류를 고치고(코드를 바꾸거나 지워도 됨), 이름이 DB 에 없으면 같은 코드의 행 이름을 바꾸고, 그래도 없으면 넣습니다. 여기서 지운 기술은 DB 에서 지우지 않고 `is_active=0` 으로 꺼서 ④ 태그·통계에서 빠집니다(다시 적으면 켜짐).
  - 주의: 코드가 없는 기술의 이름을 바꾸거나 이름과 코드를 한 번에 바꾸면 새 행이 생기고 옛 행은 꺼져, 지난 통계(`job_skill_stat`)가 두 skill_id 로 갈립니다. 반대로 꺼진 기술의 코드를 새 이름에 주면 그 옛 행의 이름이 바뀌어 지난 공고·통계가 새 이름으로 합쳐집니다.
- `skill_alias.csv`: 별칭. 세 번째 칸(`text_match`)은 0 이나 1 만 됩니다.
  - 1: 태그 이름과 자격요건·우대 글 모두에서 찾음
  - 0: 태그 이름으로만 찾음 (뷰·깃·node·cursor 처럼 다른 뜻과 겹치는 것). 단 `SkillMatcher.LIST_ONLY` 의 go·c·r·node·cursor 는 0 이어도 글에서 다른 기술과 나열됐을 때(`Java, Go`·`Java/Go`·`C/Go`·`JavaㆍGo`·`TypeScript + Node`·`C 및 C++`·`Go와 Rust`·`언어: Go`·`Embedded C`·`C 프로그래밍`·`React, Node`·`Claude, Cursor`)는 찾습니다. `C/S`·`R/R`·`R&D`·`Microsoft(R)`·`Copyright (C)`·`Go-Live`·`Go Live`·`C레벨`·`C 등급`·`C 사`·`Plan A, B, C`·`worker node`·`(Function, Cursor)`·`Cursor 기반 페이지네이션` 은 안 잡습니다.
    - 이 다섯 별칭은 **반드시 0** 이어야 합니다(1 이면 글의 모든 "c"·"go" 가 잡힘). 줄을 지우면 기술 이름 별칭이 1 로 돌아가므로 지우지 않습니다(SeedTest 가 봄).
    - 줄 앞 글머리 `•`·`ㆍ`·`・` 는 `·` 로 바뀝니다. 글 가운데의 `·` 는 나열 기호지만, 줄 앞 글머리 바로 뒤의 별칭은 뒤에 나열 기호나 기술 낱말(개발·언어·서버·기반·백엔드·활용·사용·경험·`(`·버전 등)이 올 때만 잡습니다(`• Go 백엔드 개발` 은 잡고 `• Go Live` 는 안 잡음). `-`·`○` 로 시작하는 줄은 나열일 때만 잡습니다. 줄바꿈은 나열 기호가 아닙니다.
    - 괄호 하나만 둘러싼 `(R)`·`(C)` 는 바로 앞이 영문·숫자일 때만 등록상표·저작권으로 봅니다(`통계 언어(R)` 는 R).
  - 기술 이름(소문자)은 자동으로 1 별칭이 됩니다. 같은 기술이면 이 파일에서 덮을 수 있고(`windows,Windows,0`), 같은 별칭을 다른 기술로 두 번 적거나 다른 기술 이름과 같은 별칭을 적으면 실행이 멈춥니다.
  - 앞뒤 글 때문에 다른 뜻이 되는 것(Apache Flink·License, Cisco IOS, SAS 스토리지·Expander·HBA, SWIFT 전문·망·MT, Unity Catalog, LLM guard rails, k8s node, DB 커서 등)은 `SkillMatcher.EXCEPT` 에 있습니다. 키는 별칭 표에 있어야 합니다(SeedTest 가 봄).
  - SAS 는 공고 전체로도 가릅니다(`SkillMatcher.dropByContext`): 자격요건·우대사항에 스토리지 낱말(SATA·NVMe·SSD·HDD·SCSI·HBA·RAID·스토리지·펌웨어)이 있고 통계 낱말(SPSS·STATA·통계·데이터 분석·SAS/STAT·Viya·Base SAS·`SAS, R` 등)이 없으면 SAS 를 뺍니다.
- `saramin_codes.csv`: 사람인 IT 코드표

## 지킬 것

- 사람인 원문(`raw/`, 저장한 HTML)은 저장소에 올리지 않습니다 (사람인 약관). 원문은 마감 7일 뒤 지웁니다.
- 요청은 1~2초 간격으로 하나씩. 403·3xx 는 바로 멈추고, 429 는 재시도 뒤에도 오면 멈춥니다 (응답이 HTML 이 아니어도 같음).
- 도는 회차는 `crawl_run.heartbeat_at` 을 계속 적습니다. 60분 넘게 끊긴 RUNNING 만 죽은 회차로 보고 정리하고, RUNNING 은 DB 가 한 건만 허용합니다.
- `crawler.jobs` 에서 직무를 빼면 그 직무에만 걸린 진행 중 공고는 다음 회차에 마감되고, 다른 직무에도 걸린 공고는 그 직무와의 연결만 끊깁니다.
- 사람인에 많이 요청하는 실행(직무 추가, 전체 다시 받기)은 팀에 먼저 알립니다.
