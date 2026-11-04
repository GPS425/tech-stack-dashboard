package com.example.test.analysis;

import java.util.List;

public record AnalysisRequest(Integer jobId,
                              List<String> skills) {
}
