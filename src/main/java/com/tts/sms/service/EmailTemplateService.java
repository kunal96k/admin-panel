package com.tts.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDate;
import java.util.Base64;

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
     *  Send fee receipt email with PDF (FULLY ASYNC)
     */
    @Async("emailTaskExecutor")
    public void sendFeeReceiptWithPDF(
            String toEmail,
            String studentName,
            String receiptNo,
            Double amount,
            LocalDate receiptDate,
            String paymentMode,
            String message,
            String pdfBase64) {

        log.info(" [ASYNC] Sending receipt email to: {}", toEmail);

        // ADDED: Log PDF size
        if (pdfBase64 != null) {
            int sizeKB = (pdfBase64.length() * 3 / 4) / 1024;
            log.info("[STATS] PDF attachment size: {} KB", sizeKB);
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // Prepare email context
            Context context = new Context();
            context.setVariable("studentName", studentName);
            context.setVariable("receiptNo", receiptNo);
            context.setVariable("amount", String.format("₹%.2f", amount));
            context.setVariable("receiptDate", receiptDate != null ?
                    receiptDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : "N/A");
            context.setVariable("paymentMode", paymentMode);
            context.setVariable("message", message != null ? message : "");
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/receipt-email", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Fee Receipt - " + receiptNo + " - TechnoKraft Training Solutions");
            helper.setText(htmlContent, true);

            // Decode base64 PDF and attach
            if (pdfBase64 != null && !pdfBase64.isEmpty()) {
                try {
                    log.info("[SYNC] Decoding PDF base64 (length: {})", pdfBase64.length());

                    byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);

                    log.info(" PDF decoded successfully: {} bytes", pdfBytes.length);

                    helper.addAttachment(
                            "Fee-Receipt-" + receiptNo + ".pdf",
                            new ByteArrayResource(pdfBytes),
                            "application/pdf"
                    );

                    log.info(" PDF attachment added: Fee-Receipt-{}.pdf ({} bytes)", receiptNo, pdfBytes.length);
                } catch (IllegalArgumentException e) {
                    log.error(" Invalid base64 PDF data: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to decode PDF: Invalid base64 format");
                } catch (Exception e) {
                    log.error(" Failed to attach PDF: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to attach PDF: " + e.getMessage());
                }
            } else {
                log.warn("No PDF data provided - sending email without attachment");
            }

            // Send email
            log.info(" Sending email...");
            mailSender.send(mimeMessage);

            log.info(" Fee receipt email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error(" Failed to send fee receipt email to: {} - {}", toEmail, e.getMessage(), e);
        } catch (Exception e) {
            log.error(" Unexpected error sending email to: {} - {}", toEmail, e.getMessage(), e);
        }
    }

    /**
     * Send HTML email
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info(" HTML email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error(" Failed to send HTML email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send certificate email with attachment
     */
    @Async("emailTaskExecutor")
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

            if (certificateImage != null && certificateImage.length > 0) {
                helper.addAttachment(
                        "Certificate-TTS-" + certificateNo + ".jpg",
                        new ByteArrayResource(certificateImage),
                        "image/jpeg"
                );
            }

            mailSender.send(message);

            log.info(" Certificate email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error(" Failed to send certificate email to: {}", toEmail, e);
        } catch (Exception e) {
            log.error(" Unexpected error sending certificate email", e);
        }
    }

    /**
     * Send employee credentials email with HTML template
     */
    @Async("emailTaskExecutor")
    public void sendCredentialsEmail(String toEmail, String employeeName, String username, String password) {
        sendCredentialsEmail(toEmail, employeeName, username, password, false);
    }

    @Async("emailTaskExecutor")
    public void sendCredentialsEmail(String toEmail, String employeeName, String username, String password, boolean isUpdate) {
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
            context.setVariable("isUpdate", isUpdate);

            String htmlContent = templateEngine.process("email/credentials-email", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(isUpdate ? "Login Details Updated - TTS SMS" : "Your Login Credentials - TTS SMS");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info(" Credentials email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error(" Failed to send credentials email to: {}", toEmail, e);
        }
    }

    /**
     * Send password change notification
     */
    @Async("emailTaskExecutor")
    public void sendPasswordChangedEmail(String toEmail, String employeeName, String username, String newPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("username", username);
            context.setVariable("password", newPassword);
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
            log.error(" Failed to send password change email to: {}", toEmail, e);
        }
    }

    /**
     * Send birthday wishes email
     */
    @Async("emailTaskExecutor")
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
        }
    }

    /**
     * Send fee due reminder email (5 days in advance)
     */
    @Async("emailTaskExecutor")
    public void sendFeeDueReminderEmail(
            String toEmail,
            String studentName,
            String registrationNumber,
            String courseName,
            Integer installmentNumber,
            Double dueAmount,
            LocalDate dueDate,
            Double totalPendingFees) {

        log.info(" [ASYNC] Sending fee due reminder email to: {} for regNo: {}", toEmail, registrationNumber);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("studentName", studentName != null ? studentName : "Student");
            context.setVariable("regNo", registrationNumber);
            context.setVariable("courseName", courseName != null ? courseName : "");
            context.setVariable("installmentText", installmentNumber != null ? "Installment #" + installmentNumber : "Upcoming Installment");
            context.setVariable("dueAmount", dueAmount != null ? String.format("₹%,.2f", dueAmount) : "₹0.00");
            context.setVariable("dueDate", dueDate != null ?
                    dueDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : "Upcoming");
            context.setVariable("totalDue", totalPendingFees != null && totalPendingFees > 0 ?
                    String.format("₹%,.2f", totalPendingFees) : null);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/fee-due-reminder", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Fee Due Reminder - Installment Due in 5 Days - TechnoKraft");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info(" Fee due reminder email sent successfully to: {} ({})", toEmail, registrationNumber);

        } catch (MessagingException e) {
            log.error(" Failed to send fee due reminder email to: {} - {}", toEmail, e.getMessage(), e);
        } catch (Exception e) {
            log.error(" Unexpected error sending fee due reminder email to: {} - {}", toEmail, e.getMessage(), e);
        }
    }
}