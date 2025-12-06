// ==================== UPDATE EmailTemplateService.java ====================

package com.tts.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
     *  Send fee receipt email with PDF generated from frontend
     */
    public void sendFeeReceiptWithPDF(
            String toEmail,
            String studentName,
            String receiptNo,
            Double amount,
            LocalDate receiptDate,
            String paymentMode,
            String message,
            String pdfBase64) {

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
            context.setVariable("message", message);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/receipt-email", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Fee Receipt - " + receiptNo + " - TTS");
            helper.setText(htmlContent, true);

            // Decode base64 PDF and attach
            if (pdfBase64 != null && !pdfBase64.isEmpty()) {
                try {
                    byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);

                    helper.addAttachment(
                            "Fee-Receipt-" + receiptNo + ".pdf",
                            new ByteArrayResource(pdfBytes),
                            "application/pdf"
                    );

                    log.info(" PDF attachment added: Fee-Receipt-{}.pdf ({} bytes)", receiptNo, pdfBytes.length);
                } catch (Exception e) {
                    log.error("❌ Failed to decode/attach PDF", e);
                    throw new RuntimeException("Failed to attach PDF: " + e.getMessage());
                }
            } else {
                log.warn("⚠️ No PDF data provided - sending email without attachment");
            }

            mailSender.send(mimeMessage);

            log.info(" Fee receipt email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send fee receipt email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send fee receipt email: " + e.getMessage());
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
            log.info("✅ HTML email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("❌ Failed to send HTML email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

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
            helper.setSubject("🎂 Happy Birthday from TechnoKraft Team!");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info(" Birthday wishes sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send birthday wishes to: {}", toEmail, e);
            throw new RuntimeException("Failed to send birthday email: " + e.getMessage());
        }
    }
}