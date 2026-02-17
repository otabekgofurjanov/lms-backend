package com.company.lms.video.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lms.video")
public record VideoProgressProperties(
        int progressPingMinIntervalSeconds,
        int flushIntervalSeconds,
        int requiredCompletionPctToUnlockQuiz,
        Suspicious suspicious
) {
    public record Suspicious(int tabSwitchThreshold, int seekAttemptThreshold) {
    }
}
