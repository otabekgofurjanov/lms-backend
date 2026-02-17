package com.company.lms.video.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VideoProgressRedisService {
    private static final String DIRTY_KEY = "video:dirty";
    private final StringRedisTemplate redisTemplate;

    public UUID createSession(UUID studentId, UUID lessonId) {
        UUID sessionId = UUID.randomUUID();
        String key = sessionKey(sessionId);
        redisTemplate.opsForHash().put(key, "studentId", studentId.toString());
        redisTemplate.opsForHash().put(key, "lessonId", lessonId.toString());
        redisTemplate.opsForHash().put(key, "lastPingEpoch", "0");
        redisTemplate.expire(key, Duration.ofHours(6));
        return sessionId;
    }

    public Map<Object, Object> getSession(UUID sessionId) {
        return redisTemplate.opsForHash().entries(sessionKey(sessionId));
    }

    public long getAndUpdateLastPing(UUID sessionId, long newEpochSeconds) {
        String key = sessionKey(sessionId);
        Object existing = redisTemplate.opsForHash().get(key, "lastPingEpoch");
        long prev = existing == null ? 0L : Long.parseLong(existing.toString());
        redisTemplate.opsForHash().put(key, "lastPingEpoch", String.valueOf(newEpochSeconds));
        redisTemplate.expire(key, Duration.ofHours(6));
        return prev;
    }

    public ProgressState getProgress(UUID studentId, UUID lessonId) {
        String key = progressKey(studentId, lessonId);
        Map<Object, Object> h = redisTemplate.opsForHash().entries(key);
        if (h.isEmpty()) {
            return null;
        }
        return mapToState(studentId, lessonId, h);
    }

    public ProgressState applyDelta(UUID studentId, UUID lessonId, int deltaSeconds, int totalSeconds, OffsetDateTime eventTime,
                                    int tabSwitchDelta, int seekDelta) {
        String key = progressKey(studentId, lessonId);
        Map<Object, Object> current = redisTemplate.opsForHash().entries(key);

        int watched = Integer.parseInt(current.getOrDefault("watchedSeconds", "0").toString());
        int total = Integer.parseInt(current.getOrDefault("totalSeconds", "0").toString());
        int tabs = Integer.parseInt(current.getOrDefault("tabSwitchCount", "0").toString());
        int seeks = Integer.parseInt(current.getOrDefault("seekAttempts", "0").toString());

        watched += deltaSeconds;
        if (total == 0) {
            total = totalSeconds;
        }
        tabs += tabSwitchDelta;
        seeks += seekDelta;

        redisTemplate.opsForHash().put(key, "watchedSeconds", String.valueOf(watched));
        redisTemplate.opsForHash().put(key, "totalSeconds", String.valueOf(total));
        redisTemplate.opsForHash().put(key, "lastEventAt", eventTime.toString());
        redisTemplate.opsForHash().put(key, "tabSwitchCount", String.valueOf(tabs));
        redisTemplate.opsForHash().put(key, "seekAttempts", String.valueOf(seeks));
        if (seekDelta > 0) {
            redisTemplate.opsForHash().put(key, "lastSeekAt", eventTime.toString());
        }

        redisTemplate.opsForSet().add(DIRTY_KEY, studentId + ":" + lessonId);
        redisTemplate.expire(key, Duration.ofDays(2));

        return new ProgressState(studentId, lessonId, watched, total, eventTime, tabs, seeks, seekDelta > 0 ? eventTime : parse(current.get("lastSeekAt")));
    }

    public Set<String> dirtyKeys() {
        Set<String> members = redisTemplate.opsForSet().members(DIRTY_KEY);
        return members == null ? Set.of() : members;
    }

    public void clearDirty(String dirtyMember) {
        redisTemplate.opsForSet().remove(DIRTY_KEY, dirtyMember);
    }

    public ProgressState readByDirtyMember(String member) {
        String[] parts = member.split(":");
        UUID studentId = UUID.fromString(parts[0]);
        UUID lessonId = UUID.fromString(parts[1]);
        ProgressState state = getProgress(studentId, lessonId);
        if (state == null) {
            return new ProgressState(studentId, lessonId, 0, 0, OffsetDateTime.now(ZoneOffset.UTC), 0, 0, null);
        }
        return state;
    }

    private ProgressState mapToState(UUID studentId, UUID lessonId, Map<Object, Object> h) {
        return new ProgressState(
                studentId,
                lessonId,
                Integer.parseInt(h.getOrDefault("watchedSeconds", "0").toString()),
                Integer.parseInt(h.getOrDefault("totalSeconds", "0").toString()),
                parse(h.get("lastEventAt")),
                Integer.parseInt(h.getOrDefault("tabSwitchCount", "0").toString()),
                Integer.parseInt(h.getOrDefault("seekAttempts", "0").toString()),
                parse(h.get("lastSeekAt"))
        );
    }

    private OffsetDateTime parse(Object value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.parse(value.toString());
    }

    private String sessionKey(UUID sessionId) {
        return "video:session:" + sessionId;
    }

    private String progressKey(UUID studentId, UUID lessonId) {
        return "video:progress:" + studentId + ":" + lessonId;
    }

    public record ProgressState(UUID studentId, UUID lessonId, int watchedSeconds, int totalSeconds, OffsetDateTime lastEventAt,
                                int tabSwitchCount, int seekAttempts, OffsetDateTime lastSeekAt) {
    }
}
