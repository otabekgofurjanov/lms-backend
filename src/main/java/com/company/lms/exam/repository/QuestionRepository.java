package com.company.lms.exam.repository;

import com.company.lms.exam.entity.QuestionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID> {
    @Query("""
            select q from QuestionEntity q
            where (:search is null or lower(q.text) like lower(concat('%', :search, '%')))
            """)
    Page<QuestionEntity> search(@Param("search") String search, Pageable pageable);

    @Query("""
            select q from QuestionEntity q
            where q.createdBy = :createdBy
            and (:search is null or lower(q.text) like lower(concat('%', :search, '%')))
            """)
    Page<QuestionEntity> searchByCreator(@Param("createdBy") UUID createdBy, @Param("search") String search, Pageable pageable);

    List<QuestionEntity> findByIdIn(Collection<UUID> ids);
}
