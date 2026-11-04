package com.example.test.posting;

import com.example.test.common.ApiException;
import com.example.test.trend.TrendRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class PostingController {

    private final PostingRepository repository;
    private final PostingTagRepository tagRepository;
    private final TrendRepository trendRepository;

    public PostingController(PostingRepository repository, PostingTagRepository tagRepository, TrendRepository trendRepository){
        this.repository = repository;
        this.tagRepository = tagRepository;
        this.trendRepository = trendRepository;
    }

    @GetMapping("/api/postings")
    public PostingListResponse getPostings(@RequestParam(required = false) List<Integer> jobIds,
                                           @RequestParam(required = false) String employment,
                                           @RequestParam(required = false) String region,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) List<String> skills,
                                           @RequestParam(required = false) String career,
                                           @RequestParam(defaultValue = "latest") String sort,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size){
        if(page < 1 || page > 100000){
            throw new ApiException(400, "INVALID_PAGE",
                    "page는 1~100000 사이여야 합니다: " + page);
        }

        if(size < 1 || size > 50){
            throw new ApiException(400, "INVALID_SIZE",
                    "size는 1~50 사이여야 합니다: " + size);
        }

        if(career != null && !List.of("NEW", "EXP1", "EXP3", "EXP5").contains(career)){
            throw new ApiException(400, "INVALID_CAREER", "career는 NEW, EXP1, EXP3, EXP5 중 하나여야 합니다: " + career);
        }

        if(!"latest".equals(sort) && !"deadline".equals(sort)){
            throw new ApiException(400, "INVALID_SORT", "sort는 latest, deadline 중 하나여야 합니다: " + sort);
        }

        // "Java," 처럼 끝에 쉼표가 있으면 빈 원소가 생긴다. 빼고 센다
        List<String> skillNames = skills == null ? List.of()
                : skills.stream().filter(m -> m != null && !m.isBlank()).map(m -> m.trim()).toList();

        if(jobIds != null && jobIds.size() > 50){
            throw new ApiException(400, "INVALID_PARAMETER", "jobIds는 최대 50개입니다: " + jobIds.size());
        }

        if(skillNames.size() > 50){
            throw new ApiException(400, "INVALID_PARAMETER", "skills는 최대 50개입니다: " + skillNames.size());
        }

        boolean allJobs = jobIds == null || jobIds.isEmpty();

        if(!allJobs && trendRepository.findJobs(jobIds).size() != jobIds.stream().distinct().count()){
            throw new ApiException(404, "JOB_NOT_FOUND", "직무를 찾을 수 없습니다: " + jobIds);
        }

        // 정렬은 쿼리의 ORDER BY 가 한다(PostingRepository.search 주석)
        Pageable pageable = PageRequest.of(page - 1, size);
        boolean deadlineSort = "deadline".equals(sort);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<Integer> queryJobIds = allJobs ? List.of(-1) : jobIds;
        String employmentPattern = employment == null || employment.isBlank() ? "%" : "%" + escapeLike(employment.trim()) + "%";
        String regionPattern = region == null || region.isBlank() ? "%" : escapeLike(region.trim()) + "%";
        String qPattern = q == null || q.isBlank() ? "%" : "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        List<String> aliases = skillNames.stream().map(m -> m.toLowerCase(Locale.ROOT)).distinct().toList();
        boolean newOnly = "NEW".equals(career);

        int minExp = 0;
        if("EXP1".equals(career)) minExp = 1;
        else if ("EXP3".equals(career)) minExp = 3;
        else if ("EXP5".equals(career)) minExp = 5;
        int skillCount = 0;
        List<Integer> querySkillIds = List.of(-1);

        if(!aliases.isEmpty()){
            List<TrendRepository.SkillMatch> matches = trendRepository.findSkillsByAlias(aliases);

            Set<String> found = matches.stream()
                    .map(m -> m.alias())
                    .collect(Collectors.toSet());

            if (found.size() < aliases.size()){
                return new PostingListResponse(page, size, 0, List.of());
            }

            querySkillIds = matches.stream()
                    .map(m -> m.skillId())
                    .distinct()
                    .toList();
            skillCount = querySkillIds.size();
        }

        Page<Posting> result = repository.search(allJobs, queryJobIds, employmentPattern, regionPattern
                , skillCount, querySkillIds, newOnly, minExp, qPattern, deadlineSort, today, pageable);

        List<Posting> postings = result.getContent();
        List<Long> ids = postings.stream()
                .map(p -> p.getId())
                .toList();

        Map<Long, List<String>> jobsById = groupByPosting(tagRepository.findJobNames(ids));
        Map<Long, List<String>> skillsById = groupByPosting(tagRepository.findSkillNames(ids));

        List<PostingItem> items = postings.stream()
                .map(p -> toItem(p,
                        jobsById.getOrDefault(p.getId(), List.of()),
                        skillsById.getOrDefault(p.getId(), List.of())))
                .toList();

        return new PostingListResponse(page, size, result.getTotalElements(), items);
    }

    private Map<Long, List<String>> groupByPosting(List<PostingTagRepository.Tag> tags){
        Map<Long, List<String>> map = new HashMap<>();
        for(PostingTagRepository.Tag tag : tags){
            map.computeIfAbsent(tag.postingId(), k -> new ArrayList<>()).add(tag.name());
        }
        return map;
    }

    // LIKE 의 % _ 를 글자 그대로 찾게 ! 로 이스케이프한다(쿼리는 ESCAPE '!')
    private String escapeLike(String s){
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String careerLabel(Posting p){
        Integer min = p.getExpMin();
        Integer max = p.getExpMax();
        if(p.getCareerType() == null) return "미정";
        else if("NEW".equals(p.getCareerType())) return "신입";
        else if("ANY".equals(p.getCareerType())) return "경력무관";
        else if("NEW_OR_EXP".equals(p.getCareerType())) return "신입, 경력";
        else if ("EXPERIENCED".equals(p.getCareerType())){
            if((min == null || min == 0) && max == null) return "경력";
            else if (max == null) return "경력 " + min + "년 이상";
            else if (min == null) return "경력 " + max + "년 이하";
            else if (min.equals(max)) return "경력 " + min + "년";
            else return "경력 " + min + "~" + max + "년";
        }
        else return "미정";
    }

    private PostingItem toItem(Posting p, List<String> jobs, List<String> skills){
        return new PostingItem(
                p.getId(),
                p.getCompany().getName(),
                p.getTitle(),
                jobs,
                careerLabel(p),
                p.getExpMin(),
                p.getEmploymentType(),
                p.getRegion(),
                skills,
                p.getPostedOn() == null ? null : p.getPostedOn().toString(),
                p.getDeadlineOn() == null ? null : p.getDeadlineOn().toString(),
                p.getDeadlineType(),
                p.getUrl(),
                null
        );
    }

    @GetMapping("/api/postings/{postingId}")
    public PostingDetail getPosting(@PathVariable long postingId){
        Posting p = repository.findWithCompanyById(postingId)
                .orElseThrow(() -> new ApiException(404, "POSTING_NOT_FOUND", "공고를 찾을 수 없습니다: " + postingId));

        List<Long> ids = List.of(p.getId());
        Map<Long, List<String>> jobsById = groupByPosting(tagRepository.findJobNames(ids));
        Map<Long, List<String>> skillById = groupByPosting(tagRepository.findSkillNames(ids));
        List<String> jobs = jobsById.getOrDefault(p.getId(), List.of());
        List<String> skills = skillById.getOrDefault(p.getId(), List.of());

        return new PostingDetail(
                p.getId(),
                p.getCompany().getName(),
                p.getTitle(),
                jobs,
                careerLabel(p),
                p.getExpMin(),
                p.getEmploymentType(),
                p.getRegion(),
                skills,
                p.getPostedOn() == null ? null : p.getPostedOn().toString(),
                p.getDeadlineOn() == null ? null : p.getDeadlineOn().toString(),
                p.getDeadlineType(),
                p.getUrl(),
                p.getEducation(),
                p.getClosed() != null && p.getClosed() == 1
        );
    }
}
