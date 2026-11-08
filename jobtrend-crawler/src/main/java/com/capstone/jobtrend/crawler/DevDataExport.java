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

/**
 * 팀원 개발용 데이터 꺼내기(읽기만). 기준 데이터와 집계 통계만 꺼낸다.
 * 공고·회사(posting, company, posting_skill, posting_job)는 사람인 데이터라 꺼내지 않는다.
 */
@Service
public class DevDataExport {

    private static final Logger log = LoggerFactory.getLogger(DevDataExport.class);

    private final NamedParameterJdbcTemplate db;

    public DevDataExport(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public void export(Path file) throws IOException {
        MapSqlParameterSource none = new MapSqlParameterSource();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", rows("SELECT job_id, saramin_code, name, sort_order FROM job ORDER BY job_id", none));
        out.put("skill", rows("SELECT skill_id, saramin_code, name, category, is_active FROM skill ORDER BY skill_id", none));
        out.put("skill_alias", rows("SELECT alias, skill_id, text_match FROM skill_alias ORDER BY alias", none));
        out.put("job_skill_stat", rows("""
                SELECT TO_CHAR(calc_date, 'YYYY-MM-DD') AS calc_date, job_id, skill_id, posting_cnt, job_total,
                       TO_CHAR(ratio, 'FM0.0000') AS ratio, rank_no
                  FROM job_skill_stat ORDER BY calc_date, job_id, rank_no
                """, none));
        if (file.toAbsolutePath().getParent() != null) Files.createDirectories(file.toAbsolutePath().getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), out);
        log.info("개발용 데이터를 {} 에 씀", file.toAbsolutePath());
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource p) {
        return db.queryForList(sql, p).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            r.forEach((k, v) -> m.put(k.toLowerCase(), v));
            return m;
        }).toList();
    }
}
