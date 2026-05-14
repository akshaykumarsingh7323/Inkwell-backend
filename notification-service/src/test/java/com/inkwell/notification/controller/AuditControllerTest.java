package com.inkwell.notification.controller;

import com.inkwell.notification.entity.AuditLog;
import com.inkwell.notification.exception.CustomException;
import com.inkwell.notification.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditController auditController;

    @Test
    void getAuditLogs_AsAdmin_ShouldReturnPage() {
        Page<AuditLog> page = new PageImpl<>(List.of(AuditLog.builder().action("LOGIN").build()));
        when(auditLogRepository.findWithFilters(eq(1L), eq("LOGIN"), eq("USER"), any(), any(), any(Pageable.class))).thenReturn(page);

        assertEquals(page, auditController.getAuditLogs("ADMIN", 1L, "LOGIN", "USER",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 0, 20).getBody());
    }

    @Test
    void getAuditLogs_WhenSizeExceedsMax_ShouldCapTo100() {
        Page<AuditLog> page = new PageImpl<>(List.of());
        when(auditLogRepository.findWithFilters(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        assertEquals(page, auditController.getAuditLogs("ADMIN", null, null, null, null, null, 0, 500).getBody());
    }

    @Test
    void getAuditLogs_WhenNotAdmin_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class,
                () -> auditController.getAuditLogs("AUTHOR", null, null, null, null, null, 0, 20));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }
}
