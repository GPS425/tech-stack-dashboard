package com.example.test.skill;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SkillRepository {

    private final JdbcTemplate jdbcTemplate;

    public SkillRepository(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SkillItem> search(String q, int limit){
        String keyword = q == null ? "" : q.trim().toLowerCase();
        String pattern = "%" + keyword + "%";

        String sql = "SELECT k.skill_id, k.name, k.category FROM skill k "
                + "WHERE k.is_active = 1 "
                + "AND (LOWER(k.name) LIKE ? "
                + "OR EXISTS (SELECT 1 FROM skill_alias a "
                + "WHERE a.skill_id = k.skill_id AND a.alias LIKE ? )) "
                + "ORDER BY k.name "
                + "FETCH FIRST ? ROWS ONLY";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SkillItem(
                        rs.getInt("skill_id"),
                        rs.getString("name"),
                        rs.getString("category")),
                pattern, pattern, limit);
    }
}
