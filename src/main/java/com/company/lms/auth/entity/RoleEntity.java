package com.company.lms.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter @Setter
public class RoleEntity {
    @Id
    private UUID id;
    private String code;
}
