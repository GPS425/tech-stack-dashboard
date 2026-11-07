package com.example.test.posting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class PostingTagRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Tag(long postingId, String name) {
    }

    public record SkillIdTag(long postingId, int skillId) {
    }

    public PostingTagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Tag> findJobNames(List<Long> postingIds) {
        if (postingIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(postingIds.size(), "?"));
        String sql = "SELECT pj.posting_id, j.name "
                + "FROM posting_job pj "
                + "JOIN job j ON j.job_id = pj.job_id "
                + "WHERE pj.posting_id IN (" + placeholders + ") "
                + "ORDER BY pj.posting_id, j.sort_order";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new Tag(rs.getLong("posting_id"), rs.getString("name")),
                postingIds.toArray());
    }

    public List<Tag> findSkillNames(List<Long> postingIds) {
        if (postingIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(postingIds.size(), "?"));
        String sql = "SELECT DISTINCT ps.posting_id, k.skill_id, k.name "
                + "FROM posting_skill ps "
                + "JOIN skill k ON k.skill_id = ps.skill_id AND k.is_active = 1 "
                + "WHERE ps.posting_id IN (" + placeholders + ") "
                + "ORDER BY ps.posting_id, k.skill_id";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new Tag(rs.getLong("posting_id"), rs.getString("name")),
                postingIds.toArray());
    }

    public List<String> findSkillNamesBySource(long postingId, String source) {
        String sql = "SELECT DISTINCT k.skill_id, k.name "
                + "FROM posting_skill ps "
                + "JOIN skill k ON k.skill_id = ps.skill_id AND k.is_active = 1 "
                + "WHERE ps.posting_id = ? AND ps.source = ? "
                + "ORDER BY k.skill_id";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getString("name"),
                postingId, source);
    }

    public List<SkillIdTag> findSkillIds(List<Long> postingIds) {
        if (postingIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(postingIds.size(), "?"));
        String sql = "SELECT DISTINCT ps.posting_id, ps.skill_id "
                + "FROM posting_skill ps "
                + "JOIN skill k ON k.skill_id = ps.skill_id AND k.is_active = 1 "
                + "WHERE ps.posting_id IN (" + placeholders + ")";

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SkillIdTag(rs.getLong("posting_id"), rs.getInt("skill_id")),
                postingIds.toArray());
    }

}
