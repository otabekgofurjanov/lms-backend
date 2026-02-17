package com.company.lms.video;

import com.company.lms.audit.repository.AuditLogRepository;
import com.company.lms.video.repository.VideoProgressRepository;
import com.company.lms.video.service.VideoProgressFlushJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VideoProgressIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired VideoProgressFlushJob flushJob;
    @Autowired VideoProgressRepository videoProgressRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Test
    void sessionProgressRateLimitFlushAndSuspiciousAudit() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher P", "teacher.p@local", "+998931111111", "Teacher123!", "TEACHER");
        createUser(admin, "Student P", "student.p@local", "+998932222222", "Student123!", "STUDENT");
        createUser(admin, "Student P2", "student.p2@local", "+998933333333", "Student123!", "STUDENT");

        String teacher = token("teacher.p@local", "Teacher123!");
        String student = token("student.p@local", "Student123!");
        String student2 = token("student.p2@local", "Student123!");

        String courseId = createActiveCourse(teacher, "Progress Course");
        String moduleId = createModule(teacher, courseId, "m1");
        String lessonId = createLesson(teacher, moduleId, "l1", "RECORDED");

        String studentId = userIdByEmail(admin, "student.p@local");
        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + studentId + "\"]}"))
                .andExpect(status().isOk());

        String sessionBody = mockMvc.perform(post("/api/student/lessons/{lessonId}/video/session", lessonId)
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(sessionBody).get("data").get("sessionId").asText();

        String t1 = OffsetDateTime.now().minusSeconds(10).toString();
        mockMvc.perform(post("/api/student/lessons/{lessonId}/video/progress", lessonId)
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"sessionId\":\"" + sessionId + "\"," +
                                "\"currentSecond\":10," +
                                "\"watchedDeltaSeconds\":10," +
                                "\"totalSeconds\":100," +
                                "\"eventTime\":\"" + t1 + "\"," +
                                "\"tabVisible\":true," +
                                "\"tabSwitchCountDelta\":2," +
                                "\"seekAttemptDelta\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/student/lessons/{lessonId}/video/progress", lessonId)
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"sessionId\":\"" + sessionId + "\"," +
                                "\"currentSecond\":11," +
                                "\"watchedDeltaSeconds\":1," +
                                "\"totalSeconds\":100," +
                                "\"eventTime\":\"" + OffsetDateTime.now().minusSeconds(9) + "\"," +
                                "\"tabVisible\":true," +
                                "\"tabSwitchCountDelta\":0," +
                                "\"seekAttemptDelta\":0}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/student/lessons/{lessonId}/video/progress", lessonId)
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"sessionId\":\"" + sessionId + "\"," +
                                "\"currentSecond\":16," +
                                "\"watchedDeltaSeconds\":6," +
                                "\"totalSeconds\":120," +
                                "\"eventTime\":\"" + OffsetDateTime.now().plusSeconds(10) + "\"," +
                                "\"tabVisible\":true," +
                                "\"tabSwitchCountDelta\":0," +
                                "\"seekAttemptDelta\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/student/lessons/{lessonId}/video/progress", lessonId)
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/student/lessons/{lessonId}/video/session", lessonId)
                        .header("Authorization", "Bearer " + student2))
                .andExpect(status().isForbidden());

        long auditBefore = auditLogRepository.count();
        mockMvc.perform(post("/api/student/lessons/{lessonId}/video/progress", lessonId)
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"sessionId\":\"" + sessionId + "\"," +
                                "\"currentSecond\":20," +
                                "\"watchedDeltaSeconds\":8," +
                                "\"totalSeconds\":100," +
                                "\"eventTime\":\"" + OffsetDateTime.now().plusSeconds(20) + "\"," +
                                "\"tabVisible\":false," +
                                "\"tabSwitchCountDelta\":6," +
                                "\"seekAttemptDelta\":4}"))
                .andExpect(status().isOk());

        flushJob.flush();
        assertThat(videoProgressRepository.count()).isGreaterThan(0);
        assertThat(auditLogRepository.count()).isGreaterThan(auditBefore);

        mockMvc.perform(get("/api/teacher/courses/{courseId}/video-progress", courseId)
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/courses/{courseId}/video-progress", courseId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private String token(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    private void createUser(String adminToken, String fullName, String email, String phone, String password, String role) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"fullName\":\"" + fullName + "\"," +
                                "\"email\":\"" + email + "\"," +
                                "\"phone\":\"" + phone + "\"," +
                                "\"password\":\"" + password + "\"," +
                                "\"roles\":[\"" + role + "\"]}"))
                .andExpect(status().isOk());
    }

    private String createActiveCourse(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"desc\",\"coverUrl\":null}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("data").get("id").asText();
        mockMvc.perform(patch("/api/courses/{id}/status", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String createModule(String token, String courseId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/courses/{courseId}/modules", courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asText();
    }

    private String createLesson(String token, String moduleId, String title, String type) throws Exception {
        String body = mockMvc.perform(post("/api/modules/{moduleId}/lessons", moduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"lessonType\":\"" + type + "\",\"availableAt\":null}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asText();
    }

    private String userIdByEmail(String adminToken, String email) throws Exception {
        String body = mockMvc.perform(get("/api/admin/users").param("search", email)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("items").get(0).get("id").asText();
    }
}
