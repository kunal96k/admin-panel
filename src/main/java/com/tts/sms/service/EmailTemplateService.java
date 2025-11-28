package com.tts.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send employee credentials email with HTML template
     */
    public void sendCredentialsEmail(String toEmail, String employeeName, String username, String password) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("username", username);
            context.setVariable("password", password);
            context.setVariable("loginUrl", baseUrl + "/login");
            context.setVariable("companyName", "TechnoKraft Training & Solution Pvt. Ltd.");
            context.setVariable("supportEmail", "support@tts.net.in");
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/credentials-email", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your Login Credentials - TTS SMS");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("✅ Credentials email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send credentials email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    /**
     * Send password change notification
     */
    public void sendPasswordChangedEmail(String toEmail, String employeeName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("loginUrl", baseUrl + "/login");
            context.setVariable("supportEmail", "support@tts.net.in");
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/password-changed", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Password Changed - TTS SMS");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("✅ Password change notification sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send password change email to: {}", toEmail, e);
        }
    }

    /**
     * Send birthday wishes email
     */
    public void sendBirthdayWishes(String toEmail, String employeeName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/birthday-wishes", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(" Happy Birthday from TechnoKraft Team!");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info(" Birthday wishes sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error(" Failed to send birthday wishes to: {}", toEmail, e);
            throw new RuntimeException("Failed to send birthday email: " + e.getMessage());
        }
    }
}