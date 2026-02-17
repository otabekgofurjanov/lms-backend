package com.company.lms.zoom;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AttendanceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static MockWebServer server;

    @BeforeAll static void b() throws Exception { server = new MockWebServer(); server.start(); }
    @AfterAll static void a() throws Exception { server.shutdown(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("zoom.client-id", () -> "cid");
        r.add("zoom.client-secret", () -> "sec");
        r.add("zoom.redirect-uri", () -> "http://localhost:8080/api/zoom/oauth/callback");
        r.add("zoom.webhook-secret", () -> "whsec");
        r.add("zoom.api-base-url", () -> server.url("/v2").toString().replaceAll("/$", ""));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void identityMatchingAndAttendanceAndAccessControl() throws Exception {
        String admin = token("admin@local", "Admin123!");
        createUser(admin, "Teacher A", "teacher.a6@local", "+998961111111", "Teacher123!", "TEACHER");
        createUser(admin, "Student One", "student.one6@local", "+998962222222", "Student123!", "STUDENT");
        createUser(admin, "Student Two", "student.two6@local", "+998963333333", "Student123!", "STUDENT");
        createUser(admin, "Another Teacher", "teacher.b6@local", "+998964444444", "Teacher123!", "TEACHER");

        String teacher = token("teacher.a6@local", "Teacher123!");
        String otherTeacher = token("teacher.b6@local", "Teacher123!");
        String s1 = token("student.one6@local", "Student123!");
        String s2 = token("student.two6@local", "Student123!");

        String state = connectState(teacher);
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3600}").addHeader("Content-Type","application/json"));
        server.enqueue(new MockResponse().setBody("{\"email\":\"teacher.a6@zoom.test\"}").addHeader("Content-Type","application/json"));
        mvc.perform(get("/api/zoom/oauth/callback").param("code","c").param("state",state).header("Authorization","Bearer "+teacher)).andExpect(status().isOk());

        String course = createActiveCourse(teacher, "Attendance C");
        String module = createModule(teacher, course, "M");
        String l1 = createLesson(teacher, module, "L1", "LIVE_ZOOM");
        String l2 = createLesson(teacher, module, "L2", "LIVE_ZOOM");

        server.enqueue(new MockResponse().setBody("{\"id\":\"m111\",\"join_url\":\"https://z/j1\"}").addHeader("Content-Type","application/json"));
        server.enqueue(new MockResponse().setBody("{\"id\":\"m222\",\"join_url\":\"https://z/j2\"}").addHeader("Content-Type","application/json"));
        String m1id = createMeeting(teacher, l1, 100);
        String m2id = createMeeting(teacher, l2, 100);

        String st1Id = userIdByEmail(admin, "student.one6@local");
        String st2Id = userIdByEmail(admin, "student.two6@local");
        mvc.perform(post("/api/admin/courses/{id}/enrollments", course).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentIds\":[\""+st1Id+"\",\""+st2Id+"\"]}")).andExpect(status().isOk());

        // meeting1 email match 80%
        sendWebhook("m111", "meeting.participant_joined", "student.one6@local", "Student One", "2026-01-01T10:00:00Z", null);
        sendWebhook("m111", "meeting.participant_left", "student.one6@local", "Student One", null, "2026-01-01T11:20:00Z");
        // name match 50%
        sendWebhook("m111", "meeting.participant_joined", null, "Student Two", "2026-01-01T10:00:00Z", null);
        sendWebhook("m111", "meeting.participant_left", null, "Student Two", null, "2026-01-01T10:50:00Z");
        sendEnded("m111");

        // meeting2 only student1 10%
        sendWebhook("m222", "meeting.participant_joined", "student.one6@local", "Student One", "2026-01-02T10:00:00Z", null);
        sendWebhook("m222", "meeting.participant_left", "student.one6@local", "Student One", null, "2026-01-02T10:10:00Z");
        sendEnded("m222");

        mvc.perform(post("/api/admin/zoom/meetings/{id}/recalculate-attendance", m1id).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(2));

        mvc.perform(post("/api/admin/zoom/meetings/{id}/recalculate-attendance", m2id).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/courses/{id}/recalculate-attendance", course).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk());

        mvc.perform(get("/api/teacher/courses/{id}/attendance", course).header("Authorization","Bearer "+teacher))
                .andExpect(status().isOk());
        mvc.perform(get("/api/teacher/courses/{id}/attendance", course).header("Authorization","Bearer "+otherTeacher))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/student/courses/{id}/attendance", course).header("Authorization","Bearer "+s1))
                .andExpect(status().isOk());
        mvc.perform(get("/api/student/courses/{id}/attendance", course).header("Authorization","Bearer "+s2))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/zoom/meetings/{id}/recalculate-attendance", m1id).header("Authorization","Bearer "+teacher))
                .andExpect(status().isForbidden());
    }

    private void sendWebhook(String meetingId, String event, String email, String name, String join, String leave) throws Exception {
        String payload = "{\"event\":\""+event+"\",\"payload\":{\"object\":{\"id\":\""+meetingId+"\",\"participant\":{\"email\":"+(email==null?"null":"\""+email+"\"")+",\"user_name\":\""+name+"\""+
                (join==null?"":",\"join_time\":\""+join+"\"") + (leave==null?"":",\"leave_time\":\""+leave+"\"") + "}}}}";
        String ts = "1700000000";
        mvc.perform(post("/api/zoom/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("x-zm-request-timestamp", ts).header("x-zm-signature", sign("whsec", ts, payload)))
                .andExpect(status().isOk());
    }

    private void sendEnded(String meetingId) throws Exception {
        String payload = "{\"event\":\"meeting.ended\",\"payload\":{\"object\":{\"id\":\""+meetingId+"\"}}}";
        String ts = "1700000000";
        mvc.perform(post("/api/zoom/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("x-zm-request-timestamp", ts).header("x-zm-signature", sign("whsec", ts, payload)))
                .andExpect(status().isOk());
    }

    private String createMeeting(String token, String lessonId, int minutes) throws Exception {
        String b = mvc.perform(post("/api/lessons/{id}/zoom-meeting", lessonId).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\""+OffsetDateTime.now().plusDays(1)+"\",\"durationMinutes\":"+minutes+",\"topic\":\"t\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(b).get("data").get("id").asText();
    }

    private String sign(String secret, String ts, String payload) throws Exception {
        Mac sha = Mac.getInstance("HmacSHA256"); sha.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v0="+ HexFormat.of().formatHex(sha.doFinal(("v0:"+ts+":"+payload).getBytes(StandardCharsets.UTF_8)));
    }

    private String connectState(String token) throws Exception {
        String body = mvc.perform(get("/api/zoom/connect-url").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = om.readTree(body).get("data").get("url").asText();
        return url.substring(url.indexOf("state=")+6);
    }

    private String token(String email, String password) throws Exception {
        String b = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(b).get("data").get("accessToken").asText();
    }

    private void createUser(String admin, String n, String e, String p, String pass, String role) throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\""+n+"\",\"email\":\""+e+"\",\"phone\":\""+p+"\",\"password\":\""+pass+"\",\"roles\":[\""+role+"\"]}"))
                .andExpect(status().isOk());
    }

    private String createActiveCourse(String token, String title) throws Exception {
        String b = mvc.perform(post("/api/courses").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\""+title+"\",\"description\":\"d\",\"coverUrl\":null}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = om.readTree(b).get("data").get("id").asText();
        mvc.perform(patch("/api/courses/{id}/status", id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String createModule(String token, String courseId, String t) throws Exception {
        String b = mvc.perform(post("/api/courses/{id}/modules", courseId).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\""+t+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(b).get("data").get("id").asText();
    }

    private String createLesson(String token, String moduleId, String t, String type) throws Exception {
        String b = mvc.perform(post("/api/modules/{id}/lessons", moduleId).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\""+t+"\",\"lessonType\":\""+type+"\",\"availableAt\":null}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(b).get("data").get("id").asText();
    }

    private String userIdByEmail(String adminToken, String email) throws Exception {
        String b = mvc.perform(get("/api/admin/users").param("search", email).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(b).get("data").get("items").get(0).get("id").asText();
    }
}
