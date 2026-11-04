package com.example.test.posting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skill")
public class Skill {

    @Id
    @Column(name = "skill_id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "is_active")
    private Integer active;

    protected Skill(){
    }

    public String getName(){
    return name;
    }


}
