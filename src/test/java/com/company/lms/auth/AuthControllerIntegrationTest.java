package com.company.lms.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class AuthControllerIntegrationTest {
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
    void adminLoginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void adminCreateAndUpdateUser() throws Exception {
        String adminToken = adminAccessToken();

        String createBody = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Teacher One",
                                  "email":"teacher1@local",
                                  "phone":"+998901111111",
                                  "password":"Teacher123!",
                                  "roles":["TEACHER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String userId = objectMapper.readTree(createBody).get("data").get("id").asText();

        mockMvc.perform(put("/api/admin/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Teacher One Updated",
                                  "phone":"+998902222222",
                                  "roles":["TEACHER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Teacher One Updated"));
    }

    @Test
    void blockUserLoginFails() throws Exception {
        String adminToken = adminAccessToken();

        String createBody = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Student One",
                                  "email":"student1@local",
                                  "phone":"+998903333333",
                                  "password":"Student123!",
                                  "roles":["STUDENT"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String userId = objectMapper.readTree(createBody).get("data").get("id").asText();

        mockMvc.perform(patch("/api/admin/users/{id}/status", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student1@local\",\"password\":\"Student123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importCsvWorks() throws Exception {
        String adminToken = adminAccessToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                ("fullName,email,phone,role\n" +
                 "Import Teacher,import.teacher@local,+998904444444,TEACHER\n" +
                 "Import Student,import.student@local,+998905555555,STUDENT\n").getBytes()
        );

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.created").value(2));
    }

    @Test
    void nonAdminCannotCreateUser() throws Exception {
        String adminToken = adminAccessToken();

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Limited Teacher",
                                  "email":"limited.teacher@local",
                                  "phone":"+998906666666",
                                  "password":"Teacher123!",
                                  "roles":["TEACHER"]
                                }
                                """))
                .andExpect(status().isOk());

        String teacherToken = loginAndGet("limited.teacher@local", "Teacher123!", "accessToken");

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Another User",
                                  "email":"another.user@local",
                                  "phone":"+998907777777",
                                  "password":"User12345!",
                                  "roles":["STUDENT"]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String adminAccessToken() throws Exception {
        return loginAndGet("admin@local", "Admin123!", "accessToken");
    }

    private String loginAndGet(String email, String password, String field) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("data").get(field).asText();
    }
}
