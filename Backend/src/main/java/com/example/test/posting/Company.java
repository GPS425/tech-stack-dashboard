package com.example.test.posting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company")
public class Company {

    @Id
    @Column(name = "company_id")
    private Long id;

    @Column(name = "name")
    private String name;

    protected Company(){
    }

    public String getName(){
        return name;
    }
}
