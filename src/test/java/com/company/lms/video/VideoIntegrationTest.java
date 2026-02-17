package com.company.lms.video;

import com.company.lms.video.repository.LessonRecordingRepository;
import com.company.lms.video.repository.VideoAssetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VideoIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static MockWebServer mockWebServer;

    @BeforeAll
    static void setup() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void cleanup() throws Exception {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("zoom.client-id", () -> "cid");
        registry.add("zoom.client-secret", () -> "csecret");
        registry.add("zoom.redirect-uri", () -> "http://localhost:8080/api/zoom/oauth/callback");
        registry.add("zoom.webhook-secret", () -> "whsec");
        registry.add("zoom.api-base-url", () -> mockWebServer.url("/v2").toString().replaceAll("/$", ""));
        registry.add("minio.url", () -> "http://localhost:9000");
        registry.add("minio.access-key", () -> "minio");
        registry.add("minio.secret-key", () -> "minio123");
        registry.add("minio.bucket", () -> "lms");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LessonRecordingRepository lessonRecordingRepository;
    @Autowired VideoAssetRepository videoAssetRepository;

    @Test
    void recordingIngestAndStudentAccessAndIdempotency() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher V", "teacher.v@local", "+998901100001", "Teacher123!", "TEACHER");
        createUser(admin, "Student V1", "student.v1@local", "+998901100002", "Student123!", "STUDENT");
        createUser(admin, "Student V2", "student.v2@local", "+998901100003", "Student123!", "STUDENT");

        String teacher = token("teacher.v@local", "Teacher123!");
        String studentEnrolled = token("student.v1@local", "Student123!");
        String studentNotEnrolled = token("student.v2@local", "Student123!");

        String state = connectState(teacher);
        mockWebServer.enqueue(new MockResponse().setBody("{\"access_token\":\"at1\",\"refresh_token\":\"rt1\",\"expires_in\":3600}").addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody("{\"email\":\"teacher.v@zoom.test\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(get("/api/zoom/oauth/callback").param("code", "abc").param("state", state).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());

        String courseId = createActiveCourse(teacher, "Video course");
        String moduleId = createModule(teacher, courseId, "module");
        String lessonId = createLesson(teacher, moduleId, "live", "LIVE_ZOOM");

        String studentId = userIdByEmail(admin, "student.v1@local");
        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + studentId + "\"]}"))
                .andExpect(status().isOk());

        mockWebServer.enqueue(new MockResponse().setBody("{\"id\":\"555888\",\"join_url\":\"https://zoom.test/j/555888\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(post("/api/lessons/{lessonId}/zoom-meeting", lessonId)
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + OffsetDateTime.now().plusDays(1) + "\",\"durationMinutes\":60,\"topic\":\"topic\"}"))
                .andExpect(status().isOk());

        String downloadUrl = mockWebServer.url("/recording.mp4").toString();
        mockWebServer.enqueue(new MockResponse().setBody("fake-mp4-content").addHeader("Content-Type", "video/mp4"));
        String payload = "{\"event\":\"recording.completed\",\"payload\":{\"object\":{\"id\":\"555888\",\"uuid\":\"rec-1\",\"recording_files\":[{\"file_type\":\"MP4\",\"download_url\":\"" + downloadUrl + "\",\"recording_type\":\"shared_screen_with_speaker_view\"}]}}}";

        String ts = "1700000000";
        mockMvc.perform(post("/api/zoom/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("x-zm-request-timestamp", ts)
                        .header("x-zm-signature", sign("whsec", ts, payload)))
                .andExpect(status().isOk());

        waitForReady(UUID.fromString(lessonId));
        assertThat(videoAssetRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/student/lessons/{lessonId}/video", lessonId)
                        .header("Authorization", "Bearer " + studentEnrolled))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/lessons/{lessonId}/video", lessonId)
                        .header("Authorization", "Bearer " + studentNotEnrolled))
                .andExpect(status().isForbidden());

        mockWebServer.enqueue(new MockResponse().setBody("fake-mp4-content-2").addHeader("Content-Type", "video/mp4"));
        mockMvc.perform(post("/api/zoom/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("x-zm-request-timestamp", ts)
                        .header("x-zm-signature", sign("whsec", ts, payload)))
                .andExpect(status().isOk());
        Thread.sleep(600);

        assertThat(videoAssetRepository.count()).isEqualTo(1);
    }

    @Test
    void failureAndRetryAdminOnly() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher F", "teacher.f@local", "+998901100011", "Teacher123!", "TEACHER");
        String teacher = token("teacher.f@local", "Teacher123!");

        String state = connectState(teacher);
        mockWebServer.enqueue(new MockResponse().setBody("{\"access_token\":\"at2\",\"refresh_token\":\"rt2\",\"expires_in\":3600}").addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody("{\"email\":\"teacher.f@zoom.test\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(get("/api/zoom/oauth/callback").param("code", "abc").param("state", state).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());

        String courseId = createActiveCourse(teacher, "Failure course");
        String moduleId = createModule(teacher, courseId, "module");
        String lessonId = createLesson(teacher, moduleId, "live", "LIVE_ZOOM");

        mockWebServer.enqueue(new MockResponse().setBody("{\"id\":\"999333\",\"join_url\":\"https://zoom.test/j/999333\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(post("/api/lessons/{lessonId}/zoom-meeting", lessonId)
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + OffsetDateTime.now().plusDays(1) + "\",\"durationMinutes\":60,\"topic\":\"topic\"}"))
                .andExpect(status().isOk());

        String payload = "{\"event\":\"recording.completed\",\"payload\":{\"object\":{\"id\":\"999333\",\"uuid\":\"rec-2\",\"recording_files\":[{\"file_type\":\"MP4\",\"download_url\":\"" + mockWebServer.url("/missing.mp4") + "\"}]}}}";
        String ts = "1700000001";
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockMvc.perform(post("/api/zoom/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("x-zm-request-timestamp", ts)
                        .header("x-zm-signature", sign("whsec", ts, payload)))
                .andExpect(status().isOk());

        waitForFailed(UUID.fromString(lessonId));

        mockMvc.perform(post("/api/admin/lessons/{lessonId}/recording/retry", lessonId)
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/lessons/{lessonId}/recording/retry", lessonId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private void waitForReady(UUID lessonId) throws Exception {
        for (int i = 0; i < 20; i++) {
            var rec = lessonRecordingRepository.findByLessonId(lessonId);
            if (rec.isPresent() && "READY".equals(rec.get().getStatus())) {
                return;
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("Recording did not become READY in time");
    }

    private void waitForFailed(UUID lessonId) throws Exception {
        for (int i = 0; i < 20; i++) {
            var rec = lessonRecordingRepository.findByLessonId(lessonId);
            if (rec.isPresent() && "FAILED".equals(rec.get().getStatus())) {
                assertThat(rec.get().getErrorMessage()).isNotBlank();
                return;
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("Recording did not become FAILED in time");
    }

    private String connectState(String token) throws Exception {
        String body = mockMvc.perform(get("/api/zoom/connect-url").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(body).get("data").get("url").asText();
        return url.substring(url.indexOf("state=") + 6);
    }

    private String sign(String secret, String timestamp, String payload) throws Exception {
        String message = "v0:" + timestamp + ":" + payload;
        Mac sha = Mac.getInstance("HmacSHA256");
        sha.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v0=" + HexFormat.of().formatHex(sha.doFinal(message.getBytes(StandardCharsets.UTF_8)));
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
