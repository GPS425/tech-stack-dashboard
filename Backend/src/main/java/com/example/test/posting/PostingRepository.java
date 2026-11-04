package com.example.test.posting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    @EntityGraph(attributePaths = "company")
    @Query(value = "select p from Posting p where p.closed = 0 "
            + "and (:allJobs = true or exists (select 1 from p.jobIds j where j in :jobIds)) "
            + "and (:employment = '%' or p.employmentType like :employment) "
            + "and (:region = '%' or p.region like :region) "
            + "and (:skillCount = 0 or (select count(distinct k) from p.skillIds k where k in :skillIds) = :skillCount) "
            + "and (:newOnly = false or p.careerType in ('NEW', 'NEW_OR_EXP', 'ANY')) "
            + "and (:minExp = 0 or (p.careerType = 'EXPERIENCED' and p.expMin >= :minExp)) "
            + "and (:q = '%' or lower(p.title) like :q or lower(p.company.name) like :q "
            + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q "
            + "                and s.id member of p.skillIds)) ",
            countQuery = "select count(p) from Posting p where p.closed = 0 "
                    + "and (:allJobs = true or exists (select 1 from p.jobIds j where j in :jobIds)) "
                    + "and (:employment = '%' or p.employmentType like :employment) "
                    + "and (:region = '%' or p.region like :region) "
                    + "and (:skillCount = 0 or (select count(distinct k) from p.skillIds k where k in :skillIds) = :skillCount) "
                    + "and (:newOnly = false or p.careerType in ('NEW', 'NEW_OR_EXP', 'ANY')) "
                    + "and (:minExp = 0 or (p.careerType = 'EXPERIENCED' and p.expMin >= :minExp)) "
                    + "and (:q = '%' or lower(p.title) like :q or lower(p.company.name) like :q "
                    + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q "
                    + "                and s.id member of p.skillIds))")
    Page<Posting> search(@Param("allJobs") boolean allJobs,
                         @Param("jobIds") List<Integer> jobIds,
                         @Param("employment") String employment,
                         @Param("region") String region,
                         @Param("skillCount") int skillCount,
                         @Param("skillIds") List<Integer> skillIds,
                         @Param("newOnly") boolean newOnly,
                         @Param("minExp") int minExp,
                         @Param("q") String q,
                         Pageable pageable);

    @EntityGraph(attributePaths = "company")
    Optional<Posting> findWithCompanyById(Long id);
}


