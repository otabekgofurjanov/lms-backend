package com.company.lms.zoom.repository;

import com.company.lms.zoom.entity.ZoomOauthStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoomOauthStateRepository extends JpaRepository<ZoomOauthStateEntity, String> {
}
