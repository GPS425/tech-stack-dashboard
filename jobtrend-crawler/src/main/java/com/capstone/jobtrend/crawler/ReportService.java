package com.capstone.jobtrend.crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 단계 보고서용 숫자를 DB 에서 뽑아 JSON 한 파일로 쓴다(crawler.report-file). 사람인에 요청하지 않는다.
 * 보고서 HTML 은 이 파일로 만든다.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final NamedParameterJdbcTemplate db;

    public ReportService(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public void export(Path file) throws IOException {
        MapSqlParameterSource none = new MapSqlParameterSource();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", db.queryForList("""
                SELECT run_id, started_at, finished_at, status, list_complete, list_cnt, new_cnt, closed_cnt, fail_cnt, note
                  FROM crawl_run ORDER BY run_id DESC FETCH FIRST 10 ROWS ONLY
                """, none));
        out.put("totals", db.queryForMap("""
                SELECT (SELECT COUNT(*) FROM posting) AS postings,
                       (SELECT COUNT(*) FROM posting WHERE is_closed = 0) AS open_postings,
                       (SELECT COUNT(*) FROM posting WHERE is_closed = 0 AND detail_at IS NOT NULL) AS with_detail,
                       (SELECT COUNT(*) FROM posting WHERE is_closed = 0 AND detail_at IS NULL AND detail_fail_cnt > 0) AS fail_now,
                       (SELECT COUNT(*) FROM posting WHERE is_closed = 0 AND detail_fail_cnt >= 3) AS fail_resting,
                       (SELECT COUNT(*) FROM company) AS companies,
                       (SELECT COUNT(*) FROM posting_job pj JOIN posting p ON p.posting_id = pj.posting_id
                         WHERE p.is_closed = 0) AS posting_jobs,
                       (SELECT COUNT(*) FROM posting p WHERE p.is_closed = 0 AND p.detail_at IS NOT NULL
                           AND NOT EXISTS (SELECT 1 FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1 WHERE ps.posting_id = p.posting_id)) AS no_skill,
                       (SELECT COUNT(*) FROM posting WHERE is_closed = 0 AND is_excluded = 1) AS excluded,
                       (SELECT COUNT(*) FROM posting p WHERE p.is_closed = 0 AND p.detail_at IS NOT NULL AND p.is_excluded = 0
                           AND EXISTS (SELECT 1 FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1 WHERE ps.posting_id = p.posting_id)) AS stat_base,
                       (SELECT COUNT(*) FROM skill WHERE is_active = 1) AS skills,
                       (SELECT COUNT(DISTINCT ps.skill_id) FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1) AS skills_seen,
                       (SELECT COUNT(*) FROM posting_skill) AS posting_skills
                  FROM dual
                """, none));
        out.put("jobs", db.queryForList("""
                SELECT j.job_id, j.saramin_code, j.name, j.sort_order,
                       COUNT(p.posting_id) AS open_postings,
                       SUM(CASE WHEN p.detail_at IS NOT NULL THEN 1 ELSE 0 END) AS with_detail,
                       SUM(CASE WHEN p.is_headhunting = 1 THEN 1 ELSE 0 END) AS headhunting,
                       SUM(CASE WHEN p.career_type IN ('NEW', 'NEW_OR_EXP', 'ANY') THEN 1 ELSE 0 END) AS newcomer_ok,
                       SUM(CASE WHEN p.detail_at IS NOT NULL AND NOT EXISTS
                                     (SELECT 1 FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1 WHERE ps.posting_id = p.posting_id) THEN 1 ELSE 0 END) AS no_skill,
                       SUM(CASE WHEN p.posting_id IS NOT NULL AND (SELECT COUNT(*) FROM posting_job x
                                     WHERE x.posting_id = p.posting_id) = 1 THEN 1 ELSE 0 END) AS only_this_job,
                       SUM(CASE WHEN p.is_excluded = 1 THEN 1 ELSE 0 END) AS excluded,
                       SUM(CASE WHEN p.detail_at IS NOT NULL AND p.is_excluded = 0 AND EXISTS
                                     (SELECT 1 FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1 WHERE ps.posting_id = p.posting_id) THEN 1 ELSE 0 END) AS stat_base,
                       SUM(CASE WHEN p.detail_at IS NOT NULL AND p.is_excluded = 0
                                 AND EXISTS (SELECT 1 FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1 WHERE ps.posting_id = p.posting_id)
                                 AND (SELECT COUNT(*) FROM posting_job x WHERE x.posting_id = p.posting_id) = 1
                                THEN 1 ELSE 0 END) AS only_this_job_base
                  FROM job j
                  LEFT JOIN posting_job pj ON pj.job_id = j.job_id
                  LEFT JOIN posting p ON p.posting_id = pj.posting_id AND p.is_closed = 0
                 GROUP BY j.job_id, j.saramin_code, j.name, j.sort_order
                 ORDER BY j.sort_order
                """, none));
        out.put("top", db.queryForList("""
                SELECT st.calc_date, st.job_id, st.rank_no, s.name AS skill, s.category, st.posting_cnt, st.job_total, st.ratio
                  FROM job_skill_stat st JOIN skill s ON s.skill_id = st.skill_id
                 WHERE st.calc_date = (SELECT MAX(calc_date) FROM job_skill_stat) AND st.rank_no <= 20
                 ORDER BY st.job_id, st.rank_no
                """, none));
        out.put("sources", db.queryForList("""
                SELECT source, COUNT(*) AS cnt, COUNT(DISTINCT posting_id) AS postings
                  FROM posting_skill GROUP BY source ORDER BY source
                """, none));
        out.put("categories", db.queryForList("""
                SELECT s.category, COUNT(DISTINCT ps.posting_id) AS postings, COUNT(DISTINCT ps.skill_id) AS skills
                  FROM posting_skill ps JOIN skill s ON s.skill_id = ps.skill_id
                 GROUP BY s.category ORDER BY postings DESC
                """, none));
        out.put("careers", db.queryForList("""
                SELECT NVL(career_type, '(없음)') AS career_type, COUNT(*) AS cnt
                  FROM posting WHERE is_closed = 0 AND detail_at IS NOT NULL GROUP BY career_type ORDER BY cnt DESC
                """, none));
        out.put("deadlines", db.queryForList("""
                SELECT deadline_type, COUNT(*) AS cnt FROM posting WHERE is_closed = 0 GROUP BY deadline_type ORDER BY cnt DESC
                """, none));
        out.put("textOnly", db.queryForList("""
                SELECT s.name AS skill, COUNT(DISTINCT ps.posting_id) AS postings
                  FROM posting_skill ps JOIN skill s ON s.skill_id = ps.skill_id
                  JOIN posting p ON p.posting_id = ps.posting_id AND p.is_closed = 0
                 WHERE s.saramin_code IS NULL AND ps.source IN ('REQUIRED', 'PREFERRED')
                 GROUP BY s.name ORDER BY postings DESC FETCH FIRST 20 ROWS ONLY
                """, none));
        // 마지막으로 끝까지 받은 회차를 ②의 조건 그대로 다시 세어 본 것(읽기만).
        // long_gone 이 날마다 불어나면 '목록에서 사라진 공고 닫기'가 멈춘 것이다
        java.time.LocalDateTime last = CrawlService.previousCompleteRun(db, CrawlService.now());
        java.time.LocalDateTime prev = CrawlService.previousCompleteRun(db, last);
        List<Integer> jobIds = db.queryForList("SELECT job_id FROM job", none, Integer.class);
        MapSqlParameterSource cp = new MapSqlParameterSource("runStart", last).addValue("prevStart", prev)
                .addValue("incomplete", List.of(-1)).addValue("complete", jobIds.isEmpty() ? List.of(-1) : jobIds);
        Map<String, Object> preview = new LinkedHashMap<>(db.queryForMap("""
                SELECT (SELECT COUNT(*) FROM posting p WHERE %s) AS long_gone,
                       (SELECT COUNT(*) FROM posting p
                         WHERE p.is_closed = 0 AND p.last_seen_at >= :prevStart AND p.last_seen_at < :runStart) AS first_missing,
                       (SELECT COUNT(*) FROM posting_job pj WHERE %s) AS long_gone_links
                  FROM dual
                """.formatted(CrawlService.LONG_GONE, CrawlService.LONG_GONE_LINK), cp));
        preview.put("run_start", last.toString());
        preview.put("prev_start", prev.toString());
        out.put("close_preview", preview);
        out.put("close_preview_jobs", db.queryForList("""
                SELECT j.name, COUNT(*) AS open_postings,
                       SUM(CASE WHEN p.last_seen_at < :prevStart THEN 1 ELSE 0 END) AS long_gone,
                       SUM(CASE WHEN p.last_seen_at >= :prevStart AND p.last_seen_at < :runStart THEN 1 ELSE 0 END) AS first_missing
                  FROM posting p JOIN posting_job pj ON pj.posting_id = p.posting_id JOIN job j ON j.job_id = pj.job_id
                 WHERE p.is_closed = 0
                 GROUP BY j.name, j.sort_order ORDER BY j.sort_order
                """, cp));
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        Files.createDirectories(file.toAbsolutePath().getParent());
        json.writeValue(file.toFile(), lower(out));
        log.info("보고서 숫자를 {} 에 씀", file.toAbsolutePath());
    }

    /** 오라클은 칸 이름을 대문자로 돌려준다. JSON 은 소문자로. */
    @SuppressWarnings("unchecked")
    private static Object lower(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            m.forEach((k, v) -> r.put(String.valueOf(k).toLowerCase(), lower(v)));
            return r;
        }
        if (o instanceof List<?> l) return l.stream().map(ReportService::lower).toList();
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toString();
        if (o instanceof java.sql.Date d) return d.toLocalDate().toString();
        return o;
    }
}
