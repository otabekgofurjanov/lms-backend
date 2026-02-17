package com.company.lms.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
@Getter
@Setter
public class EnrollmentEntity {
    @Id
    private UUID id;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "student_id")
    private UUID studentId;

    private String status;

    @Column(name = "enrolled_at")
    private OffsetDateTime enrolledAt;
}
