package com.example.test.analysis;

import java.util.List;

public record AnalysisResponse(int jobId,
                               String name,
                               String calcDate,
                               int fitScore,
                               List<String> marketSkills,
                               List<String> matchedSkills,
                               List<String> missingSkills,
                               List<String> prioritySkills,
                               List<String> unknownSkills) {
}
