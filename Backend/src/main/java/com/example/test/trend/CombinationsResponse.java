package com.example.test.trend;

import java.util.List;

public record CombinationsResponse(Integer jobId,
                                   List<Item> items) {
    public record Item(List<String> skills,
                       int postingCount){
    }
}
