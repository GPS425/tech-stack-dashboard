package com.example.test.skill;

import com.example.test.common.ApiException;
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
            throw new ApiException(400, "INVALID_LIMIT",
                    "limit은 1~50 사이여야 합니다: " + limit);
        }
        return ResponseEntity.ok(repository.search(q, limit));
    }
}
