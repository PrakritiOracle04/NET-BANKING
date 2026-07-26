package com.oracle.banking.branch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "BRANCH")
public class Branch {
    @Id
    @Column(name = "BRANCH_ID", length = 36)
    private String branchId;
    @Column(name = "BRANCH_NAME", nullable = false, length = 120)
    private String branchName;
    @Column(name = "IFSC", nullable = false, unique = true, length = 11)
    private String ifsc;
    @Column(name = "CITY", nullable = false, length = 80)
    private String city;
    @Column(name = "STATE", nullable = false, length = 80)
    private String state;

    protected Branch() {
    }

    public Branch(String branchId, String branchName, String ifsc, String city, String state) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.ifsc = ifsc;
        this.city = city;
        this.state = state;
    }

    public String getBranchId() { return branchId; }
    public String getBranchName() { return branchName; }
    public String getIfsc() { return ifsc; }
    public String getCity() { return city; }
    public String getState() { return state; }
}
