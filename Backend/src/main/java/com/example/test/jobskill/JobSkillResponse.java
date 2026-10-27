package com.example.test.jobskill;

import java.util.List;

public record JobSkillResponse(int jobId,
                               String name,
                               String calcDate,
                               int jobTotal,
                               boolean smallSample,
                               List<Item> items) {
    public record Item(int rank,
                       int skillId,
                       String name,
                       String category,
                       double percentage,
                       int postingCount) {

    }
}
