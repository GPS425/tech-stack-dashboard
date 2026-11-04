package com.example.test.trend;

import com.example.test.common.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class TrendController {

    private final TrendRepository repository;


    public TrendController(TrendRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/trends/job-demand")
    public JobDemandResponse getJobDemand() {
        return new JobDemandResponse(repository.findCalcDate(), repository.findJobDemand());
    }

    @GetMapping("/api/trends/matrix")
    public ResponseEntity<?> getMatrix(@RequestParam List<Integer> jobIds,
                                       @RequestParam(defaultValue = "6") int limit) {
        if (limit < 1 || limit > 50) {
            throw new ApiException(400, "INVALID_LIMIT",
                    "limit은 1~50 사이여야 합니다: " +limit);
        }

        // jobIds= 처럼 값이 비면 빈 목록으로 들어온다. IN () 이 되지 않게 여기서 막는다
        if (jobIds.isEmpty()) {
            throw new ApiException(400, "MISSING_PARAMETER", "필수 파라미터가 없습니다: jobIds");
        }

        if (jobIds.size() > 50) {
            throw new ApiException(400, "INVALID_PARAMETER",
                    "jobIds는 최대 50개입니다: " + jobIds.size());
        }

        List<MatrixResponse.Job> jobs = repository.findJobs(jobIds);

        if (jobs.size() != jobIds.stream().distinct().count()) {
            throw new ApiException(404, "JOB_NOT_FOUND",
                    "직무를 찾을 수 없습니다: " + jobIds);
        }

        List<TrendRepository.Cell> cells = repository.findMatrixCells(jobIds, limit);

        Map<String, Map<Integer, Double>> table = new LinkedHashMap<>();

        for (TrendRepository.Cell cell : cells) {
            table.computeIfAbsent(cell.skillName(), k -> new HashMap<>())
                    .put(cell.jobId(), cell.percentage());
        }

        List<MatrixResponse.Row> rows = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, Double>> entry : table.entrySet()) {
            String skillName = entry.getKey();
            Map<Integer, Double> byjob = entry.getValue();

            List<Double> values = new ArrayList<>();
            for(MatrixResponse.Job job : jobs){
                values.add(byjob.getOrDefault(job.jobId(), 0.0));
            }

            rows.add(new MatrixResponse.Row(skillName, values));
        }

        return ResponseEntity.ok(new MatrixResponse(repository.findCalcDate(), jobs, rows));

    }

    @GetMapping("/api/trends/combinations")
    public ResponseEntity<?> getCombinations(@RequestParam(required = false) Integer jobId,
                                             @RequestParam(defaultValue = "5") int limit){
        if(limit < 1 || limit > 50){
            throw new ApiException(400, "INVALID_LIMIT",
                    "limit은 1~50 사이여야 합니다: " + limit);
        }

        if(jobId != null && repository.findJobs(List.of(jobId)).isEmpty()){
            throw new ApiException(404, "JOB_NOT_FOUND",
                    "직무를 찾을 수 없습니다: " + jobId);
        }

        List<CombinationsResponse.Item> items = repository.findCombinations(jobId, limit);

        return ResponseEntity.ok(new CombinationsResponse(jobId, items));
    }

}
