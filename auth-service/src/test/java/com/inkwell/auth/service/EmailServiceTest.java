package com.inkwell.auth.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendResetPasswordEmail_ShouldSendMessage() {
        emailService.sendResetPasswordEmail("john@example.com", "123456");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendWelcomeEmail_ShouldSendMessage() {
        emailService.sendWelcomeEmail("john@example.com", "John Doe");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendLoginNotificationEmail_ShouldSendMessage() {
        emailService.sendLoginNotificationEmail("john@example.com", "john");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendSuspensionEmail_ShouldSendMessage() {
        emailService.sendSuspensionEmail("john@example.com", "John Doe");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendAccountDeletionEmail_ShouldSendMessage() {
        emailService.sendAccountDeletionEmail("john@example.com", "John Doe");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendMethods_WhenMailSenderFails_ShouldBeSwallowed() {
        doThrow(new RuntimeException("mail failure")).when(mailSender).send(any(MimeMessage.class));

        emailService.sendResetPasswordEmail("john@example.com", "123456");
        emailService.sendWelcomeEmail("john@example.com", "John Doe");
        emailService.sendLoginNotificationEmail("john@example.com", "john");
        emailService.sendSuspensionEmail("john@example.com", "John Doe");
        emailService.sendAccountDeletionEmail("john@example.com", "John Doe");

        verify(mailSender, times(5)).send(any(MimeMessage.class));
    }
}
