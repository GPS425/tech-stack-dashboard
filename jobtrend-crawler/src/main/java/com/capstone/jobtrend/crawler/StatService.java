package com.capstone.jobtrend.crawler;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ⑤ 직무별 기술 비율·순위. 그날 행을 지우고 다시 넣는다(한 트랜잭션).
 *
 * 계산 규칙 (9/29 동희님 결정, 설계 문서의 계산 규칙을 바꾼 것):
 * - 대상 공고: 진행 중 + 상세 받음 + 통계 제외(is_excluded) 아님 + 기술이 1개 이상 잡힘
 * - 여러 직무에 걸린 공고는 1/k 로 나눠 센다 (k = 그 공고가 걸린 직무 수)
 * - ratio     = 그 기술이 나온 공고의 가중치 합 / 그 직무 대상 공고의 가중치 합  (가중 비율)
 * - posting_cnt = 그 기술이 나온 실제 공고 수, job_total = 그 직무 대상 공고의 실제 수
 *   → ratio 는 posting_cnt / job_total 과 다르다. 화면은 "기술 정보가 있는 공고 job_total 건 중" 으로 쓴다
 * - 순위는 직무 안에서 가중 비율 순, 같으면 실제 공고 수, 그래도 같으면 먼저 등록된 기술. 동률도 번호가 겹치지 않는다
 */
@Service
public class StatService {

    private static final Logger log = LoggerFactory.getLogger(StatService.class);

    private final NamedParameterJdbcTemplate db;
    private final TransactionTemplate tx;

    public StatService(NamedParameterJdbcTemplate db, TransactionTemplate tx) {
        this.db = db;
        this.tx = tx;
    }

    /** calcDate 는 한국 날짜. 오라클의 SYSDATE 는 쓰지 않는다. */
    public int calculate(LocalDate calcDate) {
        MapSqlParameterSource p = new MapSqlParameterSource("calcDate", calcDate);
        Integer rows = tx.execute(s -> {
            db.update("DELETE FROM job_skill_stat WHERE calc_date = :calcDate", p);
            return db.update("""
                    INSERT INTO job_skill_stat
                           (calc_date, job_id, skill_id, posting_cnt, job_total, ratio, rank_no)
                    WITH base AS (
                        SELECT p.posting_id FROM posting p
                         WHERE p.is_closed = 0 AND p.detail_at IS NOT NULL AND p.is_excluded = 0
                           AND EXISTS (SELECT 1 FROM posting_skill ps WHERE ps.posting_id = p.posting_id)
                    ), pw AS (
                        SELECT pj.posting_id, pj.job_id, 1 / COUNT(*) OVER (PARTITION BY pj.posting_id) AS w
                          FROM posting_job pj JOIN base b ON b.posting_id = pj.posting_id
                    ), jt AS (
                        SELECT job_id, COUNT(*) AS total, SUM(w) AS wtotal FROM pw GROUP BY job_id
                    ), js AS (
                        SELECT pw.job_id, ps.skill_id, COUNT(*) AS cnt, SUM(pw.w) AS wcnt
                          FROM pw
                          JOIN (SELECT DISTINCT posting_id, skill_id FROM posting_skill) ps ON ps.posting_id = pw.posting_id
                         GROUP BY pw.job_id, ps.skill_id
                    )
                    SELECT :calcDate, js.job_id, js.skill_id, js.cnt, jt.total,
                           ROUND(js.wcnt / jt.wtotal, 4),
                           ROW_NUMBER() OVER (PARTITION BY js.job_id ORDER BY js.wcnt DESC, js.cnt DESC, js.skill_id)
                      FROM js JOIN jt ON jt.job_id = js.job_id
                    """, p);
        });
        log.info("⑤ 집계 {}: {}행", calcDate, rows);
        return rows == null ? 0 : rows;
    }
}
