package com.example.test.analysis;

import com.example.test.common.ApiException;
import com.example.test.trend.MatrixResponse;
import com.example.test.trend.TrendRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AnalysisController {

    private final TrendRepository repository;

    public AnalysisController(TrendRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/api/analysis")
    public ResponseEntity<?> analyze(@RequestBody AnalysisRequest request) {

        if (request.jobId() == null) {
            throw new ApiException(400, "INVALID_BODY", "jobId가 필요합니다");
        }

        if (request.skills() == null) {
            throw new ApiException(400, "INVALID_SKILLS", "기술은 1~10개여야 합니다");
        }

        if (request.skills().size() < 1 || request.skills().size() > 10) {
            throw new ApiException(400, "INVALID_SKILLS",
                    "기술은 1~10개여야 합니다: " + request.skills().size());
        }

        if (request.skills().contains(null)) {
            throw new ApiException(400, "INVALID_SKILLS", "기술 이름에 빈 값(null)이 있습니다");
        }

        List<MatrixResponse.Job> jobs = repository.findJobs(List.of(request.jobId()));

        if (jobs.isEmpty()) {
            throw new ApiException(404, "JOB_NOT_FOUND",
                    "직무를 찾을 수 없습니다: " + request.jobId());
        }

        List<String> aliases = request.skills().stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .toList();

        List<TrendRepository.MarketSkills> market = repository.marketSkills(request.jobId());
        List<TrendRepository.SkillMatch> matches = repository.findSkillsByAlias(aliases);

        Set<String> found = matches.stream()
                .map(m -> m.alias())
                .collect(Collectors.toSet());

        List<String> unknown = request.skills().stream()
                .filter(s -> !found.contains(s.trim().toLowerCase(Locale.ROOT)))
                .toList();

        List<String> marketNames = market.stream()
                .map(m -> m.skillName())
                .toList();

        Set<String> myNames = matches.stream()
                .map(m -> m.name())
                .collect(Collectors.toSet());

        List<String> matched = marketNames.stream()
                .filter(n -> myNames.contains(n))
                .toList();

        List<String> missing = marketNames.stream()
                .filter(n -> !myNames.contains(n))
                .toList();

        double total = market.stream()
                .mapToDouble(m -> m.ratio())
                .sum();

        double mine = market.stream()
                .filter(m -> myNames.contains(m.skillName()))
                .mapToDouble(m -> m.ratio())
                .sum();

        // 시장 기술이 없으면(통계 없음) 0 으로 나누지 않고 0 점
        int fitScore = total == 0 ? 0 : (int) Math.round(mine / total * 100);

        List<String> priority = missing.stream()
                .limit(3)
                .toList();

        AnalysisResponse response = new AnalysisResponse(
                request.jobId(),
                jobs.get(0).name(),
                repository.findCalcDate(),
                fitScore,
                marketNames,
                matched,
                missing,
                priority,
                unknown
        );

        return ResponseEntity.ok(response);
    }


}
