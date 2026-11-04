package com.example.test.posting;

import com.example.test.common.ApiException;
import com.example.test.trend.TrendRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;

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
        if(page < 1){
            throw new ApiException(400, "INVALID_PAGE",
                    "page는 1 이상이어야 합니다: " + page);
        }

        if(size < 1 || size > 50){
            throw new ApiException(400, "INVALID_SIZE",
                    "size는 1~50 사이여야 합니다: " + size);
        }

        if(career != null && !List.of("NEW", "EXP1", "EXP3", "EXP5").contains(career)){
            throw new ApiException(400, "INVALID_CAREER", "career는 NEW, EXP1, EXP3, EXP5중 하나여야 합니다: " + career);
        }

        Sort order;
        if("latest".equals(sort)){
            order = Sort.by(Sort.Order.desc("postedOn").nullsLast(), Sort.Order.desc("id"));
        }else if ("deadline".equals(sort)){
            order = Sort.by(Sort.Order.asc("deadlineOn").nullsLast(),
                    Sort.Order.desc("postedOn").nullsLast(),
                    Sort.Order.desc("id"));
        }else {
            throw new ApiException(400, "INVALID_SORT", "sort는 latest, deadline중 하나여야 합니다: " + sort);
        }

        Pageable pageable = PageRequest.of(page - 1, size, order);

        boolean allJobs = jobIds == null || jobIds.isEmpty();
        List<Integer> queryJobIds = allJobs ? List.of(-1) : jobIds;
        String employmentPattern = employment == null || employment.isBlank() ? "%" : "%" + employment + "%";
        String regionPattern = region == null || region.isBlank() ? "%" : region + "%";
        String qPattern = q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase() + "%";
        List<String> aliases = skills == null ? List.of() : skills.stream().map(m -> m.trim().toLowerCase()).distinct().toList();
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
                , skillCount, querySkillIds, newOnly, minExp, qPattern, pageable);

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

    private String careerLabel(Posting p){
        if("NEW".equals(p.getCareerType())) return "신입";
        else if("ANY".equals(p.getCareerType())) return "경력무관";
        else if("NEW_OR_EXP".equals(p.getCareerType())) return "신입, 경력";
        else if ("EXPERIENCED".equals(p.getCareerType())){
            if(p.getExpMin() == null && p.getExpMax() == null) return "경력";
            else if (p.getExpMin() != null && p.getExpMax() == null) return "경력 " + p.getExpMin() + "년 이상";
            else return "경력 " + p.getExpMin() + "~" + p.getExpMax() + "년";
        }
        else return "잘못된 값입니다.";
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
