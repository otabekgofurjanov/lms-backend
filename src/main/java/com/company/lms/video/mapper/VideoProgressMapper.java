package com.company.lms.video.mapper;

import com.company.lms.video.dto.VideoProgressSnapshotResponse;
import com.company.lms.video.entity.VideoProgressEntity;
import com.company.lms.video.service.VideoProgressRedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VideoProgressMapper {
    private final ObjectMapper objectMapper;

    public VideoProgressSnapshotResponse fromRedis(VideoProgressRedisService.ProgressState state) {
        return new VideoProgressSnapshotResponse(
                state.watchedSeconds(),
                state.totalSeconds(),
                calcCompletion(state.watchedSeconds(), state.totalSeconds()),
                mapSuspicious(state.tabSwitchCount(), state.seekAttempts(), state.lastSeekAt() == null ? null : state.lastSeekAt().toString()),
                state.lastEventAt()
        );
    }

    public VideoProgressSnapshotResponse fromEntity(VideoProgressEntity entity) {
        return new VideoProgressSnapshotResponse(
                entity.getWatchedSeconds(),
                entity.getTotalSeconds(),
                entity.getCompletionPct(),
                parseFlags(entity.getSuspiciousFlags()),
                entity.getLastEventAt()
        );
    }

    public String suspiciousJson(VideoProgressRedisService.ProgressState state) {
        try {
            return objectMapper.writeValueAsString(mapSuspicious(state.tabSwitchCount(), state.seekAttempts(), state.lastSeekAt() == null ? null : state.lastSeekAt().toString()));
        } catch (Exception e) {
            return "{}";
        }
    }

    public BigDecimal calcCompletion(int watched, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        double pct = ((double) watched / (double) total) * 100.0;
        pct = Math.max(0d, Math.min(100d, pct));
        return BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> mapSuspicious(int tabSwitchCount, int seekAttempts, String lastSeekAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("tabSwitchCount", tabSwitchCount);
        m.put("seekAttempts", seekAttempts);
        m.put("lastSeekAt", lastSeekAt);
        return m;
    }

    private Map<String, Object> parseFlags(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
