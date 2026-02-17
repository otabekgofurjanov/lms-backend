package com.company.lms.enrollment;

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
class EnrollmentIntegrationTest {
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
    void adminBulkEnrollmentCreateAndStatusChangeWorks() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Student A", "st.a@local", "+998941111111", "Student123!", "STUDENT");
        createUser(admin, "Student B", "st.b@local", "+998942222222", "Student123!", "STUDENT");

        String courseId = createActiveCourse(admin, "Enrollment Course");
        String stAId = userIdByEmail(admin, "st.a@local");
        String stBId = userIdByEmail(admin, "st.b@local");

        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + stAId + "\",\"" + stBId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2));

        String enrollmentId = mockMvc.perform(get("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstEnrollmentId = objectMapper.readTree(enrollmentId).get("data").get("items").get(0).get("enrollmentId").asText();

        mockMvc.perform(patch("/api/admin/enrollments/{id}/status", firstEnrollmentId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentStatus").value("PAUSED"));
    }

    @Test
    void studentOnlySeesOwnCoursesAndAccessControlWorks() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Student X", "st.x@local", "+998943333333", "Student123!", "STUDENT");
        createUser(admin, "Student Y", "st.y@local", "+998944444444", "Student123!", "STUDENT");

        String courseId = createActiveCourse(admin, "Secure Course");
        String studentXId = userIdByEmail(admin, "st.x@local");

        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + studentXId + "\"]}"))
                .andExpect(status().isOk());

        String sx = token("st.x@local", "Student123!");
        String sy = token("st.y@local", "Student123!");

        mockMvc.perform(get("/api/student/courses").header("Authorization", "Bearer " + sx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseId").value(courseId));

        mockMvc.perform(get("/api/student/courses/{id}", courseId).header("Authorization", "Bearer " + sx))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/courses/{id}", courseId).header("Authorization", "Bearer " + sy))
                .andExpect(status().isForbidden());

        String eId = enrollmentIdForCourse(admin, courseId);
        mockMvc.perform(patch("/api/admin/enrollments/{id}/status", eId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/courses/{id}", courseId).header("Authorization", "Bearer " + sx))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherOnlyOwnCourseStudentsAndNonAdminForbiddenOnAdminEnrollmentEndpoints() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher A", "t.a@local", "+998945555555", "Teacher123!", "TEACHER");
        createUser(admin, "Teacher B", "t.b@local", "+998946666666", "Teacher123!", "TEACHER");
        createUser(admin, "Student Z", "st.z@local", "+998947777777", "Student123!", "STUDENT");

        String ta = token("t.a@local", "Teacher123!");
        String tb = token("t.b@local", "Teacher123!");
        String student = token("st.z@local", "Student123!");

        String courseA = createActiveCourse(ta, "Teacher A Course");
        String courseB = createActiveCourse(tb, "Teacher B Course");
        String stzId = userIdByEmail(admin, "st.z@local");

        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseA)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + stzId + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teacher/courses/{courseId}/students", courseA)
                        .header("Authorization", "Bearer " + ta))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teacher/courses/{courseId}/students", courseA)
                        .header("Authorization", "Bearer " + tb))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/courses/{courseId}/enrollments", courseB)
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[\"" + stzId + "\"]}"))
                .andExpect(status().isForbidden());
    }

    private String enrollmentIdForCourse(String adminToken, String courseId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/courses/{courseId}/enrollments", courseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("items").get(0).get("enrollmentId").asText();
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

    private String userIdByEmail(String adminToken, String email) throws Exception {
        String body = mockMvc.perform(get("/api/admin/users")
                        .param("search", email)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("items").get(0).get("id").asText();
    }

    private String token(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }
}
