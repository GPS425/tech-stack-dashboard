package com.example.test.jobskill;

import com.example.test.common.ApiException;
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
            throw new ApiException(400, "INVALID_LIMIT",
                    "limit은 1~50 사이여야 합니다: " + limit);
        }

        // 없는 직무일시 404 응답
        String name = repository.findJobName(jobId);
        if(name == null){
            throw new ApiException(404, "JOB_NOT_FOUND",
                    "직무를 찾을 수 없습니다: " + jobId);
        }

        // 최신 통계와 기술순위 조회
        JobSkillRepository.Stat stat = repository.findLatestStat(jobId);

        // 통계가 없으면 날짜는 null, 전체 공고 수는 0
        String calcDate = stat == null ? null : stat.calcDate();
        int jobTotal = stat == null ? 0 : stat.jobTotal();

        Double avgTotal = repository.findAvgJobTotal(jobId);
        boolean smallSample = avgTotal == null ? jobTotal < 100 : avgTotal < 100;

        List<JobSkillResponse.Item> items = smallSample
                ? repository.findTopSkillsByCount(jobId)
                : repository.findTopSkills(jobId, limit);

        // 응답 조립, 전체 공고가 100건보다 작으면 smallSample을 트루로
        return ResponseEntity.ok(
                new JobSkillResponse(jobId, name, calcDate, jobTotal, smallSample, items)
        );
    }

}
