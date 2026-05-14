package com.inkwell.notification.service;

import com.inkwell.notification.entity.AuditLog;
import com.inkwell.notification.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void log_ShouldSaveAuditLog() {
        auditService.log(1L, "LOGIN", "USER", 2L, "details");

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void log_WhenSaveFails_ShouldSwallowException() {
        doThrow(new RuntimeException("boom")).when(auditLogRepository).save(any(AuditLog.class));

        auditService.log(1L, "LOGIN", "USER", 2L, "details");
    }

    @Test
    void getAuditLogsForActor_ShouldReturnRepositoryResult() {
        List<AuditLog> logs = List.of(AuditLog.builder().actorId(1L).build());
        when(auditLogRepository.findByActorIdOrderByTimestampDesc(1L)).thenReturn(logs);

        assertEquals(logs, auditService.getAuditLogsForActor(1L));
    }

    @Test
    void getAuditLogsForEntity_ShouldReturnRepositoryResult() {
        List<AuditLog> logs = List.of(AuditLog.builder().entityType("USER").entityId(2L).build());
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("USER", 2L)).thenReturn(logs);

        assertEquals(logs, auditService.getAuditLogsForEntity("USER", 2L));
    }
}
