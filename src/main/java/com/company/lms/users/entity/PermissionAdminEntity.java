package com.company.lms.users.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class PermissionAdminEntity {
    @Id
    private UUID id;
    private String code;
    private String description;
}
