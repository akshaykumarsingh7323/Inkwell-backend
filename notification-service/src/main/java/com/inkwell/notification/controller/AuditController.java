package com.inkwell.notification.controller;

import com.inkwell.notification.entity.AuditLog;
import com.inkwell.notification.exception.CustomException;
import com.inkwell.notification.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.time.LocalDateTime;

/**
 * Admin-only endpoint for viewing the platform audit trail.
 * All requests must carry X-User-Role: ADMIN (injected by the Gateway).
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Admin endpoint for viewing the platform audit trail")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @Operation(
        summary = "Get audit logs",
        description = "Returns a paginated, filterable list of all audit log entries. Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Audit logs retrieved")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestHeader("X-User-Role") String roleHeader,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            throw new CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<AuditLog> result = auditLogRepository.findWithFilters(
                actorId, action, entityType, from, to, pageable);

        return ResponseEntity.ok(result);
    }
}
