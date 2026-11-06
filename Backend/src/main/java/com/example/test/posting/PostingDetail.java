package com.example.test.posting;

import java.util.List;

public record PostingDetail(long postingId,
                            String company,
                            String title,
                            List<String> jobs,
                            String career,
                            Integer minExperience,
                            String employmentType,
                            String region,
                            List<String> skills,
                            String postedOn,
                            String deadlineOn,
                            String deadlineType,
                            String url,
                            String education,
                            boolean closed,
                            Double matchRate) {
}
