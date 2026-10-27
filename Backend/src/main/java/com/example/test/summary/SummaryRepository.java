package com.example.test.summary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public SummaryRepository(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }

    public SummaryResponse find(){
        String sql = "SELECT (SELECT MAX(calc_date) FROM job_skill_stat) AS calc_date, "
                + "(SELECT COUNT(*) FROM posting WHERE is_closed = 0) AS posting_count, "
                + "(SELECT COUNT(*) FROM posting p WHERE p.is_closed = 0 AND p.is_excluded = 0 "
                + "AND p.detail_at IS NOT NULL AND EXISTS (SELECT 1 FROM posting_skill ps "
                + "JOIN skill k ON k.skill_id = ps.skill_id AND k.is_active = 1 "
                + "WHERE ps.posting_id = p.posting_id)) AS stat_posting_count, "
                + "(SELECT COUNT(*) FROM skill WHERE is_active = 1) AS skill_count, "
                + "(SELECT COUNT(*) FROM job) AS job_count "
                + "FROM DUAL";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            java.sql.Date date = rs.getDate("calc_date");
            return new SummaryResponse(
                    date == null ? null : date.toString(),
                    rs.getInt("posting_count"),
                    rs.getInt("stat_posting_count"),
                    rs.getInt("skill_count"),
                    rs.getInt("job_count")
            );
        });
    }
}
