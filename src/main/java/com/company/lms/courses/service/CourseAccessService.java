package com.company.lms.courses.service;

import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseAccessService {
    private final UserRepository userRepository;

    public UserEntity requireActor(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }

    public void assertCanManageCourse(UserEntity actor, CourseEntity course) {
        boolean admin = actor.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
        boolean teacher = actor.getRoles().stream().anyMatch(r -> "TEACHER".equals(r.getCode()));
        if (admin) {
            return;
        }
        if (teacher && actor.getId().equals(course.getCreatedBy())) {
            return;
        }
        throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot modify this course");
    }
}
