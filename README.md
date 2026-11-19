# JOBSTACK (tech-stack-dashboard)

개발자 채용공고에서 요구하는 **기술 스택 트렌드**를 분석하고, 내가 가진 기술과 비교해 **역량을 진단**해 주는 웹 서비스입니다. (졸업 캡스톤 프로젝트)

> 이 문서는 초안입니다. `[채워 주세요]` 표시는 팀이 직접 확인해서 채웁니다.

## 주요 기능

- 직무별 요구 기술 TOP 순위, 직무 수요·기술 조합 트렌드
- 내 기술 분석: 직무와 보유 기술을 고르면 부족한 기술과 맞는 공고를 보여 줌
- 채용공고 목록 (기술 일치율 순 정렬)
- 사람인 개발 직무 채용공고를 매일 수집하는 크롤러

`[채워 주세요: 화면 캡처 2~3장 — docs/images/ 에 넣고 여기에 연결]`

## 구성

```
브라우저 ─▶ web (nginx, 화면 + /api 전달) ─▶ backend (Spring Boot) ─▶ Oracle XE
                                                         ▲
                                          crawler (사람인 수집) ─┘
```

| 폴더 | 역할 | 기술 |
|---|---|---|
| `Frontend/` | 화면 | React, Vite |
| `Backend/` | REST API | Java 21, Spring Boot 3.5, JDBC/JPA |
| `jobtrend-crawler/` | 채용공고 수집·기술 추출·통계 | Java 21, Spring Boot |
| `db/` | 스키마와 개발용 데이터 SQL | Oracle XE 21c (XEPDB1) |
| `deploy/` | 시연 서버(NAS) 배포 파일 | Docker Compose, nginx |
| `docs/` | API 명세, 성능 테스트 기록 | |

각 폴더의 자세한 내용: [db](db/README.md) · [크롤러](jobtrend-crawler/README.md) · [API 명세](docs/API.md) · [배포](deploy/README.md)

## 따라 해 보기 (로컬 실행)

필요한 것: Java 21, Node.js `[채워 주세요: 버전]`, Docker (Oracle용)

### 1. DB 준비

`gvenzl/oracle-xe` 이미지는 `APP_USER` 환경변수를 주면 처음 만들 때 팀 사용자를 자동으로 만듭니다. `db/01-schema.sql` 을 초기화 폴더에 연결하면 테이블까지 한 번에 만들어집니다.

```bash
docker run -d --name capstone-oracle -p 1521:1521 \
  -e ORACLE_PASSWORD=<관리자 비밀번호> \
  -e APP_USER=capstone \
  -e APP_USER_PASSWORD=<capstone 사용자 비밀번호> \
  -v "$(pwd)/db/01-schema.sql:/container-entrypoint-initdb.d/01-schema.sql:ro" \
  gvenzl/oracle-xe:21-slim
```

처음 시작에 몇 분 걸립니다 (`docker logs -f capstone-oracle` 에 `DATABASE IS READY TO USE!` 가 나올 때까지). 그 뒤 `db/02-review-fix.sql` → `db/04-primary-job.sql` 을 순서대로 적용하고, 개발용 견본 데이터가 필요하면 빈 DB 에만 `db/03-dev-data.sql` 을 넣습니다. 적용 명령은 [db/README.md](db/README.md) 를 보세요. 기준 데이터(직무·기술)는 크롤러가 처음 실행할 때 넣습니다.

### 2. 백엔드

```bash
cd Backend
./gradlew bootRun          # http://localhost:8080
```

DB 접속 정보는 `Backend/src/main/resources/application.properties` 의 `spring.datasource.*` 이고, 비밀번호는 환경변수 `DB_PASSWORD` 로 받습니다. **비밀번호는 저장소에 올리지 마세요.**

```bash
# Windows PowerShell
$env:DB_PASSWORD="<capstone 사용자 비밀번호>"; ./gradlew bootRun
# macOS / Linux
DB_PASSWORD="<capstone 사용자 비밀번호>" ./gradlew bootRun
```

DB 주소를 바꿔야 하면 `SPRING_DATASOURCE_URL` 환경변수로 덮어쓸 수 있습니다 (배포용 compose 가 이 방식을 씁니다).

### 3. 프론트엔드

```bash
cd Frontend
npm install
npm run dev                # 개발 서버. /api 는 localhost:8080 으로 전달됨
```

### 4. 크롤러 (선택)

```bash
cd jobtrend-crawler
./gradlew bootJar
```

환경변수와 실행 모드는 [크롤러 README](jobtrend-crawler/README.md) 를 보세요. 사람인에 요청을 보내므로 약관·요청 간격 규칙을 지켜야 합니다.

## 시연 서버 배포 (현재: NAS + Docker Compose)

NAS의 Docker에서 backend(jar), web(nginx), Oracle XE, 크롤러를 컨테이너로 돌립니다. 배포 파일과 순서는 [deploy/README.md](deploy/README.md) 에 있습니다.

```bash
# deploy/ 폴더에서
cp .env.example .env       # DB_PASSWORD 만 직접 입력
docker compose up -d       # http://서버주소:8088
```

화면 파일은 `Frontend` 에서 `npm run build` 한 `dist/` 안의 파일을 `deploy/web/` 에 넣습니다. 백엔드는 `./gradlew bootJar` 로 만든 jar 를 `deploy/backend/app.jar` 로 둡니다.

외부 공개는 Cloudflare 임시 터널을 쓰고 있어, **주소가 터널을 다시 만들 때마다 바뀝니다.**

## 접속 주소

- 시연 서버(같은 네트워크): `http://192.168.219.50:8088`
- 외부 임시 주소: `[채워 주세요: 발표 때 확인한 주소]`

## 앞으로 할 일

- [ ] 클라우드 배포 (2026-12 ~ 2027-01 고도화 단계)
- [ ] Dockerfile 정리, GitHub Actions 로 빌드·배포 자동화 (CI/CD)
- [ ] 고정 도메인 연결

## 팀

`[채워 주세요: 팀원과 역할]`

## 문서

- [API 명세](docs/API.md) · [Postman](docs/postman)
- [성능 테스트 기록](docs/테스트-성능-확인.md)
