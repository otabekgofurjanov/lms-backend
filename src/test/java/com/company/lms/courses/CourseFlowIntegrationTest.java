package com.company.lms.courses;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CourseFlowIntegrationTest {
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

    @Test
    void adminCourseCreateUpdateStatusWorks() throws Exception {
        String adminToken = loginAndGet("admin@local", "Admin123!", "accessToken");

        String created = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"title\":\"Java Core\"," +
                                "\"description\":\"desc\"," +
                                "\"coverUrl\":\"http://cover\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(created).get("data").get("id").asText();

        mockMvc.perform(put("/api/courses/{id}", courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"title\":\"Java Core Updated\"," +
                                "\"description\":\"desc2\"," +
                                "\"coverUrl\":\"http://cover2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Java Core Updated"));

        mockMvc.perform(patch("/api/courses/{id}/status", courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void teacherOwnershipIsEnforced() throws Exception {
        String adminToken = loginAndGet("admin@local", "Admin123!", "accessToken");
        createUser(adminToken, "Teacher One", "teacher.one@local", "+998911111111", "Teacher123!", "TEACHER");
        createUser(adminToken, "Teacher Two", "teacher.two@local", "+998922222222", "Teacher123!", "TEACHER");

        String t1 = loginAndGet("teacher.one@local", "Teacher123!", "accessToken");
        String t2 = loginAndGet("teacher.two@local", "Teacher123!", "accessToken");

        String created = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T1 Course\",\"description\":\"d\",\"coverUrl\":null}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(created).get("data").get("id").asText();

        mockMvc.perform(put("/api/courses/{id}", courseId)
                        .header("Authorization", "Bearer " + t2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\",\"description\":\"d\",\"coverUrl\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void moduleAndLessonReorderWorks() throws Exception {
        String adminToken = loginAndGet("admin@local", "Admin123!", "accessToken");
        String courseId = createCourse(adminToken, "Course for reorder");

        String m1 = createModule(adminToken, courseId, "M1");
        String m2 = createModule(adminToken, courseId, "M2");

        mockMvc.perform(post("/api/courses/{courseId}/modules/reorder", courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleIdsInOrder\":[\"" + m2 + "\",\"" + m1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(m2));

        String l1 = createLesson(adminToken, m2, "L1");
        String l2 = createLesson(adminToken, m2, "L2");

        mockMvc.perform(post("/api/modules/{moduleId}/lessons/reorder", m2)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonIdsInOrder\":[\"" + l2 + "\",\"" + l1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(l2));
    }

    @Test
    void studentCannotCreateCourseAndPublicShowsOnlyActive() throws Exception {
        String adminToken = loginAndGet("admin@local", "Admin123!", "accessToken");
        createUser(adminToken, "Student One", "student.phase3@local", "+998933333333", "Student123!", "STUDENT");
        String studentToken = loginAndGet("student.phase3@local", "Student123!", "accessToken");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\",\"description\":\"x\",\"coverUrl\":null}"))
                .andExpect(status().isForbidden());

        String draftCourseId = createCourse(adminToken, "Draft Course");
        String activeCourseId = createCourse(adminToken, "Active Course");
        mockMvc.perform(patch("/api/courses/{id}/status", activeCourseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/courses").param("search", "Active Course")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("Active Course"));

        mockMvc.perform(get("/api/public/courses/{id}", draftCourseId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    private void createUser(String adminToken, String name, String email, String phone, String password, String role) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"fullName\":\"" + name + "\"," +
                                "\"email\":\"" + email + "\"," +
                                "\"phone\":\"" + phone + "\"," +
                                "\"password\":\"" + password + "\"," +
                                "\"roles\":[\"" + role + "\"]}"))
                .andExpect(status().isOk());
    }

    private String createCourse(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"desc\",\"coverUrl\":null}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asText();
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

    private String createLesson(String token, String moduleId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/modules/{moduleId}/lessons", moduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"lessonType\":\"RECORDED\",\"availableAt\":null}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("data").get("id").asText();
    }

    private String loginAndGet(String email, String password, String field) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get(field).asText();
    }
}
