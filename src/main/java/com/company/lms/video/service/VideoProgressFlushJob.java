package com.company.lms.video.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.video.mapper.VideoProgressMapper;
import com.company.lms.video.repository.VideoProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
@RequiredArgsConstructor
public class VideoProgressFlushJob {
    private final VideoProgressRedisService redisService;
    private final VideoProgressRepository videoProgressRepository;
    private final VideoProgressMapper mapper;
    private final AuditService auditService;

    @Scheduled(fixedDelayString = "#{${lms.video.flush-interval-seconds:60} * 1000}")
    @Transactional
    public void flush() {
        Set<String> dirty = redisService.dirtyKeys();
        for (String member : dirty) {
            try {
                var state = redisService.readByDirtyMember(member);
                var completion = mapper.calcCompletion(state.watchedSeconds(), state.totalSeconds());
                String suspicious = mapper.suspiciousJson(state);
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                videoProgressRepository.upsertProgress(
                        UUID.randomUUID(),
                        state.studentId(),
                        state.lessonId(),
                        state.watchedSeconds(),
                        state.totalSeconds(),
                        completion,
                        state.lastEventAt() == null ? now : state.lastEventAt(),
                        suspicious,
                        now,
                        now
                );
                auditService.log(state.studentId(), "VIDEO_PROGRESS_UPDATED", "LESSON", state.lessonId().toString(), null,
                        "{\"completionPct\":\"" + completion + "\"}", null, null);
                redisService.clearDirty(member);
            } catch (Exception ignored) {
            }
        }
    }
}
