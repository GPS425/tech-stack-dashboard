package com.example.test.posting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    // LIKE 패턴의 % _ 는 컨트롤러가 ! 로 이스케이프한다(ESCAPE '!'). 빈 조건은 '%' 그대로 와서 앞의 비교로 건너뛴다
    // 정렬은 Pageable 의 Sort 가 아니라 이 ORDER BY 로 한다(deadline 의 "지난 마감일은 맨 뒤"를 Sort 로 못 나타냄)
    //   latest  : postedOn 최신순(없으면 뒤), 같으면 postingId 큰 순
    //   deadline: 마감일 있음(오늘 포함, 가까운 순) → 마감일 없음 → 마감일 지남, 그 안에서는 latest 와 같은 동률 규칙
    //   matching: 일치율(내 기술 ∩ 공고 활성 기술 ÷ 공고 활성 기술) 높은 순, 같으면 latest 와 같은 동률 규칙
    @EntityGraph(attributePaths = "company")
    @Query(value = "select p from Posting p where p.closed = 0 "
            + "and (:allJobs = true or exists (select 1 from p.jobIds j where j in :jobIds)) "
            + "and (:employment = '%' or p.employmentType like :employment escape '!') "
            + "and (:region = '%' or p.region like :region escape '!') "
            + "and (:skillCount = 0 or (select count(distinct k) from p.skillIds k where k in :skillIds) = :skillCount) "
            + "and (:newOnly = false or p.careerType in ('NEW', 'NEW_OR_EXP', 'ANY')) "
            + "and (:minExp = 0 or (p.careerType = 'EXPERIENCED' and p.expMin >= :minExp)) "
            + "and (:q1 = '%' or lower(p.title) like :q1 escape '!' or lower(p.company.name) like :q1 escape '!' "
            + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q1 escape '!' "
            + "                and s.id member of p.skillIds)) "
            + "and (:q2 = '%' or lower(p.title) like :q2 escape '!' or lower(p.company.name) like :q2 escape '!' "
            + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q2 escape '!' "
            + "                and s.id member of p.skillIds)) "
            + "and (:q3 = '%' or lower(p.title) like :q3 escape '!' or lower(p.company.name) like :q3 escape '!' "
            + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q3 escape '!' "
            + "                and s.id member of p.skillIds)) "
            + "order by case when :matchingSort = false then 0.0 "
            + "              when (select count(distinct k) from p.skillIds k where exists (select 1 from Skill s where s.id = k and s.active = 1)) = 0 then 0.0 "
            + "              else (select count(distinct k) from p.skillIds k where k in :mySkillIds and exists (select 1 from Skill s where s.id = k and s.active = 1)) * 1.0 "
            + "                   / (select count(distinct k) from p.skillIds k where exists (select 1 from Skill s where s.id = k and s.active = 1)) end desc, "
            + "         case when :deadlineSort = false then 0 "
            + "              when p.deadlineOn is null then 1 "
            + "              when p.deadlineOn < :today then 2 "
            + "              else 0 end, "
            + "         case when :deadlineSort = true then p.deadlineOn end, "
            + "         case when p.postedOn > :today then 1 else 0 end, "
            + "         p.postedOn desc nulls last, p.id desc",
            countQuery = "select count(p) from Posting p where p.closed = 0 "
                    + "and (:allJobs = true or exists (select 1 from p.jobIds j where j in :jobIds)) "
                    + "and (:employment = '%' or p.employmentType like :employment escape '!') "
                    + "and (:region = '%' or p.region like :region escape '!') "
                    + "and (:skillCount = 0 or (select count(distinct k) from p.skillIds k where k in :skillIds) = :skillCount) "
                    + "and (:newOnly = false or p.careerType in ('NEW', 'NEW_OR_EXP', 'ANY')) "
                    + "and (:minExp = 0 or (p.careerType = 'EXPERIENCED' and p.expMin >= :minExp)) "
                    + "and (:q1 = '%' or lower(p.title) like :q1 escape '!' or lower(p.company.name) like :q1 escape '!' "
                    + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q1 escape '!' "
                    + "                and s.id member of p.skillIds)) "
                    + "and (:q2 = '%' or lower(p.title) like :q2 escape '!' or lower(p.company.name) like :q2 escape '!' "
                    + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q2 escape '!' "
                    + "                and s.id member of p.skillIds)) "
                    + "and (:q3 = '%' or lower(p.title) like :q3 escape '!' or lower(p.company.name) like :q3 escape '!' "
                    + "     or exists (select 1 from Skill s where s.active = 1 and lower(s.name) like :q3 escape '!' "
                    + "                and s.id member of p.skillIds)) ")
    Page<Posting> search(@Param("allJobs") boolean allJobs,
                         @Param("jobIds") List<Integer> jobIds,
                         @Param("employment") String employment,
                         @Param("region") String region,
                         @Param("skillCount") int skillCount,
                         @Param("skillIds") List<Integer> skillIds,
                         @Param("newOnly") boolean newOnly,
                         @Param("minExp") int minExp,
                         @Param("q1") String q1,
                         @Param("q2") String q2,
                         @Param("q3") String q3,
                         @Param("deadlineSort") boolean deadlineSort,
                         @Param("matchingSort") boolean matchingSort,
                         @Param("mySkillIds") List<Integer> mySkillIds,
                         @Param("today") LocalDate today,
                         Pageable pageable);

    @EntityGraph(attributePaths = "company")
    Optional<Posting> findWithCompanyById(Long id);
}


