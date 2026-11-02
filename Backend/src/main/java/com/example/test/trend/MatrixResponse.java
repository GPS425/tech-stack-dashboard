package com.example.test.trend;

import java.util.List;

public record MatrixResponse(String calcDate,
                             List<Job> jobs,
                             List<Row> rows) {
    public record Job(int jobId,
                      String name){
    }

    public record Row(String name,
                      List<Double> values){
    }
}
