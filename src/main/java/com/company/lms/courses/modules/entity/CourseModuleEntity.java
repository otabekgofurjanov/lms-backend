package com.company.lms.courses.modules.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "course_modules")
@Getter
@Setter
public class CourseModuleEntity {
    @Id
    private UUID id;

    @Column(name = "course_id")
    private UUID courseId;

    private String title;

    @Column(name = "sort_order")
    private int sortOrder;
}
