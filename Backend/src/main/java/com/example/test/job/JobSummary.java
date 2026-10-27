package com.example.test.job;

public record JobSummary(int jobId,
                         String name,
                         int postingCount,
                         int jobTotal,
                         boolean smallSample) {
}
