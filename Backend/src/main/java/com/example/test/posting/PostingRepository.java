package com.example.test.posting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    @EntityGraph(attributePaths = "company")
    @Query(value = "select p from Posting p where p.closed = 0 "
            + "order by p.postedOn desc nulls last, p.id desc",
            countQuery = "select count(p) from Posting p where p.closed = 0")
    Page<Posting> findOpenPostings(Pageable pageable);

    @EntityGraph(attributePaths = "company")
    @Query(value = "select p from Posting p where p.closed = 0 "
            + "and exists (select 1 from p.jobIds j where j in :jobIds) "
            + "order by p.postedOn desc nulls last, p.id desc",
            countQuery = "select count(p) from Posting p where p.closed = 0 "
                    + "and exists (select 1 from p.jobIds j where j in :jobIds)")
    Page<Posting> findOpenPostingsByJobs(@Param("jobIds") List<Integer> jobids, Pageable pageable);


}
