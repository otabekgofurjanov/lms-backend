# lms-backend

## Local run
1. `docker compose up -d`
2. `mvn spring-boot:run -Dspring-boot.run.profiles=local`

## Swagger
- http://localhost:8080/swagger-ui.html

## Default admin
- Email: `admin@local`
- Password: `Admin123!`

## Phase-2 implemented
- Admin user CRUD: create/update/status/list/detail
- Role and permission listing endpoints
- CSV user import endpoint (`fullName,email,phone,role`)
- Audit logs for USER_CREATE / USER_UPDATE / USER_STATUS_CHANGE / USER_IMPORT
- Blocked users cannot login

## Tech stack
- Java 21
- Spring Boot 3
- PostgreSQL
- Redis
- Liquibase
- OpenAPI/Swagger

## Phase-3 implemented
- Course → Module → Lesson (Udemy-style) CRUD
- Reorder endpoints for modules and lessons
- Ownership checks: teacher can manage only own courses, admin manages all
- Public authenticated catalog endpoints for ACTIVE courses


## Phase-4 implemented
- Enrollment management (admin bulk enroll, status update, soft remove)
- Teacher read-only enrolled students view with ownership checks
- Student my-courses and enrolled-course detail access control
- Public course detail now returns catalog-level basic info only


## Phase-5 implemented
- Zoom OAuth connect/disconnect with token storage
- Lesson LIVE_ZOOM meeting scheduling and student join-link endpoint
- Public webhook endpoint with signature verification and async participant event processing


## Phase-6 implemented
- Zoom participant identity matching (EMAIL/NAME/MANUAL) with enrollment constraints
- Deterministic attendance status calculation (PRESENT/LATE/ABSENT) and admin recalculation endpoints
- Course-level attendance aggregation into `course_progress.attendance_pct` with teacher/student attendance reports

## Phase-7 implemented
- `recording.completed` webhook events are delegated from zoom module to async video ingest pipeline
- Zoom recording MP4 download, MinIO upload, SHA-256 checksum persistence, and recording metadata tables
- Secure video access endpoints for student/teacher/admin with enrollment and ownership checks
- Admin recording retry endpoint for FAILED lesson recordings

## Phase-8 implemented
- Student video session/progress tracking endpoints with ACTIVE enrollment checks
- Redis buffered progress aggregation with scheduled flush to PostgreSQL (`video_progress` upsert)
- Suspicious behavior counters (`tabSwitchCount`, `seekAttempts`) with audit hooks
- Teacher/Admin course-level video progress reporting endpoints

## Phase-9 implemented
- Exam module added: question bank, quiz CRUD, attach/reorder question APIs for ADMIN/TEACHER
- Student quiz flow: list/start/submit/history with enrollment + unlock policy via video completion
- Attempt limits, scoring, pass/fail evaluation, and student-safe responses (no correctIndex leakage)
- Teacher/Admin quiz result report endpoints by course with best score and attempts count
