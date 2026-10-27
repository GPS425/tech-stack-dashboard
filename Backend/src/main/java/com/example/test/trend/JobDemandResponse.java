package com.example.test.trend;

import java.util.List;

public record JobDemandResponse(String calcDate,
                                List<Item> items) {
    public record Item(int jobId,
                       String name,
                       int postingCount,
                       double percentage){
    }
}
