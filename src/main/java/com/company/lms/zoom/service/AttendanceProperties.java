package com.company.lms.zoom.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lms.attendance")
public record AttendanceProperties(int presentThresholdPct, int lateThresholdPct) {
}
