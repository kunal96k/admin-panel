package com.tts.sms.service;

import com.tts.sms.model.User;
import com.tts.sms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.superadmin.email:kunalpatil192001@gmail.com}")
    private String superAdminEmail;

    @Value("${app.superadmin.password:admin@123}")
    private String superAdminPassword;

    /**
     * Process forgot password request
     * Only allows super admin email
     */
    @Transactional(readOnly = true)
    public boolean processForgotPassword(String email) {
        log.info("🔐 Processing forgot password request for email: {}", email);

        // Verify if email matches super admin email
        if (!email.equalsIgnoreCase(superAdminEmail)) {
            log.warn("⚠️ Unauthorized forgot password attempt for email: {}", email);
            return false;
        }

        try {
            // Find user by super admin email
            User user = userRepository.findByEmployee_EmailId(superAdminEmail)
                    .orElse(null);

            if (user == null) {
                log.error("❌ Super admin user not found with email: {}", superAdminEmail);
                return false;
            }

            // Send password email with configured password
            sendPasswordEmail(user, superAdminPassword);

            log.info("✅ Password sent successfully to: {}", superAdminEmail);
            return true;

        } catch (Exception e) {
            log.error("❌ Error processing forgot password: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send password reset email
     */
    private void sendPasswordEmail(User user, String password) {
        try {
            String subject = "Password Reset - TTS SMS System";

            String htmlContent = buildPasswordResetEmail(
                    user.getEmployee().getEmployeeName(),
                    user.getUsername(),
                    password
            );

            emailTemplateService.sendHtmlEmail(
                    user.getEmployee().getEmailId(),
                    subject,
                    htmlContent
            );

            log.info("✅ Password reset email sent successfully");
        } catch (Exception e) {
            log.error("❌ Failed to send password reset email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Build HTML email template for password reset
     */
    private String buildPasswordResetEmail(String name, String username, String password) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; background: #f4f4f4; margin: 0; padding: 20px; }" +
                "        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
                "        .header { background: linear-gradient(135deg, #0f3460, #e94560); color: white; padding: 30px; text-align: center; }" +
                "        .header h1 { margin: 0; font-size: 24px; }" +
                "        .content { padding: 30px; }" +
                "        .credentials { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #e94560; }" +
                "        .credentials p { margin: 10px 0; font-size: 16px; }" +
                "        .credentials strong { color: #0f3460; }" +
                "        .warning { background: #fff3cd; padding: 15px; border-radius: 8px; border-left: 4px solid #ffc107; margin: 20px 0; }" +
                "        .footer { background: #f8f9fa; text-align: center; padding: 20px; color: #666; font-size: 14px; border-top: 2px solid #e0e0e0; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='container'>" +
                "        <div class='header'>" +
                "            <h1>🔑 Password Reset Request</h1>" +
                "        </div>" +
                "        <div class='content'>" +
                "            <p>Dear <strong>" + name + "</strong>,</p>" +
                "            <p>You have requested a password reset. Here are your login credentials:</p>" +
                "            <div class='credentials'>" +
                "                <p><strong>Username:</strong> " + username + "</p>" +
                "                <p><strong>Password:</strong> " + password + "</p>" +
                "            </div>" +
                "            <div class='warning'>" +
                "                <p><strong>⚠️ Security Notice:</strong></p>" +
                "                <p>For your security, please change this password immediately after logging in.</p>" +
                "                <p>If you did not request this password reset, please contact the administrator immediately.</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class='footer'>" +
                "            <p><strong>TechnoKraft Training & Solutions</strong></p>" +
                "            <p>Kanchwala Avenue, College Road, Nashik, Maharashtra - 422005</p>" +
                "            <p>Phone: +91 02532312447 | Email: info@tts.net.in</p>" +
                "            <p>© " + java.time.Year.now().getValue() + " TechnoKraft. All rights reserved.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }
}