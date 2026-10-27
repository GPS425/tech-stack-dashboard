package com.example.test.skill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SkillController {

    private final SkillRepository repository;

    public SkillController(SkillRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/skills")
    public ResponseEntity<?> getSkills(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "200") int limit) {
        if (limit < 1 || limit > 500) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "code", "INVALID_LIMIT",
                    "message", "limit은 1 ~ 500 사이여야 합니다 : " + limit
            ));
        }
        return ResponseEntity.ok(repository.search(q, limit));
    }
}
