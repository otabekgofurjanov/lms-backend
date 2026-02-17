package com.company.lms.courses.lessons.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter
@Setter
public class LessonEntity {
    @Id
    private UUID id;

    @Column(name = "module_id")
    private UUID moduleId;

    private String title;

    @Column(name = "lesson_type")
    private String lessonType;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "available_at")
    private OffsetDateTime availableAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
