package com.company.lms.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "questions")
@Getter
@Setter
public class QuestionEntity {
    @Id
    private UUID id;

    @Column(name = "question_type")
    private String questionType;

    @Column(name = "text")
    private String text;

    @Column(name = "options", columnDefinition = "jsonb")
    private String options;

    @Column(name = "correct_index")
    private Integer correctIndex;

    private String explanation;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
