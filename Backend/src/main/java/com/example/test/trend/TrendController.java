package com.example.test.trend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrendController {

    private final TrendRepository repository;

    public TrendController(TrendRepository repository){
        this.repository = repository;
    }

    @GetMapping("/api/trends/job-demand")
    public JobDemandResponse getJobDemand(){
        return new JobDemandResponse(repository.findCalcDate(), repository.findJobDemand());
    }

}
