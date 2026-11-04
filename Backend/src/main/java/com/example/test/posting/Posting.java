package com.example.test.posting;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "posting")
public class Posting {

    @Id
    @Column(name = "posting_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "title")
    private String title;

    @Column(name = "url")
    private String url;

    @Column(name = "career_type")
    private String careerType;

    @Column(name = "exp_min")
    private Integer expMin;

    @Column(name = "exp_max")
    private Integer expMax;

    @Column(name = "education")
    private String education;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "region")
    private String region;

    @Column(name = "posted_on")
    private LocalDate postedOn;

    @Column(name = "deadline_type")
    private String deadlineType;

    @Column(name = "deadline_on")
    private LocalDate deadlineOn;

    @Column(name = "is_closed")
    private Integer closed;

    @ElementCollection
    @CollectionTable(name = "posting_job",
    joinColumns = @JoinColumn(name = "posting_id"))
    @Column(name = "job_id")
    private Set<Integer> jobIds;

    @ElementCollection
    @CollectionTable(name = "posting_skill",
    joinColumns = @JoinColumn(name = "posting_id"))
    @Column(name = "skill_id")
    private Set<Integer> skillIds;


    protected Posting(){
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getCareerType() {
        return careerType;
    }

    public Integer getExpMin() {
        return expMin;
    }

    public Integer getExpMax() {
        return expMax;
    }

    public String getEducation() {
        return education;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getRegion() {
        return region;
    }

    public LocalDate getPostedOn() {
        return postedOn;
    }

    public String getDeadlineType() {
        return deadlineType;
    }

    public LocalDate getDeadlineOn() {
        return deadlineOn;
    }

    public Integer getClosed() {
        return closed;
    }
}
