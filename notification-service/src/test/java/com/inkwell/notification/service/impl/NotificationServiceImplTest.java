package com.inkwell.notification.service.impl;

import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.dto.NotificationResponse;
import com.inkwell.notification.entity.Notification;
import com.inkwell.notification.enums.NotificationType;
import com.inkwell.notification.exception.CustomException;
import com.inkwell.notification.repository.NotificationRepository;
import com.inkwell.notification.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuditService auditService;

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void send_ShouldPersistAuditAndReturnResponse() {
        NotificationRequest request = NotificationRequest.builder()
                .recipientId(1L)
                .actorId(2L)
                .type(NotificationType.NEW_COMMENT)
                .title("Title")
                .message("Message")
                .build();

        Notification saved = Notification.builder().notificationId(11L).recipientId(1L).actorId(2L).type(NotificationType.NEW_COMMENT).title("Title").message("Message").build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        NotificationResponse response = notificationService.send(request);

        assertEquals(11L, response.getNotificationId());
        verify(auditService).log(2L, "SEND_NOTIFICATION", "NOTIFICATION", 11L, "Sent NEW_COMMENT notification to 1");
    }

    @Test
    void send_WithEmail_ShouldAttemptMail() {
        NotificationRequest request = NotificationRequest.builder()
                .recipientId(1L)
                .recipientEmail("test@example.com")
                .sendEmail(true)
                .type(NotificationType.ADMIN_BROADCAST)
                .title("Title")
                .message("Message")
                .build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(Notification.builder().notificationId(1L).type(NotificationType.ADMIN_BROADCAST).build());

        notificationService.send(request);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_WithEmailFailure_ShouldNotFailEntireProcess() {
        NotificationRequest request = NotificationRequest.builder()
                .recipientId(1L)
                .recipientEmail("test@example.com")
                .sendEmail(true)
                .type(NotificationType.ADMIN_BROADCAST)
                .title("Title")
                .message("Message")
                .build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(Notification.builder().notificationId(1L).type(NotificationType.ADMIN_BROADCAST).build());
        doThrow(new RuntimeException("Mail server down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> notificationService.send(request));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendBulk_ShouldSaveAll() {
        NotificationRequest request = NotificationRequest.builder()
                .actorId(1L)
                .message("Msg")
                .title("Title")
                .type(NotificationType.ADMIN_BROADCAST)
                .build();

        notificationService.sendBulk(List.of(1L, 2L), request);

        verify(notificationRepository).saveAll(any());
    }

    @Test
    void sendBulkNotification_ShouldWrapRequest() {
        notificationService.sendBulkNotification(List.of(1L, 2L), "Title", "Message", "ADMIN_BROADCAST");
        verify(notificationRepository).saveAll(any());
    }

    @Test
    void sendBroadcastToAll_WhenNoUsers_ShouldDoNothing() {
        when(notificationRepository.findAll()).thenReturn(List.of());
        notificationService.sendBroadcastToAll("Title", "Message");
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void sendBroadcastToAll_WhenUsersExist_ShouldBroadcastDistinctRecipients() {
        when(notificationRepository.findAll()).thenReturn(List.of(
                Notification.builder().recipientId(1L).build(),
                Notification.builder().recipientId(1L).build(),
                Notification.builder().recipientId(2L).build()
        ));

        notificationService.sendBroadcastToAll("Title", "Message");

        verify(notificationRepository).saveAll(any());
    }

    @Test
    void markOperations_ShouldDelegateToRepository() {
        notificationService.markAsRead(1L);
        notificationService.markAllRead(2L);
        notificationService.deleteRead(3L);

        verify(notificationRepository).markAsRead(1L);
        verify(notificationRepository).markAllRead(2L);
        verify(notificationRepository).deleteByRecipientIdAndIsRead(3L, true);
    }

    @Test
    void getByRecipient_ShouldMapResults() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                Notification.builder().notificationId(1L).recipientId(1L).type(NotificationType.NEW_COMMENT).title("Title").message("Msg").build()
        ));
        assertEquals(1, notificationService.getByRecipient(1L).size());
    }

    @Test
    void getUnreadCount_ShouldReturnRepositoryCount() {
        when(notificationRepository.countByRecipientIdAndIsRead(1L, false)).thenReturn(5L);
        assertEquals(5L, notificationService.getUnreadCount(1L));
    }

    @Test
    void deleteNotification_ShouldDeleteById() {
        notificationService.deleteNotification(1L);
        verify(notificationRepository).deleteById(1L);
    }

    @Test
    void getNotification_WhenMissing_ShouldThrowNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> notificationService.getNotification(1L));
    }

    @Test
    void getNotification_ShouldReturnMappedResponse() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(
                Notification.builder().notificationId(1L).recipientId(2L).type(NotificationType.NEW_COMMENT).title("Title").message("Msg").build()
        ));

        NotificationResponse response = notificationService.getNotification(1L);

        assertEquals(1L, response.getNotificationId());
    }

    @Test
    void sendEmail_ShouldSendMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        notificationService.sendEmail("test@example.com", "Subject", "Body");

        verify(mailSender).send(captor.capture());
        assertEquals("Subject", captor.getValue().getSubject());
    }

    @Test
    void sendEmail_WhenMailerFails_ShouldSwallowException() {
        doThrow(new RuntimeException("boom")).when(mailSender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> notificationService.sendEmail("test@example.com", "Subject", "Body"));
    }

    @Test
    void getAll_ShouldMapRepositoryResults() {
        when(notificationRepository.findAll()).thenReturn(List.of(
                Notification.builder().notificationId(1L).recipientId(2L).type(NotificationType.NEW_COMMENT).title("Title").message("Msg").build()
        ));
        assertEquals(1, notificationService.getAll().size());
    }

    @Test
    void notifyAdmins_ShouldFetchAdminIdsAndSendBulk() {
        List<Long> adminIds = List.of(10L, 20L);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(adminIds));

        notificationService.notifyAdmins("New Post", "A post needs review", 1L, "post-slug");

        verify(notificationRepository).saveAll(any());
    }

    @Test
    void notifyAdmins_WhenNoAdminsFound_ShouldReturnEarly() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));

        notificationService.notifyAdmins("Title", "Msg", 1L, "slug");

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void notifyAdmins_WhenFetchFails_ShouldHandleException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("auth down"));

        assertDoesNotThrow(() -> notificationService.notifyAdmins("Title", "Msg", 1L, "slug"));
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void fetchUserIdsByRole_ShouldReturnEmptyOnBodyNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

        notificationService.notifyAdmins("Title", "Msg", 1L, "slug");
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void send_WithEmail_ShouldInvokeMailSender() {
        Notification notification = Notification.builder().notificationId(1L).build();
        when(notificationRepository.save(any())).thenReturn(notification);
        
        NotificationRequest request = NotificationRequest.builder()
                .recipientId(1L)
                .sendEmail(true)
                .recipientEmail("user@example.com")
                .title("Title")
                .message("Msg")
                .build();
        
        notificationService.send(request);
        
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
