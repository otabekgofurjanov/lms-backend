package com.company.lms.zoom.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.service.EnrollmentService;
import com.company.lms.zoom.client.ZoomClient;
import com.company.lms.zoom.client.ZoomProperties;
import com.company.lms.zoom.dto.*;
import com.company.lms.zoom.entity.*;
import com.company.lms.zoom.mapper.ZoomMeetingMapper;
import com.company.lms.zoom.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoomService {
    private final ZoomProperties zoomProperties;
    private final ZoomClient zoomClient;
    private final ZoomOauthStateRepository oauthStateRepository;
    private final ZoomAccountRepository zoomAccountRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final LessonRepository lessonRepository;
    private final CourseModuleRepository moduleRepository;
    private final com.company.lms.courses.repository.CourseRepository courseRepository;
    private final CourseAccessService courseAccessService;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final ZoomMeetingMapper zoomMeetingMapper;
    private final EnrollmentService enrollmentService;
    private final ZoomWebhookProcessingService webhookProcessingService;

    @Transactional
    public ZoomConnectUrlResponse connectUrl(String actorEmail) {
        UserEntity actor = requireUser(actorEmail);
        String state = UUID.randomUUID().toString();

        ZoomOauthStateEntity stateEntity = new ZoomOauthStateEntity();
        stateEntity.setState(state);
        stateEntity.setOwnerUserId(actor.getId());
        stateEntity.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        stateEntity.setCreatedAt(OffsetDateTime.now());
        oauthStateRepository.save(stateEntity);

        String url = zoomProperties.oauthBaseUrl() + "/oauth/authorize?response_type=code&client_id=" + zoomProperties.clientId()
                + "&redirect_uri=" + zoomProperties.redirectUri() + "&state=" + state;
        return new ZoomConnectUrlResponse(url);
    }

    @Transactional
    public String oauthCallback(String code, String state, String actorEmail, HttpServletRequest request) {
        UserEntity actor = requireUser(actorEmail);
        ZoomOauthStateEntity stateEntity = oauthStateRepository.findById(state)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "OAuth state is invalid"));
        if (stateEntity.getExpiresAt().isBefore(OffsetDateTime.now()) || !stateEntity.getOwnerUserId().equals(actor.getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "OAuth state is expired or invalid");
        }

        ZoomOAuthTokenResponse token = zoomClient.exchangeCode(code);
        ZoomUserProfileResponse profile = zoomClient.currentUser(token.accessToken());
        oauthStateRepository.delete(stateEntity);

        ZoomAccountEntity account = zoomAccountRepository.findByOwnerUserId(actor.getId()).orElseGet(() -> {
            ZoomAccountEntity e = new ZoomAccountEntity();
            e.setId(UUID.randomUUID());
            e.setOwnerUserId(actor.getId());
            e.setCreatedAt(OffsetDateTime.now());
            return e;
        });

        account.setAccountEmail(profile.email());
        account.setAccessToken(token.accessToken());
        account.setRefreshToken(token.refreshToken());
        account.setTokenExpiresAt(OffsetDateTime.now().plusSeconds(Optional.ofNullable(token.expiresIn()).orElse(3600L)));
        account.setStatus("CONNECTED");
        account.setUpdatedAt(OffsetDateTime.now());
        zoomAccountRepository.save(account);

        auditService.log(actor.getId(), "ZOOM_CONNECT", "ZOOM_ACCOUNT", account.getId().toString(), null, "{}", request.getRemoteAddr(), request.getHeader("User-Agent"));
        return "connected";
    }

    @Transactional
    public String disconnect(String actorEmail, HttpServletRequest request) {
        UserEntity actor = requireUser(actorEmail);
        ZoomAccountEntity account = zoomAccountRepository.findByOwnerUserId(actor.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ZOOM_ACCOUNT_NOT_FOUND", "Zoom account not connected"));
        String before = account.getStatus();

        account.setStatus("DISCONNECTED");
        account.setAccessToken(null);
        account.setRefreshToken(null);
        account.setTokenExpiresAt(null);
        account.setUpdatedAt(OffsetDateTime.now());
        zoomAccountRepository.save(account);

        auditService.log(actor.getId(), "ZOOM_DISCONNECT", "ZOOM_ACCOUNT", account.getId().toString(), before, "DISCONNECTED", request.getRemoteAddr(), request.getHeader("User-Agent"));
        return "disconnected";
    }

    @Transactional
    public ZoomMeetingResponse createMeeting(UUID lessonId, ZoomMeetingCreateRequest req, String actorEmail, HttpServletRequest request) {
        UserEntity actor = requireUser(actorEmail);
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        if (!"LIVE_ZOOM".equals(lesson.getLessonType())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_TYPE", "Lesson must be LIVE_ZOOM");
        }

        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        CourseEntity course = courseRepository.findById(module.getCourseId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
        courseAccessService.assertCanManageCourse(actor, course);

        ZoomAccountEntity account = zoomAccountRepository.findByOwnerUserId(actor.getId())
                .filter(a -> "CONNECTED".equals(a.getStatus()) && a.getAccessToken() != null)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "ZOOM_NOT_CONNECTED", "Zoom account is not connected"));

        ZoomMeetingCreateZoomResponse zoomResponse = zoomClient.createMeeting(account.getAccessToken(), req);

        ZoomMeetingEntity meeting = zoomMeetingRepository.findByLessonId(lessonId).orElseGet(() -> {
            ZoomMeetingEntity e = new ZoomMeetingEntity();
            e.setId(UUID.randomUUID());
            e.setLessonId(lessonId);
            e.setCreatedAt(OffsetDateTime.now());
            return e;
        });
        meeting.setZoomAccountId(account.getId());
        meeting.setZoomMeetingId(zoomResponse.id());
        meeting.setJoinUrl(zoomResponse.joinUrl());
        meeting.setStartTime(req.startTime());
        meeting.setDurationMinutes(req.durationMinutes());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedBy(actor.getId());
        ZoomMeetingEntity saved = zoomMeetingRepository.save(meeting);

        ZoomMeetingResponse response = zoomMeetingMapper.toResponse(saved);
        auditService.log(actor.getId(), "ZOOM_MEETING_CREATE", "ZOOM_MEETING", saved.getId().toString(), null, toJson(response), request.getRemoteAddr(), request.getHeader("User-Agent"));
        return response;
    }

    @Transactional(readOnly = true)
    public ZoomMeetingResponse getMeeting(UUID lessonId, String actorEmail) {
        UserEntity actor = requireUser(actorEmail);
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        CourseEntity course = courseRepository.findById(module.getCourseId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
        courseAccessService.assertCanManageCourse(actor, course);

        ZoomMeetingEntity meeting = zoomMeetingRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ZOOM_MEETING_NOT_FOUND", "Zoom meeting not found"));
        return zoomMeetingMapper.toResponse(meeting);
    }

    @Transactional(readOnly = true)
    public StudentJoinLinkResponse studentJoinLink(UUID lessonId, String studentEmail) {
        UserEntity student = requireUser(studentEmail);
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));

        if (!enrollmentService.hasActiveEnrollment(module.getCourseId(), student.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment required");
        }

        ZoomMeetingEntity meeting = zoomMeetingRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ZOOM_MEETING_NOT_FOUND", "Zoom meeting not found"));
        return new StudentJoinLinkResponse(meeting.getJoinUrl());
    }

    public void receiveWebhook(String signature, String timestamp, String rawBody) {
        if (!isValidSignature(signature, timestamp, rawBody)) {
            throw new AppException(HttpStatus.FORBIDDEN, "INVALID_SIGNATURE", "Invalid Zoom webhook signature");
        }
        webhookProcessingService.processWebhookAsync(rawBody);
    }

    private boolean isValidSignature(String signatureHeader, String timestamp, String body) {
        if (signatureHeader == null || timestamp == null || zoomProperties.webhookSecret() == null) {
            return false;
        }
        try {
            String message = "v0:" + timestamp + ":" + body;
            Mac sha256 = Mac.getInstance("HmacSHA256");
            sha256.init(new SecretKeySpec(zoomProperties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hash = HexFormat.of().formatHex(sha256.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            String expected = "v0=" + hash;
            return expected.equals(signatureHeader);
        } catch (Exception e) {
            return false;
        }
    }

    private UserEntity requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
