-- 10/1 크롤링 검수 수정. 01-schema.sql 을 적용한 DB 에 한 번 돌린다(Flyway 를 쓰면 V2).
-- 새 크롤러는 이 칸들을 쓰므로, 크롤러를 바꾸기 전에 먼저 적용한다. 크롤러는 멈춘 상태에서 돌린다.
-- 이미 있는 DB: docker exec -i <오라클 컨테이너> sqlplus -s / as sysdba < db/02-review-fix.sql
-- 새 컨테이너: /container-entrypoint-initdb.d 에 01 다음으로 두면 이름 순으로 돈다.
-- 다시 돌려도 된다: 이미 있는 칸·제약·인덱스는 건너뛴다.
SET SQLBLANKLINES ON
SET DEFINE OFF
WHENEVER SQLERROR EXIT FAILURE
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = CAPSTONE;
-- 컨테이너를 옮긴 뒤에 켠다(앞에서 켜면 XEPDB1 에서는 출력이 안 보임)
SET SERVEROUTPUT ON

DECLARE
  PROCEDURE run(p_sql VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
    DBMS_OUTPUT.PUT_LINE('applied: ' || p_sql);
  EXCEPTION
    -- 이미 있는 칸(01430)·이름(00955)·제약 이름(02264)·같은 칸의 인덱스(01408)는 앞에서 적용된 것
    WHEN OTHERS THEN
      IF SQLCODE IN (-1430, -955, -2264, -1408) THEN
        DBMS_OUTPUT.PUT_LINE('already there: ' || p_sql);
      ELSE
        RAISE;
      END IF;
  END;
BEGIN
  -- skill.csv 에서 뺀 기술은 지우지 않고 끈다(posting_skill 이 가리킴). ④ 태그·⑤ 집계는 is_active = 1 만 쓴다
  run('ALTER TABLE skill ADD (is_active NUMBER(1) DEFAULT 1 NOT NULL)');
  run('ALTER TABLE skill ADD CONSTRAINT ck_skill_active CHECK (is_active IN (0, 1))');

  -- 이 직무 목록에서 마지막으로 본 시각. 끝까지 받은 직무의 목록에서 빠진 진행 중 공고는 ②가 그 직무와의 연결을 끊는다
  -- (안 끊으면 회사가 직무를 고친 공고가 마감될 때까지 옛 직무의 job_total·k 에 남는다). NULL = 아주 오래전
  run('ALTER TABLE posting_job ADD (last_seen_at TIMESTAMP)');

  -- 원문(raw) 파일을 지운 시각. 지운 공고를 매 회차 다시 찾지 않고, 되살아나면 상세를 다시 받는 표시로 쓴다
  run('ALTER TABLE posting ADD (raw_deleted_at TIMESTAMP)');

  -- 도는 회차가 5분마다 살아 있다고 적는 시각. 60분 넘게 끊긴 RUNNING 만 죽은 앱이 남긴 것으로 본다
  run('ALTER TABLE crawl_run ADD (heartbeat_at TIMESTAMP)');

  -- 유니크 인덱스를 만들기 전에 남은 RUNNING 을 정리한다(옛 크롤러는 동시에 둘이 생길 수 있었다).
  -- 메모는 ASCII 로(NLS_LANG 없는 sqlplus 에서 한글이 깨짐), 시각은 크롤러처럼 한국 시간으로
  UPDATE crawl_run SET status = 'FAILED',
         finished_at = CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP),
         note = SUBSTR('closed by 02-review-fix: ' || note, 1, 500)
   WHERE status = 'RUNNING';
  DBMS_OUTPUT.PUT_LINE('closed RUNNING rows: ' || SQL%ROWCOUNT);
  COMMIT;

  -- RUNNING 은 한 건만. 테스트 서버와 PC 가 같은 순간에 시작해도 둘째 INSERT 가 막힌다(NULL 은 인덱스에 안 들어감)
  run('CREATE UNIQUE INDEX uk_run_running ON crawl_run (CASE WHEN status = ''RUNNING'' THEN 0 END)');
END;
/

EXIT
