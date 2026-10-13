package com.tts.sms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tts.sms.dto.WhatsAppSendResult;
import com.tts.sms.model.Enquiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service to integrate directly with Meta WhatsApp Business Cloud API.
 * Handles exactly TWO use cases:
 * 1. Course Information (Template: crm_course_information)
 * 2. Fee Reminder (Template: crm_fee_reminder_utility)
 *
 * Strict Rules:
 * - Exact template names only (NEVER crm_fee_reminder).
 * - Exact variable count and order preserved.
 * - Single consolidated PDF document URL if real brochures exist.
 * - Never logs or exposes access tokens.
 */
@Service
@Slf4j
public class WhatsAppService {

    @Value("${app.whatsapp.enabled:true}")
    private boolean enabled;

    @Value("${app.whatsapp.api-version:v21.0}")
    private String apiVersion;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token:}")
    private String accessToken;

    @Value("${app.whatsapp.course-template-name:crm_course_information}")
    private String courseTemplateName;

    @Value("${app.whatsapp.course-template-language:en_US}")
    private String courseTemplateLanguage;

    @Value("${app.whatsapp.fee-template-name:crm_fee_reminder_utility}")
    private String feeTemplateName;

    @Value("${app.whatsapp.fee-template-language:en_US}")
    private String feeTemplateLanguage;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigurationService systemConfigurationService;
    private final WhatsAppAuditLogService auditLogService;

    public WhatsAppService(
            @Value("${app.whatsapp.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${app.whatsapp.read-timeout-ms:10000}") int readTimeout,
            ObjectMapper objectMapper,
            @org.springframework.context.annotation.Lazy SystemConfigurationService systemConfigurationService,
            @org.springframework.context.annotation.Lazy WhatsAppAuditLogService auditLogService) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.systemConfigurationService = systemConfigurationService;
        this.auditLogService = auditLogService;
    }

    /**
     * Send Course Information WhatsApp template message to student from an Enquiry record.
     * Meta Template: crm_course_information
     * Variables:
     * 1: student_name
     * 2: course_list
     * Header: Single Document (if documentUrl provided)
     *
     * @param enquiry Enquiry details
     * @param documentUrl Optional single public HTTPS document URL
     * @return WhatsAppSendResult with delivery status
     */
    public WhatsAppSendResult sendCourseInformation(Enquiry enquiry, String documentUrl) {
        return sendCourseInformation(enquiry, documentUrl, null);
    }

    public WhatsAppSendResult sendCourseInformation(Enquiry enquiry, String documentUrl, com.tts.sms.dto.WhatsAppAuditContext auditContext) {
        if (enquiry == null) {
            return WhatsAppSendResult.failure("Enquiry data is missing", null);
        }

        String rawMobile = enquiry.getMobile();
        String studentName = enquiry.getDisplayName();
        if (studentName == null || studentName.isBlank()) {
            studentName = "Student";
        }

        String courseList = "our courses";
        if (enquiry.getCourses() != null && !enquiry.getCourses().isEmpty()) {
            List<String> validCourses = enquiry.getCourses().stream()
                    .filter(c -> c != null && !c.isBlank() && !"N/A".equalsIgnoreCase(c.trim()) && !"Not Specified".equalsIgnoreCase(c.trim()))
                    .toList();
            if (!validCourses.isEmpty()) {
                courseList = String.join(", ", validCourses);
            }
        } else if (enquiry.getPackageName() != null && !enquiry.getPackageName().isBlank()) {
            courseList = enquiry.getPackageName().trim();
        }

        return sendCourseInformationDirect(rawMobile, studentName, courseList, documentUrl, enquiry.getId(), null, auditContext);
    }

    /**
     * Directly send Course Information WhatsApp template message.
     * Preserves exact Meta variable mapping:
     * 1: student_name
     * 2: course_list
     */
    public WhatsAppSendResult sendCourseInformationDirect(
            String recipientMobile,
            String studentName,
            String courseList,
            String documentUrl) {
        return sendCourseInformationDirect(recipientMobile, studentName, courseList, documentUrl, null, null, null);
    }

    public WhatsAppSendResult sendCourseInformationDirect(
            String rawMobile,
            String studentName,
            String courseList,
            String documentUrl,
            Long referenceId) {
        return sendCourseInformationDirect(rawMobile, studentName, courseList, documentUrl, referenceId, null, null);
    }

    public WhatsAppSendResult sendCourseInformationDirect(
            String rawMobile,
            String studentName,
            String courseList,
            String documentUrl,
            Long referenceId,
            String customDocFileName) {
        return sendCourseInformationDirect(rawMobile, studentName, courseList, documentUrl, referenceId, customDocFileName, null);
    }

    public WhatsAppSendResult sendCourseInformationDirect(
            String rawMobile,
            String studentName,
            String courseList,
            String documentUrl,
            Long referenceId,
            String customDocFileName,
            com.tts.sms.dto.WhatsAppAuditContext auditContext) {

        String normalizedMobile = normalizeIndianMobileNumber(rawMobile);
        String maskedMobile = maskPhoneNumber(rawMobile);

        if (auditContext == null) {
            auditContext = com.tts.sms.dto.WhatsAppAuditContext.builder()
                    .moduleType("COURSE_INFORMATION")
                    .templateName(courseTemplateName)
                    .billingCategory("MARKETING")
                    .triggerType("MANUAL")
                    .referenceId(referenceId)
                    .studentName(studentName)
                    .recipientMobile(rawMobile)
                    .courseName(courseList)
                    .build();
        } else {
            if (auditContext.getModuleType() == null) auditContext.setModuleType("COURSE_INFORMATION");
            if (auditContext.getTemplateName() == null) auditContext.setTemplateName(courseTemplateName);
            if (auditContext.getBillingCategory() == null) auditContext.setBillingCategory("MARKETING");
            if (auditContext.getReferenceId() == null) auditContext.setReferenceId(referenceId);
            if (auditContext.getStudentName() == null) auditContext.setStudentName(studentName);
            if (auditContext.getRecipientMobile() == null) auditContext.setRecipientMobile(rawMobile);
            if (auditContext.getCourseName() == null) auditContext.setCourseName(courseList);
        }

        if (normalizedMobile == null) {
            log.warn("[WHATSAPP] Invalid recipient mobile number: {}", maskedMobile);
            WhatsAppSendResult fail = WhatsAppSendResult.failure("Invalid recipient mobile number. Must be a valid 10-digit Indian number.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, fail, null, null, "Validation failed: Invalid mobile number");
            }
            return fail;
        }

        if (!enabled) {
            log.info("[WHATSAPP] WhatsApp integration is disabled. Skipping message to: {}", maskedMobile);
            WhatsAppSendResult fail = WhatsAppSendResult.failure("WhatsApp integration is currently disabled in configuration.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, fail, null, null, "Disabled in application configuration");
            }
            return fail;
        }

        if (systemConfigurationService != null && !systemConfigurationService.isCourseInfoWhatsAppEnabled()) {
            log.warn("[WHATSAPP] Course information WhatsApp API is disabled in System Configuration by SUPER_ADMIN.");
            WhatsAppSendResult blocked = WhatsAppSendResult.failure(
                    "This feature is temporarily disabled. If you have any questions or need assistance, please contact the administrator.",
                    maskedMobile
            );
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, blocked, null, null, "Blocked by System Configuration toggle");
            }
            return blocked;
        }

        if (phoneNumberId == null || phoneNumberId.isBlank() || accessToken == null || accessToken.isBlank()) {
            log.warn("[WHATSAPP] Meta WhatsApp credentials (Phone Number ID or Access Token) are not configured.");
            WhatsAppSendResult fail = WhatsAppSendResult.failure("WhatsApp API credentials are not configured on server.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, fail, null, null, "Missing API credentials");
            }
            return fail;
        }

        String finalStudentName = (studentName != null && !studentName.isBlank()) ? studentName.trim() : "Student";
        String finalCourseList = (courseList != null && !courseList.isBlank()) ? courseList.trim() : "our courses";

        List<Map<String, Object>> components = new ArrayList<>();

        // Optional Document Header (single document only, per Meta Cloud API spec)
        if (documentUrl != null && !documentUrl.isBlank()) {
            Map<String, Object> headerComponent = new LinkedHashMap<>();
            headerComponent.put("type", "header");

            Map<String, Object> docParam = new LinkedHashMap<>();
            docParam.put("type", "document");

            boolean isZip = documentUrl.toLowerCase().endsWith(".zip");
            String docFileName;

            if (customDocFileName != null && !customDocFileName.isBlank()) {
                // If custom file name was explicitly provided (e.g. from manual upload), use as is!
                docFileName = customDocFileName.trim();
            } else if (isZip) {
                // For zip files (manually uploaded or archives), preserve the original file name from URL!
                String urlName = documentUrl.trim();
                if (urlName.contains("?")) {
                    urlName = urlName.substring(0, urlName.indexOf("?"));
                }
                String namePart = urlName.substring(urlName.lastIndexOf('/') + 1);
                // Strip the timestamp prefix (e.g. 1791881839166_files.zip -> files.zip)
                if (namePart.matches("^\\d+_.*")) {
                    namePart = namePart.replaceFirst("^\\d+_", "");
                }
                docFileName = !namePart.isBlank() ? namePart : "files.zip";
            } else {
                // For Master/Consolidated course brochures, format dynamically by 1st course name
                String primaryCourse = finalCourseList;
                if (primaryCourse != null && primaryCourse.contains(",")) {
                    primaryCourse = primaryCourse.split(",")[0].trim();
                }
                docFileName = CourseService.formatBrochureFileName(primaryCourse, ".pdf");
            }

            auditContext.setAttachmentName(docFileName);

            Map<String, String> docObj = new LinkedHashMap<>();
            docObj.put("link", documentUrl.trim());
            docObj.put("filename", docFileName);
            docParam.put("document", docObj);

            headerComponent.put("parameters", List.of(docParam));
            components.add(headerComponent);
        }

        // Body component with EXACTLY 2 parameters per Meta template crm_course_information:
        // 1: student_name
        // 2: course_list
        Map<String, Object> bodyComponent = new LinkedHashMap<>();
        bodyComponent.put("type", "body");

        List<Map<String, Object>> bodyParams = new ArrayList<>();
        bodyParams.add(Map.of("type", "text", "text", finalStudentName));
        bodyParams.add(Map.of("type", "text", "text", finalCourseList));
        bodyComponent.put("parameters", bodyParams);
        components.add(bodyComponent);

        return executeMetaTemplateCall(
                "Course Information",
                referenceId,
                normalizedMobile,
                maskedMobile,
                courseTemplateName,
                courseTemplateLanguage,
                components,
                auditContext
        );
    }

    /**
     * Send Fee Due Reminder WhatsApp template message.
     * Meta Template: crm_fee_reminder_utility
     *
     * Exact Variable Order:
     * 1: student_name
     * 2: course_name
     * 3: payment_due_status
     * 4: registration_number
     * 5: installment
     * 6: installment_amount
     * 7: due_date
     * 8: outstanding_balance
     *
     * Header: NONE
     */
    public WhatsAppSendResult sendFeeReminder(
            String recipientMobile,
            String studentName,
            String courseName,
            String paymentDueStatus,
            String registrationNumber,
            String installment,
            String installmentAmount,
            String dueDate,
            String outstandingBalance) {
        return sendFeeReminder(recipientMobile, studentName, courseName, paymentDueStatus, registrationNumber,
                installment, installmentAmount, dueDate, outstandingBalance, null);
    }

    public WhatsAppSendResult sendFeeReminder(
            String recipientMobile,
            String studentName,
            String courseName,
            String paymentDueStatus,
            String registrationNumber,
            String installment,
            String installmentAmount,
            String dueDate,
            String outstandingBalance,
            com.tts.sms.dto.WhatsAppAuditContext auditContext) {

        String normalizedMobile = normalizeIndianMobileNumber(recipientMobile);
        String maskedMobile = maskPhoneNumber(recipientMobile);

        if (auditContext == null) {
            auditContext = com.tts.sms.dto.WhatsAppAuditContext.builder()
                    .moduleType("FEE_REMINDER")
                    .templateName(feeTemplateName)
                    .billingCategory("UTILITY")
                    .triggerType("AUTOMATED_SCHEDULER")
                    .referenceNumber(registrationNumber)
                    .studentName(studentName)
                    .recipientMobile(recipientMobile)
                    .courseName(courseName)
                    .build();
        } else {
            if (auditContext.getModuleType() == null) auditContext.setModuleType("FEE_REMINDER");
            if (auditContext.getTemplateName() == null) auditContext.setTemplateName(feeTemplateName);
            if (auditContext.getBillingCategory() == null) auditContext.setBillingCategory("UTILITY");
            if (auditContext.getReferenceNumber() == null) auditContext.setReferenceNumber(registrationNumber);
            if (auditContext.getStudentName() == null) auditContext.setStudentName(studentName);
            if (auditContext.getRecipientMobile() == null) auditContext.setRecipientMobile(recipientMobile);
            if (auditContext.getCourseName() == null) auditContext.setCourseName(courseName);
        }

        if (normalizedMobile == null) {
            log.warn("[WHATSAPP] Invalid fee reminder recipient mobile: {}", maskedMobile);
            WhatsAppSendResult fail = WhatsAppSendResult.failure("Invalid recipient mobile number for fee reminder.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, fail, null, null, "Validation failed: Invalid mobile number");
            }
            return fail;
        }

        if (!enabled) {
            log.info("[WHATSAPP] WhatsApp integration is disabled. Skipping fee reminder to: {}", maskedMobile);
            WhatsAppSendResult fail = WhatsAppSendResult.failure("WhatsApp integration is currently disabled in configuration.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, fail, null, null, "Disabled in application configuration");
            }
            return fail;
        }

        if (systemConfigurationService != null && !systemConfigurationService.isFeeReminderWhatsAppEnabled()) {
            log.warn("[WHATSAPP] Fee reminder WhatsApp API is disabled in System Configuration by SUPER_ADMIN.");
            WhatsAppSendResult blocked = WhatsAppSendResult.failure(
                    "This feature is temporarily disabled. If you have any questions or need assistance, please contact the administrator.",
                    maskedMobile
            );
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, blocked, null, null, "Blocked by System Configuration toggle");
            }
            return blocked;
        }

        if (phoneNumberId == null || phoneNumberId.isBlank() || accessToken == null || accessToken.isBlank()) {
            log.warn("[WHATSAPP] Meta WhatsApp credentials are not configured. Skipping fee reminder.");
            WhatsAppSendResult fail = WhatsAppSendResult.failure("WhatsApp API credentials are not configured on server.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, fail, null, null, "Missing API credentials");
            }
            return fail;
        }

        // Body component with EXACTLY 8 parameters in order:
        // 1: student_name
        // 2: course_name
        // 3: payment_due_status
        // 4: registration_number
        // 5: installment
        // 6: installment_amount
        // 7: due_date
        // 8: outstanding_balance
        Map<String, Object> bodyComponent = new LinkedHashMap<>();
        bodyComponent.put("type", "body");

        List<Map<String, Object>> bodyParams = new ArrayList<>();
        bodyParams.add(Map.of("type", "text", "text", studentName != null ? studentName.trim() : "Student"));
        bodyParams.add(Map.of("type", "text", "text", courseName != null ? courseName.trim() : "-"));
        bodyParams.add(Map.of("type", "text", "text", paymentDueStatus != null ? paymentDueStatus.trim() : "is due soon."));
        bodyParams.add(Map.of("type", "text", "text", registrationNumber != null ? registrationNumber.trim() : "-"));
        bodyParams.add(Map.of("type", "text", "text", installment != null ? installment.trim() : "Installment"));
        bodyParams.add(Map.of("type", "text", "text", installmentAmount != null ? installmentAmount.trim() : "0.00"));
        bodyParams.add(Map.of("type", "text", "text", dueDate != null ? dueDate.trim() : "-"));
        bodyParams.add(Map.of("type", "text", "text", outstandingBalance != null ? outstandingBalance.trim() : "0.00"));
        bodyComponent.put("parameters", bodyParams);

        List<Map<String, Object>> components = List.of(bodyComponent);

        return executeMetaTemplateCall(
                "Fee Reminder",
                null,
                normalizedMobile,
                maskedMobile,
                feeTemplateName,
                feeTemplateLanguage,
                components,
                auditContext
        );
    }

    /**
     * Executes the Meta WhatsApp Cloud API POST request securely and records audit log.
     */
    private WhatsAppSendResult executeMetaTemplateCall(
            String useCase,
            Long referenceId,
            String normalizedMobile,
            String maskedMobile,
            String templateName,
            String templateLanguage,
            List<Map<String, Object>> components,
            com.tts.sms.dto.WhatsAppAuditContext auditContext) {

        String url = String.format("https://graph.facebook.com/%s/%s/messages", apiVersion, phoneNumberId);

        Map<String, Object> templateObj = new LinkedHashMap<>();
        templateObj.put("name", templateName);
        templateObj.put("language", Map.of("code", templateLanguage));
        if (components != null && !components.isEmpty()) {
            templateObj.put("components", components);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalizedMobile);
        payload.put("type", "template");
        payload.put("template", templateObj);

        String requestJson = null;
        try {
            requestJson = objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {}

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken.trim());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        log.info("[WHATSAPP] Sending {} template '{}' ({}) to recipient {} (ref id: {})",
                useCase, templateName, templateLanguage, maskedMobile, referenceId != null ? referenceId : "N/A");

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            String responseBody = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                String messageId = null;
                if (root.has("messages") && root.get("messages").isArray() && !root.get("messages").isEmpty()) {
                    messageId = root.get("messages").get(0).path("id").asText();
                }

                log.info("[WHATSAPP] Successfully sent {} to {}. Meta message ID: {}", useCase, maskedMobile, messageId);
                WhatsAppSendResult result = WhatsAppSendResult.success(messageId, maskedMobile);
                if (auditLogService != null) {
                    auditLogService.recordLog(auditContext, result, response.getStatusCode().value(), requestJson, responseBody);
                }
                return result;
            } else {
                log.warn("[WHATSAPP] Meta API returned unexpected status {} for ref id: {}", response.getStatusCode(), referenceId);
                WhatsAppSendResult result = WhatsAppSendResult.failure("WhatsApp server responded with status " + response.getStatusCode(), maskedMobile);
                if (auditLogService != null) {
                    auditLogService.recordLog(auditContext, result, response.getStatusCode().value(), requestJson, responseBody);
                }
                return result;
            }

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            String rawErrResponse = ex.getResponseBodyAsString();
            String errorMessage = parseMetaErrorMessage(rawErrResponse, ex.getStatusCode().toString());
            log.error("[WHATSAPP] Meta API error for {} (ref id: {}, recipient: {}): Status={}, Error={}",
                    useCase, referenceId, maskedMobile, ex.getStatusCode(), errorMessage);
            WhatsAppSendResult result = WhatsAppSendResult.failure(errorMessage, maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, result, ex.getStatusCode().value(), requestJson, rawErrResponse);
            }
            return result;

        } catch (ResourceAccessException ex) {
            log.error("[WHATSAPP] Timeout/Network error connecting to Meta API for {} (ref id: {}): {}",
                    useCase, referenceId, ex.getMessage());
            WhatsAppSendResult result = WhatsAppSendResult.failure("Connection to WhatsApp service timed out. Please try again.", maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, result, 504, requestJson, ex.getMessage());
            }
            return result;

        } catch (Exception ex) {
            log.error("[WHATSAPP] General error sending WhatsApp for {} (ref id: {}): {}",
                    useCase, referenceId, ex.getMessage(), ex);
            WhatsAppSendResult result = WhatsAppSendResult.failure("Failed to send WhatsApp message: " + ex.getMessage(), maskedMobile);
            if (auditLogService != null) {
                auditLogService.recordLog(auditContext, result, 500, requestJson, ex.getMessage());
            }
            return result;
        }
    }

    /**
     * Extracts a user-friendly error string from Meta's JSON error response without exposing sensitive data.
     */
    private String parseMetaErrorMessage(String responseBody, String defaultStatus) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("error")) {
                    JsonNode errorNode = root.get("error");
                    String msg = errorNode.path("message").asText();
                    String details = errorNode.path("error_user_msg").asText();
                    if (!details.isBlank()) {
                        return details;
                    }
                    if (!msg.isBlank()) {
                        return msg;
                    }
                }
            } catch (Exception ignored) {
                // Return fallback
            }
        }
        return "Meta WhatsApp API error (" + defaultStatus + ")";
    }

    /**
     * Normalizes an Indian mobile number into Meta's international format (e.g. 919876543210).
     */
    public static String normalizeIndianMobileNumber(String rawMobile) {
        if (rawMobile == null) {
            return null;
        }
        String digits = rawMobile.replaceAll("[^0-9]", "");

        if (digits.length() == 10 && digits.matches("^[6-9]\\d{9}$")) {
            return "91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91") && digits.substring(2).matches("^[6-9]\\d{9}$")) {
            return digits;
        }
        return null;
    }

    /**
     * Safely masks a mobile number for logs and user confirmation (e.g. 98******10).
     */
    public static String maskPhoneNumber(String rawMobile) {
        if (rawMobile == null || rawMobile.isBlank()) {
            return "N/A";
        }
        String clean = rawMobile.trim();
        if (clean.length() >= 10) {
            String last4 = clean.substring(clean.length() - 4);
            String first2 = clean.substring(0, 2);
            return first2 + "******" + last4;
        }
        return "******" + (clean.length() >= 2 ? clean.substring(clean.length() - 2) : "");
    }
}
