package com.inkwell.notification.controller;

import com.inkwell.notification.dto.BroadcastRequest;
import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.dto.NotificationResponse;
import com.inkwell.notification.enums.NotificationType;
import com.inkwell.notification.exception.CustomException;
import com.inkwell.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NotificationController notificationController;

    private NotificationRequest request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationController, "authServiceUrl", "http://auth-service");
        request = NotificationRequest.builder()
                .recipientId(2L)
                .actorId(1L)
                .type(NotificationType.NEW_POST)
                .title("Title")
                .message("Message")
                .build();
    }

    @Test
    void send_AsAdmin_ShouldReturnResponse() {
        NotificationResponse response = new NotificationResponse();
        when(notificationService.send(request)).thenReturn(response);

        assertEquals(response, notificationController.send(request, "ADMIN", "Bearer token").getBody());
    }

    @Test
    void send_WhenNotAdmin_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> notificationController.send(request, "AUTHOR", null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void sendBulk_AsAdmin_ShouldDelegate() {
        ResponseEntity<String> response = notificationController.sendBulk(List.of(1L, 2L), request, "ADMIN");

        assertEquals("Bulk notifications sent successfully", response.getBody());
        verify(notificationService).sendBulk(List.of(1L, 2L), request);
    }

    @Test
    void getByUser_AsRecipient_ShouldReturnNotifications() {
        List<NotificationResponse> responses = List.of(new NotificationResponse());
        when(notificationService.getByRecipient(2L)).thenReturn(responses);

        assertEquals(responses, notificationController.getByUser(2L, "2", "READER").getBody());
    }

    @Test
    void getByUser_WhenUnauthorized_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> notificationController.getByUser(2L, "3", "READER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void getUnreadCount_ShouldReturnCount() {
        when(notificationService.getUnreadCount(2L)).thenReturn(5L);

        assertEquals(5L, notificationController.getUnreadCount(2L, "2", "READER").getBody());
    }

    @Test
    void markAsRead_ShouldDelegateForOwner() {
        NotificationResponse response = new NotificationResponse();
        response.setRecipientId(2L);
        when(notificationService.getNotification(10L)).thenReturn(response);

        notificationController.markAsRead(10L, "2", "READER");

        verify(notificationService).markAsRead(10L);
    }

    @Test
    void markAllRead_ShouldDelegate() {
        notificationController.markAllRead(2L, "2", "READER");

        verify(notificationService).markAllRead(2L);
    }

    @Test
    void deleteRead_ShouldDelegate() {
        notificationController.deleteRead(2L, "2", "READER");

        verify(notificationService).deleteRead(2L);
    }

    @Test
    void delete_ShouldDelegateForAdmin() {
        NotificationResponse response = new NotificationResponse();
        response.setRecipientId(7L);
        when(notificationService.getNotification(10L)).thenReturn(response);

        notificationController.delete(10L, "1", "ADMIN");

        verify(notificationService).deleteNotification(10L);
    }

    @Test
    void getAll_AsAdmin_ShouldReturnAllNotifications() {
        List<NotificationResponse> responses = List.of(new NotificationResponse());
        when(notificationService.getAll()).thenReturn(responses);

        assertEquals(responses, notificationController.getAll("ADMIN").getBody());
    }

    @Test
    void broadcast_ForSpecificRole_ShouldSendBulkNotification() {
        BroadcastRequest broadcastRequest = new BroadcastRequest();
        broadcastRequest.setTargetRole("AUTHOR");
        broadcastRequest.setTitle("Title");
        broadcastRequest.setMessage("Message");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(4L, 5L)));

        ResponseEntity<String> response = notificationController.broadcast(broadcastRequest, "ADMIN", "Bearer token");

        assertEquals("Broadcast sent to 2 users.", response.getBody());
        verify(notificationService).sendBulkNotification(List.of(4L, 5L), "Title", "Message", "ADMIN_BROADCAST");
    }

    @Test
    void broadcast_ForAllRoles_ShouldDeduplicateRecipients() {
        BroadcastRequest broadcastRequest = new BroadcastRequest();
        broadcastRequest.setTargetRole("ALL");
        broadcastRequest.setTitle("Title");
        broadcastRequest.setMessage("Message");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of(1L, 2L)))
                .thenReturn(ResponseEntity.ok(List.of(2L, 3L)))
                .thenReturn(ResponseEntity.ok(List.of(3L)));

        ResponseEntity<String> response = notificationController.broadcast(broadcastRequest, "ADMIN", null);

        assertEquals("Broadcast sent to 3 users.", response.getBody());
        verify(notificationService).sendBulkNotification(List.of(1L, 2L, 3L), "Title", "Message", "ADMIN_BROADCAST");
    }

    @Test
    void broadcast_WhenNoUsersFound_ShouldReturnMessage() {
        BroadcastRequest broadcastRequest = new BroadcastRequest();
        broadcastRequest.setTargetRole("AUTHOR");
        broadcastRequest.setTitle("Title");
        broadcastRequest.setMessage("Message");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));

        ResponseEntity<String> response = notificationController.broadcast(broadcastRequest, "ADMIN", null);

        assertEquals("No users found for role: AUTHOR", response.getBody());
    }
}
