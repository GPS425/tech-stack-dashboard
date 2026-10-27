package com.example.test.jobskill;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JobSkillRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Stat(String calcDate, int jobTotal){}

    public JobSkillRepository(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }

    // 직무 이름 조회, 없는 직무면 null
    public String findJobName(int jobId){
        List<String> names = jdbcTemplate.query(
                "SELECT name FROM job WHERE job_id = ?",
                (rs, rowNum) -> rs.getString("name"),
                jobId);
        return names.isEmpty() ? null : names.get(0);
    }

    // 직무의 최신 기술 순위를 순위순으로 limit 개수만큼 조회
    public List<JobSkillResponse.Item> findTopSkills(int jobId, int limit){
        String sql = "SELECT s.rank_no, k.skill_id, k.name, k.category, s.ratio, s.posting_cnt "
                + "FROM job_skill_stat s "
                + "JOIN skill k ON k.skill_id = s.skill_id AND k.is_active = 1 "
                + "WHERE s.job_id = ? "
                + "AND s.calc_date = (SELECT MAX(calc_date) FROM job_skill_stat) "
                + "ORDER BY s.rank_no "
                + "FETCH FIRST ? ROWS ONLY";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new JobSkillResponse.Item(
                        rs.getInt("rank_no"),
                        rs.getInt("skill_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        Math.round(rs.getDouble("ratio") * 1000) / 10.0,
                        rs.getInt("posting_cnt")),
                jobId, limit);
    }

    // 직무의 최신 계산일과 전체 공고 수 조회, 통계 없으면 null
    public Stat findLatestStat(int jobId){
        List<Stat> stats = jdbcTemplate.query(
                "SELECT calc_date, job_total FROM job_skill_stat "
                        + "WHERE job_id = ? "
                        + "AND calc_date = (SELECT MAX(calc_date) FROM job_skill_stat) "
                        + "FETCH FIRST 1 ROWS ONLY",
                (rs, rowNum) -> new Stat(
                        rs.getDate("calc_date").toString(),
                        rs.getInt("job_total")),
                jobId);
        return stats.isEmpty() ? null :stats.get(0);
    }
}
