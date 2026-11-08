package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 대시보드 상태 파일의 DB 없이 볼 수 있는 규칙. */
class StatusFileTest {

    private static Map<String, Object> row(long id, String start, String status, int list, int fail, int complete, String note) {
        Map<String, Object> r = new HashMap<>();
        r.put("RUN_ID", id);
        r.put("STARTED_AT", Timestamp.valueOf(LocalDateTime.parse(start)));
        r.put("FINISHED_AT", "RUNNING".equals(status) ? null : Timestamp.valueOf(LocalDateTime.parse(start).plusMinutes(18)));
        r.put("STATUS", status);
        r.put("LIST_CNT", list);
        r.put("NEW_CNT", 154);
        r.put("CLOSED_CNT", 154);
        r.put("FAIL_CNT", fail);
        r.put("LIST_COMPLETE", complete);
        r.put("NOTE", note);
        return r;
    }

    @Test
    void 정상_회차는_숫자만_뽑고_경고가_없다() {
        Map<String, Object> r = StatusFile.run(row(384, "2026-10-20T04:00:00", "DONE", 4133, 0, 1,
                "새 공고 264 · 마감 154 (마감일 지남 62 · 목록에서 사라짐 92)"));
        assertEquals("OK", r.get("result"));
        assertEquals(264, r.get("new_postings"));
        assertEquals(62, r.get("closed_deadline"));
        assertEquals(92, r.get("closed_unseen"));
        assertEquals(18L, r.get("minutes"));
        assertEquals("2026-10-20", r.get("date"));
        assertNull(r.get("stage"));
        assertEquals(List.of(), r.get("warnings"));
    }

    @Test
    void 안전장치_문구는_경고로_남는다() {
        Map<String, Object> r = StatusFile.run(row(263, "2026-10-12T04:00:00", "DONE", 3926, 0, 1,
                "새 공고 28 · 마감 178 (마감일 지남 178 · 목록에서 사라짐 0) · 직무 게임개발 20/135건이 10%를 넘어 마감 처리를 건너뜀"));
        assertEquals("WARN", r.get("result"));
        assertEquals(List.of("직무 게임개발 20/135건이 10%를 넘어 마감 처리를 건너뜀"), r.get("warnings"));
    }

    @Test
    void 늘_붙는_건너뜀_표시는_경고가_아니다() {
        assertEquals(List.of(), StatusFile.warnings("DONE", "새 공고 3 · 제목·회사명 없어 건너뜀 2 · 처음 보는 마감 표기 5"));
    }

    @Test
    void 상세_실패와_덜_받은_목록도_경고다() {
        Map<String, Object> r = StatusFile.run(row(64, "2026-10-02T04:00:00", "DONE", 4248, 1, 0, "새 공고 243 · 마감 198 (마감일 지남 102 · 목록에서 사라짐 96)"));
        assertEquals("WARN", r.get("result"));
        assertEquals(List.of("상세 실패 1건", "목록을 끝까지 받지 못함"), r.get("warnings"));
    }

    @Test
    void 중단된_회차는_원인_첫_줄만() {
        Map<String, Object> r = StatusFile.run(row(26, "2026-09-29T21:00:00", "FAILED", 4449, 0, 1,
                "새 공고 0 · org.springframework.jdbc.BadSqlGrammarException: StatementCallback; bad SQL grammar [UPDATE ...]"));
        assertEquals("FAIL", r.get("result"));
        assertEquals(List.of("org.springframework.jdbc.BadSqlGrammarException: StatementCallback (SQL 문법 오류)"), r.get("warnings"));
    }

    @Test
    void 중단되면_멈춘_단계와_진행률이_남는다() {
        CrawlService.Counts c = new CrawlService.Counts();
        c.newPostings = 225;
        c.step = 3;
        c.detailPlanned = 446;
        c.detailOk = 202;
        c.fail = 10;
        String note = CrawlService.runNote(c, CrawlService.stageText(c) + " · 상세가 10번 연달아 실패");
        Map<String, Object> r = StatusFile.run(row(500, "2026-10-22T04:00:00", "FAILED", 4190, 10, 1, note));
        assertEquals("FAIL", r.get("result"));
        assertEquals("③ 상세 받는 중 212/446", r.get("stage"));
        assertEquals(3, r.get("stage_step"));
        assertEquals(212, r.get("detail_done"));
        assertEquals(446, r.get("detail_target"));
        assertEquals(225, r.get("new_postings"));
        assertEquals(List.of("상세가 10번 연달아 실패", "상세 실패 10건"), r.get("warnings"));
        assertEquals("② 마감 처리 중", CrawlService.stageText(new CrawlService.Counts() {{ step = 2; }}));
    }

    @Test
    void 도는_중이면_마지막_단계를_보여_준다() {
        Map<String, Object> r = StatusFile.run(row(400, "2026-10-21T04:00:00", "RUNNING", 4100, 0, 1,
                "새 공고 120 · 마감 80 (마감일 지남 50 · 목록에서 사라짐 30) · ③ 상세 받는 중"));
        assertEquals("RUNNING", r.get("result"));
        assertEquals("③ 상세 받는 중", r.get("stage"));
        assertEquals(List.of(), r.get("warnings"));
        assertEquals("① 목록 받는 중", StatusFile.stage("RUNNING", null));
    }

    @Test
    void 상세_진행률과_지금_단계_번호() {
        Map<String, Object> r = StatusFile.run(row(400, "2026-10-21T04:00:00", "RUNNING", 4100, 0, 1,
                "새 공고 120 · 마감 80 (마감일 지남 50 · 목록에서 사라짐 30) · ③ 상세 받는 중 300/446 · 덜 받은 직무 1개는 마감을 미룸"));
        assertEquals("③ 상세 받는 중 300/446", r.get("stage"));
        assertEquals(3, r.get("stage_step"));
        assertEquals(300, r.get("detail_done"));
        assertEquals(446, r.get("detail_target"));
        assertEquals(List.of("덜 받은 직무 1개는 마감을 미룸"), r.get("warnings"));
        assertEquals(1, StatusFile.stageStep("① 목록 받는 중"));
        assertEquals(2, StatusFile.stageStep("① 목록 끝"));
        assertEquals(3, StatusFile.stageStep("② 마감 끝"));
        assertEquals(4, StatusFile.stageStep("③ 상세 끝"));
        assertEquals(4, StatusFile.stageStep("④ 기술 추출 중"));
        assertEquals(5, StatusFile.stageStep("⑤ 집계 중"));
        assertNull(StatusFile.stageStep(null));
    }

    @Test
    void 연속_정상_일수는_도는_중인_회차를_건너뛰고_하루에_하나씩() {
        List<Map<String, Object>> runs = new ArrayList<>();
        runs.add(StatusFile.run(row(5, "2026-10-21T04:00:00", "RUNNING", 0, 0, 0, null)));
        runs.add(StatusFile.run(row(4, "2026-10-20T04:00:00", "DONE", 4133, 0, 1, "새 공고 1")));
        runs.add(StatusFile.run(row(3, "2026-10-19T04:00:00", "DONE", 4013, 0, 1, "새 공고 1")));
        runs.add(StatusFile.run(row(2, "2026-10-18T04:00:00", "DONE", 4013, 0, 1, "새 공고 1 · 직무 BI 10%를 넘어 마감 처리를 건너뜀")));
        runs.add(StatusFile.run(row(1, "2026-10-17T04:00:00", "DONE", 4013, 0, 1, "새 공고 1")));
        assertEquals(2, StatusFile.streak(runs));
        List<Map<String, Object>> h = StatusFile.history(runs);
        assertEquals(4, h.size());
        assertEquals("2026-10-17", h.get(0).get("date"));          // 오래된 날부터
        assertEquals(1, h.get(1).get("warning_count"));
    }

    @Test
    void 다음_수집은_새벽_4시() {
        assertEquals(LocalDateTime.parse("2026-10-21T04:00"), StatusFile.nextRun(LocalDateTime.parse("2026-10-21T03:10")));
        assertEquals(LocalDateTime.parse("2026-10-22T04:00"), StatusFile.nextRun(LocalDateTime.parse("2026-10-21T04:00")));
        assertTrue(StatusFile.nextRun(LocalDateTime.parse("2026-10-21T23:59")).getDayOfMonth() == 22);
    }
}
