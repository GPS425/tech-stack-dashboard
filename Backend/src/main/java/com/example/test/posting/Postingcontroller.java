package com.example.test.posting;

import com.example.test.common.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class Postingcontroller {

    private final PostingRepository repository;
    private final PostingTagRepository tagRepository;

    public Postingcontroller(PostingRepository repository, PostingTagRepository tagRepository){
        this.repository = repository;
        this.tagRepository = tagRepository;
    }

    @GetMapping("/api/postings")
    public PostingListResponse getPostings(@RequestParam(required = false) List<Integer> jobIds,
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

        Pageable pageable = PageRequest.of(page - 1, size);

        Page<Posting> result = (jobIds == null || jobIds.isEmpty())
                ? repository.findOpenPostings(pageable)
                : repository.findOpenPostingsByJobs(jobIds, pageable);

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

    private PostingItem toItem(Posting p, List<String> jobs, List<String> skills){
        return new PostingItem(
                p.getId(),
                p.getCompany().getName(),
                p.getTitle(),
                jobs,
                p.getCareerType(),
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
}
