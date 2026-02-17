package com.company.lms.zoom;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ZoomIntegrationTest {
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
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void connectUrlTeacherAllowedStudentForbidden() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher Z", "teacher.z@local", "+998951111111", "Teacher123!", "TEACHER");
        createUser(admin, "Student Z", "student.z@local", "+998952222222", "Student123!", "STUDENT");

        String teacher = token("teacher.z@local", "Teacher123!");
        String student = token("student.z@local", "Student123!");

        mockMvc.perform(get("/api/zoom/connect-url").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/zoom/connect-url").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
    }

    @Test
    void meetingCreateLiveZoomSuccessRecordedFailsAndOwnershipChecked() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher M", "teacher.m@local", "+998953333333", "Teacher123!", "TEACHER");
        createUser(admin, "Teacher N", "teacher.n@local", "+998954444444", "Teacher123!", "TEACHER");

        String t1 = token("teacher.m@local", "Teacher123!");
        String t2 = token("teacher.n@local", "Teacher123!");

        String t1State = connectState(t1);
        mockWebServer.enqueue(new MockResponse().setBody("{\"access_token\":\"at1\",\"refresh_token\":\"rt1\",\"expires_in\":3600}").addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody("{\"email\":\"teacher.m@zoom.test\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(get("/api/zoom/oauth/callback").param("code", "abc").param("state", t1State).header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());

        String course = createActiveCourse(t1, "Zoom Course");
        String module = createModule(t1, course, "M1");
        String liveLesson = createLesson(t1, module, "Live", "LIVE_ZOOM");
        String recordedLesson = createLesson(t1, module, "Rec", "RECORDED");

        mockWebServer.enqueue(new MockResponse().setBody("{\"id\":\"987654321\",\"join_url\":\"https://zoom.test/j/987654321\"}").addHeader("Content-Type", "application/json"));

        mockMvc.perform(post("/api/lessons/{lessonId}/zoom-meeting", liveLesson)
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + OffsetDateTime.now().plusDays(1) + "\",\"durationMinutes\":60,\"topic\":\"topic\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lessons/{lessonId}/zoom-meeting", recordedLesson)
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + OffsetDateTime.now().plusDays(1) + "\",\"durationMinutes\":60,\"topic\":\"topic\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/lessons/{lessonId}/zoom-meeting", liveLesson)
                        .header("Authorization", "Bearer " + t2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + OffsetDateTime.now().plusDays(1) + "\",\"durationMinutes\":60,\"topic\":\"topic\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentJoinLinkEnrollmentGuardAndWebhookSignatureValidation() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher W", "teacher.w@local", "+998955555555", "Teacher123!", "TEACHER");
        createUser(admin, "Student W", "student.w@local", "+998956666666", "Student123!", "STUDENT");
        createUser(admin, "Student Q", "student.q@local", "+998957777777", "Student123!", "STUDENT");

        String teacher = token("teacher.w@local", "Teacher123!");
        String studentEnrolled = token("student.w@local", "Student123!");
        String studentNotEnrolled = token("student.q@local", "Student123!");

        String state = connectState(teacher);
        mockWebServer.enqueue(new MockResponse().setBody("{\"access_token\":\"at2\",\"refresh_token\":\"rt2\",\"expires_in\":3600}").addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody("{\"email\":\"teacher.w@zoom.test\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(get("/api/zoom/oauth/callback").param("code", "abc").param("state", state).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());

        String courseId = createActiveCourse(teacher, "Secure Zoom Course");
        String moduleId = createModule(teacher, courseId, "M");
        String liveLesson = createLesson(teacher, moduleId, "Live", "LIVE_ZOOM");

        mockWebServer.enqueue(new MockResponse().setBody("{\"id\":\"11223344\",\"join_url\":\"https://zoom.test/j/11223344\"}").addHeader("Content-Type", "application/json"));
        mockMvc.perform(post("/api/lessons/{lessonId}/zoom-meeting", liveLesson)
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + OffsetDateTime.now().plusDays(1) + "\",\"durationMinutes\":45,\"topic\":\"topic\"}"))
                .andExpect(status().isOk());

        String stId = userIdByEmail(admin, "student.w@local");
        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + stId + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/lessons/{lessonId}/join-link", liveLesson)
                        .header("Authorization", "Bearer " + studentEnrolled))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/lessons/{lessonId}/join-link", liveLesson)
                        .header("Authorization", "Bearer " + studentNotEnrolled))
                .andExpect(status().isForbidden());

        String payload = "{\"event\":\"meeting.participant_joined\",\"payload\":{\"object\":{\"id\":\"11223344\",\"participant\":{\"user_id\":\"u1\",\"user_name\":\"Stud\",\"join_time\":\"2026-01-01T10:00:00Z\"}}}}";

        mockMvc.perform(post("/api/zoom/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("x-zm-request-timestamp", "1700000000")
                        .header("x-zm-signature", "v0=bad"))
                .andExpect(status().isForbidden());

        String sig = sign("whsec", "1700000000", payload);
        mockMvc.perform(post("/api/zoom/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("x-zm-request-timestamp", "1700000000")
                        .header("x-zm-signature", sig))
                .andExpect(status().isOk());
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
