package com.company.lms.courses.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
public class CourseEntity {
    @Id
    private UUID id;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    private String status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
