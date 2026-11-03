package com.example.test.trend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class TrendRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Cell(String skillName, int jobId, double percentage) {
    }

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

    public List<MatrixResponse.Job> findJobs(List<Integer> jobIds) {
        String placeholders = String.join(",", Collections.nCopies(jobIds.size(), "?"));
        String sql = "SELECT job_id, name FROM job WHERE job_id IN (" + placeholders + ") ORDER BY sort_order";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new MatrixResponse.Job(rs.getInt("job_id"),
                        rs.getString("name")),
                jobIds.toArray());
    }

    public List<Cell> findMatrixCells(List<Integer> jobIds, int limit) {
        String placeholders = String.join(",", Collections.nCopies(jobIds.size(), "?"));

        String sql = "SELECT k.name AS skill_name, s.job_id, s.ratio "
                + "FROM job_skill_stat s "
                + "JOIN skill k ON k.skill_id = s.skill_id AND k.is_active = 1 "
                + "JOIN (SELECT s2.skill_id, SUM(s2.ratio) AS total_cnt "
                + "FROM job_skill_stat s2 "
                + "JOIN skill k2 ON k2.skill_id = s2.skill_id AND k2.is_active = 1 "
                + "WHERE s2.calc_date = (SELECT MAX(calc_date) FROM job_skill_stat) "
                + "AND s2.job_id IN (" + placeholders + ") "
                + "GROUP BY s2.skill_id "
                + "ORDER BY SUM(s2.ratio) DESC, s2.skill_id "
                + "FETCH FIRST ? ROWS ONLY) t ON t.skill_id = s.skill_id "
                + "WHERE s.calc_date = (SELECT MAX(calc_date) FROM job_skill_stat) "
                + "AND s.job_id IN (" + placeholders + ") "
                + "ORDER BY t.total_cnt DESC, k.skill_id, s.job_id";

        List<Object> params = new ArrayList<>(jobIds);
        params.add(limit);
        params.addAll(jobIds);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new Cell(rs.getString("skill_name"),
                        rs.getInt("job_id"),
                        Math.round(rs.getDouble("ratio") * 1000) / 10.0),
                params.toArray());
    }

    public List<CombinationsResponse.Item> findCombinations(Integer jobId, int limit){
        String jobFilter = jobId == null ? ""
                : "AND EXISTS (SELECT 1 FROM posting_job pj "
                  + "WHERE pj.posting_id = p.posting_id AND pj.job_id = ?) ";

        String sql = "WITH ps AS (SELECT DISTINCT p.posting_id, x.skill_id "
                + "FROM posting p "
                + "JOIN posting_skill x ON x.posting_id = p.posting_id "
                + "JOIN skill k ON k.skill_id = x.skill_id AND k.is_active = 1 "
                + "WHERE p.is_closed = 0 AND p.is_excluded = 0 AND p.detail_at IS NOT NULL "
                + jobFilter
                + ") "
                + "SELECT k1.name AS skill1, k2.name AS skill2, COUNT(*) AS posting_count "
                + "FROM ps a "
                + "JOIN ps b ON b.posting_id = a.posting_id AND a.skill_id < b.skill_id "
                + "JOIN skill k1 ON k1.skill_id = a.skill_id "
                + "JOIN skill k2 ON k2.skill_id = b.skill_id "
                + "GROUP BY a.skill_id, b.skill_id, k1.name, k2.name "
                + "ORDER BY COUNT(*) DESC, a.skill_id, b.skill_id "
                + "FETCH FIRST ? ROWS ONLY";

        List<Object> params = new ArrayList<>();
        if (jobId != null){
            params.add(jobId);
        }
        params.add(limit);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new CombinationsResponse.Item(
                        List.of(rs.getString("skill1"), rs.getString("skill2")),
                        rs.getInt("posting_count")),
                params.toArray()
        );
    }

    public record MarketSkills(String skillName,
                               double ratio){
    }

    public List<MarketSkills> marketSkills(int jobId){
        String sql = "SELECT k.name AS skill_name, s.ratio "
                + "FROM job_skill_stat s "
                + "JOIN skill k ON k.skill_id = s.skill_id AND k.is_active = 1 "
                + "WHERE s.job_id = ? "
                + "AND s.calc_date = (SELECT MAX(calc_date) FROM job_skill_stat) "
                + "ORDER BY s.ratio DESC, s.skill_id "
                + "FETCH FIRST 10 ROWS ONLY";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new MarketSkills(
                        rs.getString("skill_name"),
                        rs.getDouble("ratio")),
                jobId);
    }

    public record SkillMatch(String alias,
                             int skillId,
                             String name){
    }

    public List<SkillMatch> findSkillsByAlias(List<String> aliases){
        String placeholders = String.join(",", Collections.nCopies(aliases.size(), "?"));
        String sql = "SELECT a.alias, k.skill_id, k.name "
                + "FROM skill_alias a "
                + "JOIN skill k ON k.skill_id = a.skill_id AND k.is_active = 1 "
                + "WHERE a.alias IN (" + placeholders + ")";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SkillMatch(
                        rs.getString("alias"),
                        rs.getInt("skill_id"),
                        rs.getString("name")),
                aliases.toArray());
    }


}
