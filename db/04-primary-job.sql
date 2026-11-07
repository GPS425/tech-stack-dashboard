-- 11/7 대표 직무. 01·02 를 적용한 DB 에 한 번 돌린다(Flyway 를 쓰면 V4).
-- 칸 추가만 한다(지우거나 바꾸는 행 없음). 새 칸은 모두 0 으로 시작하고, 크롤러 ④ 다음 단계가 공고마다 하나를 1 로 바꾼다.
-- 새 크롤러보다 먼저 적용한다(크롤러는 칸이 없으면 대표 직무 단계를 건너뛴다). 크롤러는 멈춘 상태에서 돌린다.
-- 이미 있는 DB: docker exec -i <오라클 컨테이너> sqlplus -s / as sysdba < db/04-primary-job.sql
-- 다시 돌려도 된다: 이미 있는 칸·제약은 건너뛴다.
SET SQLBLANKLINES ON
SET DEFINE OFF
WHENEVER SQLERROR EXIT FAILURE
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = CAPSTONE;
SET SERVEROUTPUT ON

DECLARE
  PROCEDURE run(p_sql VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
    DBMS_OUTPUT.PUT_LINE('applied: ' || p_sql);
  EXCEPTION
    -- 이미 있는 칸(01430)·제약 이름(02264)은 앞에서 적용된 것
    WHEN OTHERS THEN
      IF SQLCODE IN (-1430, -2264) THEN
        DBMS_OUTPUT.PUT_LINE('already there: ' || p_sql);
      ELSE
        RAISE;
      END IF;
  END;
BEGIN
  -- 공고의 대표 직무(공고마다 하나). 화면은 직무 이름을 is_primary DESC, sort_order 순으로 보여 준다.
  -- 1 이 없는 공고(새로 들어와 아직 ④를 안 거친 공고)는 지금처럼 sort_order 순
  run('ALTER TABLE posting_job ADD (is_primary NUMBER(1) DEFAULT 0 NOT NULL)');
  run('ALTER TABLE posting_job ADD CONSTRAINT ck_pj_primary CHECK (is_primary IN (0, 1))');
END;
/

EXIT
