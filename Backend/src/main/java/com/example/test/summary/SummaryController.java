package com.example.test.summary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SummaryController {

    private final SummaryRepository repository;

    public SummaryController(SummaryRepository repository){
        this.repository = repository;
    }

    @GetMapping("/api/summary")
    public SummaryResponse getSummary(){
        return repository.find();
    }

}
