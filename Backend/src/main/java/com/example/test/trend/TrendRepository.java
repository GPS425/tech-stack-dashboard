package com.example.test.trend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TrendRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrendRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String findCalcDate() {
        java.sql.Date date = jdbcTemplate.queryForObject(
                "SELECT MAX(calc_date) FROM job_skill_stat", java.sql.Date.class);
        return date == null ? null : date.toString();
    }

    public List<JobDemandResponse.Item> findJobDemand() {
        String sql = "SELECT j.job_id, j.name, d.posting_count, "
                + "ROUND(d.weight * 100 / (SELECT COUNT(DISTINCT x.posting_id) "
                + "FROM posting_job x JOIN posting y ON y.posting_id = x.posting_id "
                + "WHERE y.is_closed = 0), 1) AS percentage "
                + "FROM job j "
                + "JOIN(SELECT pj.job_id, COUNT(*) AS posting_count, SUM(1 / c.job_cnt) AS weight "
                + "FROM posting_job pj "
                + "JOIN posting p ON p.posting_id = pj.posting_id AND p.is_closed = 0 "
                + "JOIN (SELECT posting_id, COUNT(*) AS job_cnt FROM posting_job GROUP BY posting_id ) c "
                + "ON c.posting_id = pj.posting_id "
                + "GROUP BY pj.job_id) d ON d.job_id = j.job_id "
                + "ORDER BY j.sort_order";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new JobDemandResponse.Item(
                rs.getInt("job_id"),
                rs.getString("name"),
                rs.getInt("posting_count"),
                rs.getDouble("percentage")
        ));
    }

}
