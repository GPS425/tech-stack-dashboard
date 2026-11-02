package com.example.test.trend;

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
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "code", "INVALID_LIMIT",
                    "message", "limit은 1~50 사이여야 합니다: " + limit
            ));
        }

        List<MatrixResponse.Job> jobs = repository.findJobs(jobIds);

        if (jobs.size() != jobIds.stream().distinct().count()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404,
                    "code", "JOB_NOT_FOUND",
                    "message", "직무를 찾을 수 없습니다: " + jobIds
            ));
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
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "code", "INVALID_LIMIT",
                    "message", "limit은 1~50 사이여야 합니다: " + limit
            ));
        }

        if(jobId != null && repository.findJobs(List.of(jobId)).isEmpty()){
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404,
                    "code", "JOB_NOT_FOUND",
                    "message", "직무를 찾을 수 없습니다: " + jobId
            ));
        }

        List<CombinationsResponse.Item> items = repository.findCombinations(jobId, limit);

        return ResponseEntity.ok(new CombinationsResponse(jobId, items));
    }

}
