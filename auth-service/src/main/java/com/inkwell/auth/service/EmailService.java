package com.inkwell.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendResetPasswordEmail(String to, String otp) {
        String subject = "InkWell - Your Password Reset OTP";
        String content = getEmailTemplate(
            "Password Reset Request",
            "Hello,",
            "We received a request to reset your InkWell account password. Please use the One-Time Password (OTP) below to proceed:",
            "<div style='background: #f8fafc; border: 2px dashed #e2e8f0; padding: 25px; text-align: center; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #004643; margin: 25px 0; border-radius: 12px;'>" + otp + "</div>" +
            "<p style='text-align: center; color: #64748b; font-size: 14px;'>This code is valid for 10 minutes. If you didn't request this, you can safely ignore this email.</p>",
            null,
            null
        );

        try {
            sendEmail(to, subject, content);
            log.info("Reset password OTP email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send reset password email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String fullName) {
        String subject = "Welcome to the InkWell Community!";
        String content = getEmailTemplate(
            "Welcome to InkWell",
            "Hello " + fullName + ",",
            "We're absolutely thrilled to have you join our community of creative thinkers and storytellers. InkWell is more than just a platform—it's your digital sanctuary for expression, inspiration, and connection.",
            "<p>Your journey starts here. Whether you're here to share your wisdom or discover new perspectives, we're here to support your growth every step of the way.</p>",
            "http://localhost:4200",
            "Start Your Journey"
        );

        try {
            sendEmail(to, subject, content);
            log.info("Welcome email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendLoginNotificationEmail(String to, String username) {
        String subject = "New Login to Your InkWell Account";
        String time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"));
        String content = getEmailTemplate(
            "Security Notification",
            "Hello " + username + ",",
            "This is a quick security note to let you know that a new login was detected for your InkWell account.",
            "<div style='background: #f8fafc; padding: 15px; border-radius: 10px; margin: 20px 0; border: 1px solid #e2e8f0;'>" +
            "<p style='margin: 0; color: #64748b;'><strong>Time:</strong> " + time + "</p>" +
            "</div>" +
            "<p>If this was you, you can safely ignore this email. If you don't recognize this activity, please reset your password immediately to secure your account.</p>",
            "http://localhost:4200/forgot-password",
            "Secure My Account"
        );

        try {
            sendEmail(to, subject, content);
            log.info("Login notification email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send login notification email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendSuspensionEmail(String to, String fullName) {
        String subject = "InkWell - Account Status Update";
        String content = getEmailTemplate(
            "Account Suspended",
            "Hello " + fullName + ",",
            "We regret to inform you that your InkWell account has been suspended due to violations of our community guidelines.",
            "<p>Maintaining a safe and inspiring environment for all our members is our top priority. If you believe this was a mistake or would like to appeal this decision, please contact our support team.</p>" +
            "<div style='background: #fef2f2; padding: 15px; border-radius: 10px; margin: 20px 0; border: 1px solid #fee2e2; text-align: center;'>" +
            "<strong style='color: #b91c1c;'>Support Contact:</strong> <a href='mailto:support@inkwell.com' style='color: #b91c1c; text-decoration: underline;'>support@inkwell.com</a>" +
            "</div>",
            null,
            null
        );

        try {
            sendEmail(to, subject, content);
            log.info("Suspension email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send suspension email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendAccountDeletionEmail(String to, String fullName) {
        String subject = "InkWell - Account Deleted";
        String content = getEmailTemplate(
            "Account Permanently Deleted",
            "Hello " + fullName + ",",
            "This is to confirm that your InkWell account has been permanently removed from our platform for serious rule violations.",
            "<p>All your data, including posts and profile information, has been purged from our active systems in accordance with our safety policies.</p>",
            null,
            null
        );

        try {
            sendEmail(to, subject, content);
            log.info("Account deletion email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send deletion email to {}: {}", to, e.getMessage());
        }
    }

    private String getEmailTemplate(String title, String greeting, String mainText, String extraHtml, String buttonUrl, String buttonText) {
        String buttonSection = "";
        if (buttonUrl != null && buttonText != null) {
            buttonSection = "<div style='text-align: center; margin: 35px 0;'>" +
                    "<a href='" + buttonUrl + "' style='background-color: #004643; color: #ffffff; padding: 16px 32px; text-decoration: none; border-radius: 12px; font-weight: 700; font-size: 16px; display: inline-block; box-shadow: 0 4px 12px rgba(0, 70, 67, 0.2); transition: all 0.3s;'> " + buttonText + " </a>" +
                    "</div>";
        }

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "</head>" +
                "<body style='margin: 0; padding: 0; background-color: #f0f2f5; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif;'>" +
                "  <table align='center' border='0' cellpadding='0' cellspacing='0' width='100%' style='max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.05);'>" +
                "    <tr>" +
                "      <td style='background-color: #004643; padding: 40px 20px; text-align: center;'>" +
                "        <h1 style='color: #ffffff; margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.5px;'>InkWell</h1>" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='padding: 40px 40px 20px 40px;'>" +
                "        <h2 style='color: #1a1a1a; margin: 0 0 20px 0; font-size: 24px; font-weight: 700;'>" + title + "</h2>" +
                "        <p style='color: #1a1a1a; font-size: 16px; line-height: 1.6; font-weight: 500;'>" + greeting + "</p>" +
                "        <p style='color: #4b5563; font-size: 16px; line-height: 1.6;'>" + mainText + "</p>" +
                "        " + (extraHtml != null ? extraHtml : "") +
                "        " + buttonSection +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='padding: 0 40px 40px 40px;'>" +
                "        <p style='color: #1a1a1a; font-size: 16px; line-height: 1.6; margin-bottom: 0;'>Best regards,<br><strong>The InkWell Team</strong></p>" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style='background-color: #f8fafc; padding: 30px; text-align: center; border-top: 1px solid #e2e8f0;'>" +
                "        <p style='color: #94a3b8; font-size: 13px; margin: 0;'>&copy; 2026 InkWell Platform. All rights reserved.</p>" +
                "        <p style='color: #94a3b8; font-size: 13px; margin: 10px 0 0 0;'>You are receiving this email because you registered on InkWell.</p>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</body>" +
                "</html>";
    }

    private void sendEmail(String to, String subject, String content) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("InkWell <noreply@inkwell.com>");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);
        mailSender.send(message);
    }
}
