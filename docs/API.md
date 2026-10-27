# API 명세 (초안)

- 상태: **초안**. 확정 전에는 주소·칸 이름이 바뀔 수 있습니다.
- 담당: 백엔드 김동희
- `jobId` 는 DB 의 `job.job_id`(내부 번호)입니다. 사람인 직무 코드(84 등)가 아닙니다. 예: 백엔드 1, 프론트엔드 23, 데이터분석가 35, 게임개발 37
- 가정: 직무는 수집하는 **사람인 개발 직무 17개를 그대로** 씁니다(프론트 목업의 8개와 다름 — 팀 결정 대기).
- 모든 응답은 JSON, 오류는 아래 "공통 규칙"의 오류 모양.

## 목록

아래 표가 API 목록의 원본입니다. 열(`| 단계 | 메서드 | 주소 | 설명 |`)은 그대로 두고 줄만 고쳐 주세요.

| 단계 | 메서드 | 주소 | 설명 |
|---|---|---|---|
| 0 | GET | /api/health | 서버 상태 ✅ 구현 |
| 0 | GET | /api/health/db | DB 연결 ✅ 구현 |
| 2 | GET | /api/jobs | 직무 목록 ✅ 구현 |
| 2 | GET | /api/skills | 기술 목록·검색 ✅ 구현 |
| 2 | GET | /api/summary | 요약 숫자 (공고·기술·직무 수, 기준 날짜) ✅ 구현 |
| 3 | GET | /api/jobs/{jobId}/skills | 직무별 기술 순위 ✅ 구현 (소규모 직무 규칙은 미구현) |
| 4 | GET | /api/trends/job-demand | 직무별 공고 수와 비율 ✅ 구현 |
| 4 | GET | /api/trends/matrix | 직무 × 기술 비율 표 |
| 4 | GET | /api/trends/combinations | 함께 요구되는 기술 짝 |
| 5 | POST | /api/analysis | 내 기술로 직무 적합도 분석 |
| 6 | GET | /api/postings | 공고 목록 (검색·필터·정렬·쪽) |
| 6 | GET | /api/postings/{postingId} | 공고 상세 |

## 공통 규칙

- **기준 날짜**: 순위·비율은 `job_skill_stat` 의 가장 최근 날짜(`MAX(calc_date)`)를 씁니다. 오늘 날짜로 찾지 않습니다(새벽 수집 전이나 수집이 실패한 날에도 빈 화면이 안 나오게). 응답마다 `calcDate` 를 넣습니다.
- **비율**: `percentage` 는 0~100, 소수 한 자리(예: 43.6). 여러 직무에 걸린 공고는 직무 수로 나눠 센 가중 비율입니다(`job_skill_stat.ratio × 100`).
- **건수**: `postingCount` 는 실제 공고 수입니다. 그래서 `postingCount / jobTotal` 과 `percentage` 는 조금 다를 수 있습니다.
- **작은 직무 (⚠ 아직 미구현 — 현재는 `smallSample` 값만 내려주고 순위·비율은 그대로 줍니다)**: 통계 공고(`jobTotal`)가 100건 미만인 직무는 `smallSample: true` 로 표시하고, 순위·비율 대신 건수만 줍니다(`rank`·`percentage` 는 null, 건수 많은 순 상위 5개). 날마다 오가지 않게 켜는 기준 100, 끄는 기준 80 (`db/api-표시규칙.sql`).
- **쓰지 않는 기술**: `skill.is_active = 0` 인 기술은 어디에도 내보내지 않습니다.
- **공고 원문**: 약관 때문에 공고 본문 글은 주지 않습니다. 제목·회사·조건·기술 태그와 사람인 원문 주소(`url`)만 줍니다.
- **날짜 형식**: `2026-10-23`, 시각은 `2026-10-23T04:18:06` (한국 시간).
- **오류 모양**:

```json
{ "status": 404, "code": "JOB_NOT_FOUND", "message": "직무를 찾을 수 없습니다: 999" }
```

| 상태 | 언제 |
|---|---|
| 400 | 파라미터가 잘못됨 (예: 기술 11개 이상, 모르는 정렬값). 구현: `limit` 범위 밖이면 `INVALID_LIMIT` |
| 404 | 없는 직무·공고 |
| 500 | 서버 오류 (DB 연결 등) |

## 구현 방식 (섞어 쓰기)

| 단계 | API | DB 접근 | 이유 |
|---|---|---|---|
| 2~5 | 직무·기술·요약, 순위, 트렌드, 역량 분석 | JdbcTemplate | 집계 SQL(1/k 가중 비율, 기술 짝 등)을 그대로 쓴다. 크롤러도 같은 방식 |
| 6 | 공고 목록·상세 | 스프링 데이터 JPA | 검색·필터·정렬·쪽 나누기(Pageable)가 편하다 |

- **`spring.jpa.hibernate.ddl-auto=none`** 필수. 개발 중에도 테스트 서버의 실제 수집 DB 에 붙으므로, `create`·`update` 면 켜는 순간 수집 표가 바뀌거나 지워질 수 있다. 표는 `db/*.sql` 로만 바꾼다
- 백엔드는 읽기만 한다. 공고·통계를 쓰는 건 크롤러뿐이다
- 엔티티는 단계 6 에 필요한 것만 만든다(`Posting`, `Company`, 직무·기술 연결). 통계 표(`job_skill_stat`)는 엔티티로 만들지 않는다

---

## 2단계: 기준 데이터

### GET /api/jobs
직무 목록. `job.sort_order` 순.

```json
[
  { "jobId": 1, "name": "백엔드/서버개발", "postingCount": 1736, "jobTotal": 1254, "smallSample": false },
  { "jobId": 37, "name": "게임개발", "postingCount": 115, "jobTotal": 48, "smallSample": true }
]
```
- `postingCount`: 진행 중 공고 수, `jobTotal`: 통계 공고 수(순위의 분모)
- 쓰는 화면: 직무 선택(메인·트렌드·역량 입력·공고 필터)

### GET /api/skills?q=spr&limit=20
기술 목록·검색. `q` 가 없으면 전체(`limit` 기본 200). 다른 표기(`skill_alias`)로도 찾습니다(예: "스프링" → Spring).

```json
[
  { "skillId": 12, "name": "Spring", "category": "FRAMEWORK" },
  { "skillId": 13, "name": "Spring Boot", "category": "FRAMEWORK" }
]
```
- `limit` 기본 200, 1~500 (범위 밖이면 400 `INVALID_LIMIT`)
- `category`: LANGUAGE · FRAMEWORK · DATABASE · CLOUD_INFRA · DEVOPS_TOOL · AI_DATA · MOBILE · ETC
- 쓰는 화면: 역량 입력의 기술 검색

### GET /api/summary
```json
{ "calcDate": "2026-10-23", "postingCount": 4255, "statPostingCount": 2508, "skillCount": 175, "jobCount": 17 }
```
- 쓰는 화면: 메인 "분석 현황 요약", 트렌드 "시장 현황 요약"

## 3단계: 직무별 기술 순위

### GET /api/jobs/{jobId}/skills?limit=5
```json
{
  "jobId": 1, "name": "백엔드/서버개발", "calcDate": "2026-10-23",
  "jobTotal": 1232, "smallSample": false,
  "items": [
    { "rank": 1, "skillId": 1, "name": "Java", "category": "LANGUAGE", "percentage": 43.6, "postingCount": 582 },
    { "rank": 2, "skillId": 7, "name": "Python", "category": "LANGUAGE", "percentage": 31.9, "postingCount": 393 }
  ]
}
```
- `limit` 기본 5, 1~50 (범위 밖이면 400 `INVALID_LIMIT`)
- 쓰는 화면: 메인 "직무별 인기 기술 TOP 5", 트렌드 "직무별 인기 기술", 역량 입력 "추천 기술"(limit=6)

## 4단계: 트렌드

### GET /api/trends/job-demand
```json
{
  "calcDate": "2026-10-23",
  "items": [
    { "jobId": 1, "name": "백엔드/서버개발", "postingCount": 1736, "percentage": 21.3 }
  ]
}
```
- `percentage`: 공고 하나가 여러 직무에 걸리므로 직무 수로 나눠 센 비중(합 100%). 건수 합은 전체 공고 수보다 큽니다.
- 쓰는 화면: 트렌드 "직무별 채용공고 현황"

### GET /api/trends/matrix?jobIds=1,23,35&limit=6
직무 × 기술 비율 표. 기술은 고른 직무들에서 많이 나온 순 `limit` 개.

```json
{
  "calcDate": "2026-10-23",
  "jobs": [ { "jobId": 1, "name": "백엔드/서버개발" }, { "jobId": 23, "name": "프론트엔드" } ],
  "rows": [ { "name": "Java", "values": [43.6, 12.0] } ]
}
```
- 값이 없으면 0
- 쓰는 화면: 트렌드 "직무별 핵심 기술 비교"

### GET /api/trends/combinations?jobId=1&limit=5
같은 공고에 함께 나온 기술 짝. `jobId` 가 없으면 전체.

```json
{ "jobId": 1, "items": [ { "skills": ["Java", "Spring Boot"], "postingCount": 241 } ] }
```
- 진행 중 + 통계에 들어간 공고 기준
- 쓰는 화면: 트렌드 "기술 조합"

## 5단계: 역량 분석

### POST /api/analysis
요청:
```json
{ "jobId": 1, "skills": ["Java", "Spring", "MySQL"] }
```
- `skills` 1~10개, 이름이나 다른 표기 모두 됨(서버가 `skill_alias` 로 맞춤). 모르는 이름은 `unknownSkills` 로 돌려줌

응답:
```json
{
  "jobId": 1, "name": "백엔드/서버개발", "calcDate": "2026-10-23",
  "fitScore": 52,
  "marketSkills": ["Java", "Python", "Linux", "AWS", "Git", "Spring Boot", "MySQL", "Docker", "Spring", "JavaScript"],
  "matchedSkills": ["Java", "MySQL", "Spring"],
  "missingSkills": ["Python", "Linux", "AWS", "Git", "Spring Boot", "Docker", "JavaScript"],
  "prioritySkills": ["Python", "Linux", "AWS"],
  "unknownSkills": []
}
```
- `marketSkills`: 그 직무 상위 10개 기술
- `fitScore` (0~100, **계산식은 팀과 정할 것**, 제안): 상위 10개의 비율을 가중치로, 가진 기술의 가중치 합 ÷ 전체 가중치 합 × 100
- `prioritySkills`: 부족한 기술 중 비율 높은 순 3개
- 학습 방향·기간 문구는 프론트가 가진 것을 씁니다(DB 에 없음)
- 맞는 공고 추천은 `/api/postings?sort=matching&mySkills=…` 를 다시 씁니다
- 쓰는 화면: 역량 분석

## 6단계: 공고

### GET /api/postings
| 파라미터 | 예 | 뜻 |
|---|---|---|
| `q` | `spring` | 제목·회사·기술 이름 검색 |
| `jobIds` | `1,23` | 직무 (여러 개면 하나라도 걸린 공고) |
| `skills` | `Java,Docker` | 기술 (모두 가진 공고) |
| `career` | `NEW` | `NEW`(신입 가능: 신입·신입/경력·경력무관) · `EXP1` · `EXP3` · `EXP5`(최소 경력 N년 이상 요구) |
| `employment` | `정규직` | 고용 형태 |
| `region` | `서울` | 시·도 (`region` 칸의 앞 단어) |
| `sort` | `latest` | `latest`(등록순) · `deadline`(마감 임박순) · `matching`(내 기술 일치순, `mySkills` 필요) |
| `mySkills` | `Java,Spring` | 일치율 계산용 |
| `page` · `size` | `1` · `10` | 쪽 (size 최대 50) |

```json
{
  "page": 1, "size": 10, "total": 1736,
  "items": [
    {
      "postingId": 55012345, "company": "○○소프트", "title": "백엔드 개발자 (Java/Spring)",
      "jobs": ["백엔드/서버개발", "웹개발"],
      "career": "경력 3년 이상", "minExperience": 3, "employmentType": "정규직", "region": "서울",
      "skills": ["Java", "Spring Boot", "MySQL"],
      "postedOn": "2026-10-20", "deadlineOn": "2026-11-10", "deadlineType": "DATE",
      "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=55012345",
      "matchRate": null
    }
  ]
}
```
- 진행 중 공고만. 통계 제외 공고도 목록에는 나옵니다(통계에서만 뺌)
- `matchRate`: `mySkills` 를 줬을 때만, 공고 기술 중 가진 비율(0~100)
- 마감 종류 `deadlineType`: DATE(날짜) · UNTIL_FILLED(채용시) · ALWAYS(상시) · ROLLING(수시)
- 쓰는 화면: 공고 목록, 역량 분석의 추천 공고

### GET /api/postings/{postingId}
목록의 한 줄과 같은 모양에 `education`(학력) 을 더합니다. 없는 번호는 404. 마감된 공고는 `closed: true`.

---

## 정할 것 (확정 전)
| 항목 | 지금 가정 | 누가 |
|---|---|---|
| 직무 목록 | 사람인 17개 그대로 | 팀장 |
| 적합도 계산식 | 상위 10개 비율 가중 | 팀 |
| 직무별 비중 계산 | 직무 수로 나눈 비중(합 100%) | 팀 |
| 공고 목록 기본 정렬 | 등록순 | 팀 |
| 회사 로고 자리 | 회사 이름 첫 글자 (로고는 수집 안 함) | 프론트 |
