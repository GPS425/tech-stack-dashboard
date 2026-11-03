package com.example.test.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JobRepository {

    private final JdbcTemplate jdbcTemplate;

    public JobRepository(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<JobSummary> findAll(){
        String sql = "SELECT j.job_id, j.name, "
                + "(SELECT COUNT(*) FROM posting_job pj "
                + "JOIN posting p ON p.posting_id = pj.posting_id "
                + "WHERE pj.job_id = j.job_id AND p.is_closed = 0) AS posting_count, "
                + "NVL((SELECT MAX(s.job_total) FROM job_skill_stat s "
                + "WHERE s.job_id = j.job_id "
                + "AND calc_date = (SELECT MAX(calc_date) FROM job_skill_stat)), 0) AS job_total, "
                + "a.avg_total "
                + "FROM job j "
                + "LEFT JOIN (SELECT d.job_id, AVG(d.job_total) AS avg_total "
                + "FROM (SELECT job_id, calc_date, MAX(job_total) AS job_total "
                + "FROM job_skill_stat "
                + "WHERE calc_date > (SELECT MAX(calc_date) FROM job_skill_stat) - 7 "
                + "GROUP BY job_id, calc_date) d "
                + "GROUP BY d.job_id) a ON a.job_id = j.job_id "
                + "ORDER BY j.sort_order";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int jobTotal = rs.getInt("job_total");
            Double avgTotal = rs.getObject("avg_total", Double.class);
            boolean smallSample = avgTotal == null ? jobTotal < 100 : avgTotal < 100;
            return new JobSummary(
                    rs.getInt("job_id"),
                    rs.getString("name"),
                    rs.getInt("posting_count"),
                    jobTotal,
                    smallSample);
        });
    }

}
