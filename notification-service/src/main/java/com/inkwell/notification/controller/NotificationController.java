package com.inkwell.notification.controller;

import com.inkwell.notification.dto.BroadcastRequest;
import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.dto.NotificationResponse;
import com.inkwell.notification.exception.CustomException;
import com.inkwell.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management", description = "Endpoints for sending and managing user notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://auth-service}")
    private String authServiceUrl;

    @Operation(summary = "Send notification", description = "Sends a single notification to a specific recipient.")
    @ApiResponse(responseCode = "200", description = "Notification sent successfully")
    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> send(
            @Valid @RequestBody NotificationRequest request,
            @RequestHeader("X-User-Role") String roleHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ensureAdmin(roleHeader);

        // Auto-fetch email if requested but not provided
        if (request.isSendEmail() && (request.getRecipientEmail() == null || request.getRecipientEmail().isEmpty())) {
            String email = fetchUserEmail(request.getRecipientId(), authHeader);
            if (email != null) {
                request.setRecipientEmail(email);
            }
        }

        return ResponseEntity.ok(notificationService.send(request));
    }

    private String fetchUserEmail(Long userId, String authHeader) {
        String url = authServiceUrl + "/auth/internal/users/" + userId + "/email";
        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        headers.set("X-User-Role", "ADMIN");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }

    @Operation(summary = "Send bulk notifications", description = "Sends a notification to multiple recipients simultaneously.")
    @ApiResponse(responseCode = "200", description = "Bulk notifications sent")
    @PostMapping("/send-bulk")
    public ResponseEntity<String> sendBulk(
            @RequestParam List<Long> recipientIds,
            @Valid @RequestBody NotificationRequest request,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        notificationService.sendBulk(recipientIds, request);
        return ResponseEntity.ok("Bulk notifications sent successfully");
    }

    @Operation(summary = "Get notifications by user", description = "Retrieves all notifications for a specific recipient.")
    @ApiResponse(responseCode = "200", description = "List of notifications retrieved")
    @GetMapping("/user/{recipientId}")
    public ResponseEntity<List<NotificationResponse>> getByUser(
            @PathVariable Long recipientId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureRecipientOrAdmin(recipientId, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.ok(notificationService.getByRecipient(recipientId));
    }

    @Operation(summary = "Get unread count", description = "Returns the count of unread notifications for a specific recipient.")
    @ApiResponse(responseCode = "200", description = "Count retrieved successfully")
    @GetMapping("/unread-count/{recipientId}")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable Long recipientId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureRecipientOrAdmin(recipientId, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.ok(notificationService.getUnreadCount(recipientId));
    }

    @Operation(summary = "Mark as read", description = "Marks a specific notification as read.")
    @ApiResponse(responseCode = "200", description = "Notification marked as read")
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureNotificationOwnerOrAdmin(id, Long.parseLong(userIdHeader), roleHeader);
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Mark all as read", description = "Marks all unread notifications for a recipient as read.")
    @ApiResponse(responseCode = "200", description = "All notifications marked as read")
    @PutMapping("/read-all/{recipientId}")
    public ResponseEntity<Void> markAllRead(
            @PathVariable Long recipientId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureRecipientOrAdmin(recipientId, Long.parseLong(userIdHeader), roleHeader);
        notificationService.markAllRead(recipientId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete read notifications", description = "Deletes all read notifications for a specific recipient.")
    @ApiResponse(responseCode = "204", description = "Read notifications deleted")
    @DeleteMapping("/read/{recipientId}")
    public ResponseEntity<Void> deleteRead(
            @PathVariable Long recipientId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureRecipientOrAdmin(recipientId, Long.parseLong(userIdHeader), roleHeader);
        notificationService.deleteRead(recipientId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete notification", description = "Permanently deletes a specific notification.")
    @ApiResponse(responseCode = "204", description = "Notification deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureNotificationOwnerOrAdmin(id, Long.parseLong(userIdHeader), roleHeader);
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get all notifications", description = "Retrieves all notifications stored in the system (Admin only).")
    @ApiResponse(responseCode = "200", description = "List of all notifications retrieved")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(@RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(notificationService.getAll());
    }

    private void ensureNotificationOwnerOrAdmin(Long notificationId, Long requesterId, String roleHeader) {
        NotificationResponse notification = notificationService.getNotification(notificationId);
        ensureRecipientOrAdmin(notification.getRecipientId(), requesterId, roleHeader);
    }

    @Operation(summary = "Broadcast notification by role",
               description = "Sends an in-app notification to all users of a given role. Use targetRole=ALL to send to everyone. Admin only.")
    @ApiResponse(responseCode = "200", description = "Broadcast sent")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    @PostMapping("/broadcast")
    public ResponseEntity<String> broadcast(
            @Valid @RequestBody BroadcastRequest request,
            @RequestHeader("X-User-Role") String roleHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ensureAdmin(roleHeader);

        List<Long> recipientIds;

        if ("ALL".equalsIgnoreCase(request.getTargetRole())) {
            Set<Long> recipients = new LinkedHashSet<>();
            for (String targetRole : List.of("READER", "AUTHOR", "ADMIN")) {
                recipients.addAll(fetchUserIdsForRole(targetRole, roleHeader, authHeader));
            }
            recipientIds = recipients.stream().toList();
        } else {
            recipientIds = fetchUserIdsForRole(request.getTargetRole(), roleHeader, authHeader);
        }

        if (recipientIds.isEmpty()) {
            return ResponseEntity.ok("No users found for role: " + request.getTargetRole());
        }

        notificationService.sendBulkNotification(recipientIds, request.getTitle(), request.getMessage(), "ADMIN_BROADCAST");
        return ResponseEntity.ok("Broadcast sent to " + recipientIds.size() + " users.");
    }

    private List<Long> fetchUserIdsForRole(String targetRole, String roleHeader, String authHeader) {
        String url = authServiceUrl + "/auth/users/by-role?role=" + targetRole;
        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        headers.set("X-User-Role", roleHeader);

        ResponseEntity<List<Long>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                new ParameterizedTypeReference<List<Long>>() {});

        return response.getBody() == null ? List.of() : response.getBody();
    }

    private void ensureRecipientOrAdmin(Long recipientId, Long requesterId, String roleHeader) {
        if (isAdmin(roleHeader) || recipientId.equals(requesterId)) {
            return;
        }
        throw new CustomException("You do not have permission to access these notifications", HttpStatus.FORBIDDEN);
    }

    private void ensureAdmin(String roleHeader) {
        if (!isAdmin(roleHeader)) {
            throw new CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isAdmin(String roleHeader) {
        return "ADMIN".equalsIgnoreCase(roleHeader);
    }
}
