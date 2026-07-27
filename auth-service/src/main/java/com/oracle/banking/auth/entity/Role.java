package com.oracle.banking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ROLES")
public class Role {
    @Id
    @Column(name = "ROLE_ID", length = 36)
    private String roleId;

    @Column(name = "ROLE_NAME", nullable = false, unique = true, length = 30)
    private String roleName;

    protected Role() {
    }

    public Role(String roleId, String roleName) {
        this.roleId = roleId;
        this.roleName = roleName;
    }

    public String getRoleName() {
        return roleName;
    }
}
