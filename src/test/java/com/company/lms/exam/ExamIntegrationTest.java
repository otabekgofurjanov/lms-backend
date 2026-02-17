package com.company.lms.exam;

import com.company.lms.video.repository.VideoProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExamIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired VideoProgressRepository videoProgressRepository;

    @Test
    void fullExamFlowWithUnlockAndLimits() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher A", "teacher.a@local", "+998901111111", "Teacher123!", "TEACHER");
        createUser(admin, "Teacher B", "teacher.b@local", "+998902222222", "Teacher123!", "TEACHER");
        createUser(admin, "Student A", "student.a@local", "+998903333333", "Student123!", "STUDENT");
        createUser(admin, "Student B", "student.b@local", "+998904444444", "Student123!", "STUDENT");

        String t1 = token("teacher.a@local", "Teacher123!");
        String t2 = token("teacher.b@local", "Teacher123!");
        String s1 = token("student.a@local", "Student123!");
        String s2 = token("student.b@local", "Student123!");

        String courseId = createActiveCourse(t1, "Exam course");
        String moduleId = createModule(t1, courseId, "m1");
        String lessonId = createLesson(t1, moduleId, "l1", "RECORDED");

        String q1 = createQuestion(t1, "2+2?", new String[]{"3", "4", "5"}, 1);
        String q2 = createQuestion(t1, "Sky color?", new String[]{"Blue", "Green"}, 0);

        String quizId = createQuiz(t1, courseId, lessonId, 1);
        attachQuestions(t1, quizId, q1, q2);

        mockMvc.perform(put("/api/exam/quizzes/{id}", quizId)
                        .header("Authorization", "Bearer " + t2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + courseId + "\",\"lessonId\":\"" + lessonId + "\",\"title\":\"x\",\"timeLimitSec\":600,\"maxAttempts\":1,\"passScorePct\":70,\"isActive\":true}"))
                .andExpect(status().isForbidden());

        String studentId = userIdByEmail(admin, "student.a@local");
        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + studentId + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/courses/{courseId}/quizzes", courseId)
                        .header("Authorization", "Bearer " + s2))
                .andExpect(status().isForbidden());

        String listBefore = mockMvc.perform(get("/api/student/courses/{courseId}/quizzes", courseId)
                        .header("Authorization", "Bearer " + s1))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        if (objectMapper.readTree(listBefore).toString().contains("\"canStart\":true")) {
            throw new AssertionError("canStart should be false before lesson completion");
        }

        mockMvc.perform(post("/api/student/quizzes/{quizId}/start", quizId)
                        .header("Authorization", "Bearer " + s1))
                .andExpect(status().isForbidden());

        videoProgressRepository.upsertProgress(UUID.randomUUID(), UUID.fromString(studentId), UUID.fromString(lessonId), 95, 100,
                BigDecimal.valueOf(95), OffsetDateTime.now(), "{\"tabSwitchCount\":0,\"seekAttempts\":0}", OffsetDateTime.now(), OffsetDateTime.now());

        String listAfter = mockMvc.perform(get("/api/student/courses/{courseId}/quizzes", courseId)
                        .header("Authorization", "Bearer " + s1))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        if (!objectMapper.readTree(listAfter).toString().contains("\"canStart\":true")) {
            throw new AssertionError("canStart should be true after lesson completion");
        }

        String start = mockMvc.perform(post("/api/student/quizzes/{quizId}/start", quizId)
                        .header("Authorization", "Bearer " + s1))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("correctIndex"))))
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(start).get("data").get("attemptId").asText();

        mockMvc.perform(post("/api/student/attempts/{attemptId}/submit", attemptId)
                        .header("Authorization", "Bearer " + s1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"" + q1 + "\",\"selectedIndex\":1},{\"questionId\":\"" + q2 + "\",\"selectedIndex\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.totalQuestions").value(2))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("correctIndex"))));

        mockMvc.perform(post("/api/student/quizzes/{quizId}/start", quizId)
                        .header("Authorization", "Bearer " + s1))
                .andExpect(status().isBadRequest());
    }

    private String createQuestion(String token, String text, String[] options, int correctIndex) throws Exception {
        StringBuilder opts = new StringBuilder("[");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) opts.append(',');
            opts.append('"').append(options[i]).append('"');
        }
        opts.append(']');
        String body = mockMvc.perform(post("/api/exam/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\",\"options\":" + opts + ",\"correctIndex\":" + correctIndex + ",\"explanation\":\"e\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asText();
    }

    private String createQuiz(String token, String courseId, String lessonId, int maxAttempts) throws Exception {
        String body = mockMvc.perform(post("/api/exam/quizzes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + courseId + "\",\"lessonId\":\"" + lessonId + "\",\"title\":\"Quiz1\",\"timeLimitSec\":600,\"maxAttempts\":" + maxAttempts + ",\"passScorePct\":70,\"isActive\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asText();
    }

    private void attachQuestions(String token, String quizId, String... qids) throws Exception {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < qids.length; i++) {
            if (i > 0) arr.append(',');
            arr.append('"').append(qids[i]).append('"');
        }
        arr.append(']');
        mockMvc.perform(post("/api/exam/quizzes/{quizId}/questions", quizId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":" + arr + "}"))
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
