package com.company.lms.completion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_progress")
@Getter
@Setter
public class CourseProgressEntity {
    @Id
    private UUID id;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "attendance_pct")
    private BigDecimal attendancePct;

    private String status;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
