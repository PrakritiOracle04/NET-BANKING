package com.oracle.banking.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "BRANCH")
public class Branch implements Serializable {

    @Id
    @Column(name = "BRANCH_ID")
    private String branchId;

    @Column(name = "BRANCH_NAME")
    private String branchName;

    @Column(name = "IFSC")
    private String ifsc;

    @Column(name = "CITY")
    private String city;

    @Column(name = "STATE")
    private String state;

    public Branch() {
    }

    public Branch(String branchId, String branchName, String ifsc, String city, String state) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.ifsc = ifsc;
        this.city = city;
        this.state = state;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}