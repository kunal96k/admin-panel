package com.tts.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.springframework.core.io.ByteArrayResource;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDate;

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
     * Send certificate email with attachment
     */
    public void sendCertificateEmail(String toEmail, String studentName, String certificateNo,
                                     String courseName, String grade, LocalDate issueDate,
                                     LocalDate courseFromDate, LocalDate courseToDate,
                                     String batch, byte[] certificateImage) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("studentName", studentName);
            context.setVariable("certificateNo", certificateNo);
            context.setVariable("courseName", courseName);
            context.setVariable("grade", grade);
            context.setVariable("issueDate", issueDate != null ?
                    issueDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : "N/A");
            context.setVariable("courseFromDate", courseFromDate != null ?
                    courseFromDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : null);
            context.setVariable("courseToDate", courseToDate != null ?
                    courseToDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : null);
            context.setVariable("batch", batch);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/certificate-email", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your Course Completion Certificate - " + courseName);
            helper.setText(htmlContent, true);

            // ✅ Attach certificate image
            if (certificateImage != null && certificateImage.length > 0) {
                helper.addAttachment(
                        "Certificate-TTS-" + certificateNo + ".jpg",
                        new ByteArrayResource(certificateImage),
                        "image/jpeg"
                );
            }

            mailSender.send(message);

            log.info("✅ Certificate email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send certificate email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send certificate email: " + e.getMessage());
        }
    }

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

            log.info(" Credentials email sent successfully to: {}", toEmail);

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

            log.info(" Password change notification sent to: {}", toEmail);

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