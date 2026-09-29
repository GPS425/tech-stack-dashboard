# jobtrend-crawler

사람인 개발 직무 채용공고를 모아 직무별 기술 통계를 만드는 크롤러입니다. API 서버와 따로 도는 앱이고, 결과는 DB 에만 씁니다.

- 대상: 사람인 개발 직무 17개 (`crawler.jobs`, 사람인 직무 코드)
- 흐름: ① 목록 → ② 마감 처리 → ③ 상세(자격요건·우대·태그) → ④ 기술 추출 → ⑤ 직무별 기술 통계(`job_skill_stat`)
- 한 번 도는 데 약 6분 (이미 받은 공고는 상세를 다시 받지 않음)
- 자세한 과정·결과·트러블슈팅은 노션 "크롤링" 아래 1~3차 결과 페이지

## 실행

Java 21 이 필요합니다.

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
| `CRAWLER_LOCAL_ONLY=true` | 사람인에 요청하지 않고 ④·⑤만 다시 (기술 표·규칙을 바꾼 뒤) |
| `--crawler.report-file=out.json` | DB 숫자를 JSON 으로 뽑고 끝남 (읽기만) |

## 기술 표 고치기

`src/main/resources/seed/` 의 CSV 를 고치면 다음 실행 때 DB 에 반영됩니다.

- `skill.csv`: 기술 이름·분류 (사람인 코드가 있으면 코드로 맞춤)
- `skill_alias.csv`: 글에서 찾을 별칭. 세 번째 칸이 0 이면 태그 이름으로만 맞춤 (go·c·r 처럼 일반 낱말과 겹치는 것)
- `saramin_codes.csv`: 사람인 IT 코드표

## 지킬 것

- 사람인 원문(`raw/`, 저장한 HTML)은 저장소에 올리지 않습니다 (사람인 약관). 원문은 마감 7일 뒤 지웁니다.
- 요청은 1~2초 간격으로 하나씩. 403·3xx 는 바로 멈추고, 429 는 재시도 뒤에도 오면 멈춥니다.
- 사람인에 많이 요청하는 실행(직무 추가, 전체 다시 받기)은 팀에 먼저 알립니다.
