package com.example.test.jobskill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class JobSkillController {

    private final JobSkillRepository repository;

    public JobSkillController(JobSkillRepository repository){
        this.repository = repository;
    }

    @GetMapping("/api/jobs/{jobId}/skills")
    public ResponseEntity<?> getTopSkills(@PathVariable int jobId,
                                          @RequestParam(defaultValue = "5") int limit){
        // 리미트 검사, 1 ~ 50 아니면 400 응답
        if (limit < 1 || limit > 50) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "code", "INVALID_LIMIT",
                    "message", "limit은 1~50 사이여야 합니다: " + limit
            ));
        }

        // 없는 직무일시 404 응답
        String name = repository.findJobName(jobId);
        if(name == null){
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404,
                    "code", "JOB_NOT_FOUND",
                    "message", "직무를 찾을 수 없습니다: " + jobId
            ));
        }

        // 최신 통계와 기술순위 조회
        JobSkillRepository.Stat stat = repository.findLatestStat(jobId);
        List<JobSkillResponse.Item> items = repository.findTopSkills(jobId, limit);

        // 통계가 없으면 날짜는 null, 전체 공고 수는 0
        String calcDate = stat == null ? null : stat.calcDate();
        int jobTotal = stat == null ? 0 : stat.jobTotal();

        // 응답 조립, 전체 공고가 100건보다 작으면 smallSample을 트루로
        return ResponseEntity.ok(
                new JobSkillResponse(jobId, name, calcDate, jobTotal, jobTotal < 100, items)
        );
    }

}
