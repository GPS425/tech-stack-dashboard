package com.example.test;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DbHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DbHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health/db")
    public Map<String, Object> checkDatabase() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM DUAL",
                Integer.class
        );

        return Map.of("status", "ok", "result", result);
    }
}