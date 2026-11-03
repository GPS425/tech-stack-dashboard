package com.example.test.analysis;

import java.util.List;

public record AnalysisRequest(int jobId,
                              List<String> skills) {
}
