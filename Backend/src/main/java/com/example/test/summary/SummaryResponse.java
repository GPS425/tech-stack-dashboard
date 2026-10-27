package com.example.test.summary;

public record SummaryResponse(
        String calcDate,
        int postingCount,
        int statPostingCount,
        int skillCount,
        int jobCount){
}
