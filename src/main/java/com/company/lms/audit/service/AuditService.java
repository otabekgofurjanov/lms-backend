package com.company.lms.audit.service;

import com.company.lms.audit.entity.AuditLogEntity;
import com.company.lms.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(UUID actorUserId, String action, String entityType, String entityId, String beforeData, String afterData, String ip, String userAgent) {
        AuditLogEntity e = new AuditLogEntity();
        e.setId(UUID.randomUUID());
        e.setActorUserId(actorUserId);
        e.setAction(action);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setBeforeData(beforeData);
        e.setAfterData(afterData);
        e.setIp(ip);
        e.setUserAgent(userAgent);
        e.setCreatedAt(OffsetDateTime.now());
        auditLogRepository.save(e);
    }
}
