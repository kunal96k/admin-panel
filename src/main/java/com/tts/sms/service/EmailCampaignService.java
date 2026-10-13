package com.tts.sms.service;

import com.tts.sms.model.EmailCampaign;
import com.tts.sms.model.EmailCampaignRecipient;
import com.tts.sms.model.User;
import com.tts.sms.repository.EmailCampaignRepository;
import com.tts.sms.repository.EmailCampaignRecipientRepository;
import com.tts.sms.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailCampaignService {

    private final EmailCampaignRepository campaignRepository;
    private final EmailCampaignRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    @Value("${spring.mail.username}")
    private String fromEmail;

    // =============================================
    // USER FULL NAME RESOLUTION
    // =============================================
    public String resolveUserFullName(String username) {
        if (username == null || username.trim().isEmpty() || "system".equalsIgnoreCase(username)) {
            return "System Administrator";
        }
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getEmployee() != null && user.getEmployee().getEmployeeName() != null &&
                        !user.getEmployee().getEmployeeName().trim().isEmpty()) {
                    return user.getEmployee().getEmployeeName().trim();
                }
            }
        } catch (Exception e) {
            log.warn("[CAMPAIGN] Could not resolve full name for username {}: {}", username, e.getMessage());
        }
        return username;
    }

    // =============================================
    // CSV & MANUAL EMAIL PARSING
    // =============================================

    /**
     * Parse a CSV file and extract emails from column named "email".
     */
    public Map<String, Object> parseEmailCsv(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        List<String> validEmails = new ArrayList<>();
        List<String> skippedRows = new ArrayList<>();
        List<String> failedRows = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.put("success", false);
                result.put("message", "CSV file is empty");
                return result;
            }

            // Find email column index
            String[] headers = headerLine.split(",");
            int emailColIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().replace("\"", "").toLowerCase();
                if (h.equals("email") || h.equals("emails") || h.equals("email_address") || h.equals("emailaddress")) {
                    emailColIndex = i;
                    break;
                }
            }

            if (emailColIndex == -1) {
                result.put("success", false);
                result.put("message", "No 'email' column found in CSV. Column header must be: email, emails, or email_address");
                return result;
            }

            Set<String> seen = new LinkedHashSet<>();
            String line;
            int rowNum = 1;

            while ((line = reader.readLine()) != null) {
                rowNum++;
                String trimmed = line.trim();

                if (trimmed.isEmpty()) {
                    skippedRows.add("Row " + rowNum + ": empty row");
                    continue;
                }

                totalRows++;
                String[] cols = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                if (emailColIndex >= cols.length) {
                    skippedRows.add("Row " + rowNum + ": email column missing");
                    continue;
                }

                String email = cols[emailColIndex].trim().replace("\"", "");

                if (email.isEmpty()) {
                    skippedRows.add("Row " + rowNum + ": empty email cell");
                    continue;
                }

                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    failedRows.add("Row " + rowNum + ": invalid email '" + email + "'");
                    continue;
                }

                if (seen.contains(email.toLowerCase())) {
                    skippedRows.add("Row " + rowNum + ": duplicate email '" + email + "'");
                    continue;
                }

                seen.add(email.toLowerCase());
                validEmails.add(email);
            }

            result.put("success", true);
            result.put("emails", validEmails);
            result.put("totalRows", totalRows);
            result.put("imported", validEmails.size());
            result.put("skipped", skippedRows.size());
            result.put("failed", failedRows.size());
            result.put("skippedDetails", skippedRows);
            result.put("failedDetails", failedRows);

        } catch (Exception e) {
            log.error("[CAMPAIGN] CSV parse error: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error parsing CSV: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse and validate manual emails entered by the user (supports single email, comma/semicolon/newline-separated lists).
     */
    public Map<String, Object> parseManualEmails(String rawInput, List<String> existingEmails) {
        Map<String, Object> result = new HashMap<>();
        if (rawInput == null || rawInput.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "Please enter at least one email address");
            return result;
        }

        Set<String> existingSet = new HashSet<>();
        if (existingEmails != null) {
            for (String e : existingEmails) {
                if (e != null) existingSet.add(e.trim().toLowerCase());
            }
        }

        String[] tokens = rawInput.split("[,;\\n\\r\\t]+");
        List<String> validList = new ArrayList<>();
        List<String> duplicateList = new ArrayList<>();
        List<String> invalidList = new ArrayList<>();
        Set<String> processedThisBatch = new HashSet<>();

        for (String raw : tokens) {
            String email = raw.trim();
            if (email.isEmpty()) continue;

            if (!EMAIL_PATTERN.matcher(email).matches()) {
                invalidList.add(email);
                continue;
            }

            String lower = email.toLowerCase();
            if (existingSet.contains(lower) || processedThisBatch.contains(lower)) {
                duplicateList.add(email);
                continue;
            }

            processedThisBatch.add(lower);
            validList.add(email);
        }

        result.put("success", true);
        result.put("validEmails", validList);
        result.put("validCount", validList.size());
        result.put("duplicateEmails", duplicateList);
        result.put("duplicateCount", duplicateList.size());
        result.put("invalidEmails", invalidList);
        result.put("invalidCount", invalidList.size());
        result.put("totalEntered", tokens.length);

        if (validList.isEmpty() && (!duplicateList.isEmpty() || !invalidList.isEmpty())) {
            StringBuilder msg = new StringBuilder("No new valid emails added.");
            if (!duplicateList.isEmpty()) msg.append(" (").append(duplicateList.size()).append(" duplicate(s))");
            if (!invalidList.isEmpty()) msg.append(" (").append(invalidList.size()).append(" invalid format(s))");
            result.put("message", msg.toString());
        } else {
            result.put("message", "Successfully added " + validList.size() + " email(s).");
        }

        return result;
    }

    // =============================================
    // CAMPAIGN CRUD
    // =============================================

    @Transactional
    public EmailCampaign createCampaign(EmailCampaign campaign, List<String> emails) {
        if (campaign.getCreatedByName() == null || campaign.getCreatedByName().trim().isEmpty()) {
            campaign.setCreatedByName(resolveUserFullName(campaign.getCreatedBy()));
        }

        EmailCampaign saved = campaignRepository.save(campaign);

        List<EmailCampaignRecipient> recipients = new ArrayList<>();
        for (String email : emails) {
            recipients.add(EmailCampaignRecipient.builder()
                    .campaign(saved)
                    .email(email.trim())
                    .status("PENDING")
                    .attemptCount(0)
                    .build());
        }
        recipientRepository.saveAll(recipients);

        saved.setTotalRecipients(emails.size());
        return campaignRepository.save(saved);
    }

    public List<EmailCampaign> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<EmailCampaign> getCampaign(Long id) {
        return campaignRepository.findById(id);
    }

    public List<EmailCampaignRecipient> getCampaignRecipients(Long campaignId) {
        return recipientRepository.findByCampaignId(campaignId);
    }

    // =============================================
    // SERVER-SIDE PAGINATED QUERIES
    // =============================================

    public Map<String, Object> getPagedCampaigns(int page, int size, String search, String status, String sortBy, String sortDir) {
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy != null && !sortBy.trim().isEmpty() ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);

        Page<EmailCampaign> paged = campaignRepository.findWithFilters(
                (search != null && !search.trim().isEmpty()) ? search.trim() : null,
                (status != null && !status.trim().isEmpty()) ? status.trim() : null,
                pageable
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (EmailCampaign c : paged.getContent()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id",              c.getId());
            map.put("campaignName",    c.getCampaignName());
            map.put("emailSubject",    c.getEmailSubject());
            map.put("conductMode",     c.getConductMode());
            map.put("onlinePlatform",  c.getOnlinePlatform());
            map.put("hiringRole",      c.getHiringRole());
            map.put("opportunityType", c.getOpportunityType());
            map.put("status",          c.getStatus());
            map.put("totalRecipients", c.getTotalRecipients());
            map.put("sentCount",       c.getSentCount());
            map.put("failedCount",     c.getFailedCount());
            map.put("skippedCount",    c.getSkippedCount());
            map.put("createdBy",       c.getCreatedBy());
            map.put("createdByName",   c.getCreatedByName() != null ? c.getCreatedByName() : resolveUserFullName(c.getCreatedBy()));
            map.put("createdAt",       c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            map.put("sentAt",          c.getSentAt() != null ? c.getSentAt().toString() : "");
            map.put("scheduledAt",     c.getScheduledAt() != null ? c.getScheduledAt().toString() : "");
            list.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("currentPage", paged.getNumber());
        response.put("totalPages", paged.getTotalPages());
        response.put("totalElements", paged.getTotalElements());
        response.put("pageSize", paged.getSize());
        response.put("hasNext", paged.hasNext());
        response.put("hasPrevious", paged.hasPrevious());
        return response;
    }

    public Map<String, Object> getPagedRecipients(Long campaignId, int page, int size, String search, String status) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.ASC, "id"));

        Page<EmailCampaignRecipient> paged = recipientRepository.findByCampaignIdWithFilters(
                campaignId,
                (search != null && !search.trim().isEmpty()) ? search.trim() : null,
                (status != null && !status.trim().isEmpty()) ? status.trim() : null,
                pageable
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (EmailCampaignRecipient r : paged.getContent()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id",            r.getId());
            map.put("email",         r.getEmail());
            map.put("status",        r.getStatus());
            map.put("attemptCount",  r.getAttemptCount() != null ? r.getAttemptCount() : 0);
            map.put("failureReason", r.getFailureReason());
            map.put("sentAt",        r.getSentAt() != null ? r.getSentAt().toString() : "");
            map.put("createdAt",     r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            list.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("currentPage", paged.getNumber());
        response.put("totalPages", paged.getTotalPages());
        response.put("totalElements", paged.getTotalElements());
        response.put("pageSize", paged.getSize());
        return response;
    }

    // =============================================
    // EMAIL SENDING & SMTP ERROR EXTRACTION
    // =============================================

    @Async("emailTaskExecutor")
    public void sendCampaignAsync(Long campaignId) {
        Optional<EmailCampaign> opt = campaignRepository.findById(campaignId);
        if (opt.isEmpty()) {
            log.error("[CAMPAIGN] Campaign {} not found", campaignId);
            return;
        }
        doSendCampaign(opt.get());
    }

    /**
     * Retries sending to all FAILED recipients for a campaign.
     */
    @Async("emailTaskExecutor")
    public void retryFailedCampaignAsync(Long campaignId) {
        Optional<EmailCampaign> opt = campaignRepository.findById(campaignId);
        if (opt.isEmpty()) {
            log.error("[CAMPAIGN] Campaign {} not found for retry", campaignId);
            return;
        }
        EmailCampaign campaign = opt.get();
        List<EmailCampaignRecipient> failedRecipients = recipientRepository.findByCampaignIdAndStatus(campaignId, "FAILED");
        if (failedRecipients.isEmpty()) {
            log.info("[CAMPAIGN] No failed recipients to retry for campaign {}", campaignId);
            return;
        }

        for (EmailCampaignRecipient r : failedRecipients) {
            r.setStatus("PENDING");
        }
        recipientRepository.saveAll(failedRecipients);

        campaign.setStatus("SENDING");
        campaignRepository.save(campaign);

        doSendCampaign(campaign);
    }

    public void doSendCampaign(EmailCampaign campaign) {
        log.info("[CAMPAIGN] Starting send for campaign: {} ({})", campaign.getCampaignName(), campaign.getId());

        campaign.setStatus("SENDING");
        campaignRepository.save(campaign);

        List<EmailCampaignRecipient> pendingRecipients = recipientRepository
                .findByCampaignIdAndStatus(campaign.getId(), "PENDING");

        for (EmailCampaignRecipient recipient : pendingRecipients) {
            recipient.setAttemptCount((recipient.getAttemptCount() != null ? recipient.getAttemptCount() : 0) + 1);
            try {
                String html = buildHiringEmailHtml(campaign, recipient.getEmail());
                sendHtmlEmail(recipient.getEmail(), campaign.getEmailSubject(), html);

                recipient.setStatus("SENT");
                recipient.setFailureReason(null);
                recipient.setSentAt(LocalDateTime.now());
                log.info("[CAMPAIGN] Email successfully sent to: {}", recipient.getEmail());

            } catch (Exception e) {
                String detailedError = extractDetailedSmtpError(e);
                log.error("[CAMPAIGN] Failed to send to {}: {}", recipient.getEmail(), detailedError);
                recipient.setStatus("FAILED");
                recipient.setFailureReason(detailedError);
            }

            recipientRepository.save(recipient);

            // Small delay to avoid SMTP rate limits
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        long sentCount = recipientRepository.countByCampaignIdAndStatus(campaign.getId(), "SENT");
        long failedCount = recipientRepository.countByCampaignIdAndStatus(campaign.getId(), "FAILED");
        long skippedCount = recipientRepository.countByCampaignIdAndStatus(campaign.getId(), "SKIPPED");
        long totalCount = (campaign.getTotalRecipients() != null && campaign.getTotalRecipients() > 0)
                ? campaign.getTotalRecipients() : (sentCount + failedCount + skippedCount);

        campaign.setSentCount((int) sentCount);
        campaign.setFailedCount((int) failedCount);
        campaign.setSkippedCount((int) skippedCount);
        campaign.setTotalRecipients((int) totalCount);

        if (totalCount == 0) {
            campaign.setStatus("SENT");
        } else if (failedCount == 0) {
            campaign.setStatus("SENT");
        } else if (sentCount > 0 && failedCount > 0) {
            campaign.setStatus("PARTIALLY_FAILED");
        } else if (sentCount == 0 && failedCount > 0) {
            campaign.setStatus("FAILED");
        } else {
            campaign.setStatus("SENT");
        }

        campaign.setSentAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        log.info("[CAMPAIGN] Finished campaign {}. Total: {}, Sent: {}, Failed: {}, Status: {}",
                campaign.getId(), totalCount, sentCount, failedCount, campaign.getStatus());
    }

    /**
     * Extracts clear, precise, and human-understandable SMTP error reasons.
     */
    public String extractDetailedSmtpError(Throwable t) {
        if (t == null) return "Unknown delivery error";

        Throwable current = t;
        String authError = null;
        String connError = null;
        String timeoutError = null;
        String smtpSendError = null;
        String rawMsg = null;

        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && rawMsg == null) rawMsg = msg;

            if (current instanceof jakarta.mail.AuthenticationFailedException ||
                current instanceof MailAuthenticationException ||
                (msg != null && (msg.contains("535") || msg.toLowerCase().contains("authentication failed") || msg.toLowerCase().contains("badcredentials")))) {
                authError = "SMTP Authentication Failed (535): Invalid mail credentials or app password";
            }

            if (current instanceof SocketTimeoutException ||
                (msg != null && (msg.toLowerCase().contains("timed out") || msg.toLowerCase().contains("timeout")))) {
                timeoutError = "SMTP Timeout: Connection to mail server timed out";
            }

            if (current instanceof ConnectException ||
                (msg != null && (msg.toLowerCase().contains("connection refused") || msg.toLowerCase().contains("could not connect")))) {
                connError = "SMTP Connection Failed: Unable to reach mail server";
            }

            if (msg != null && (msg.contains("550") || msg.contains("553") || msg.toLowerCase().contains("user unknown") || msg.toLowerCase().contains("mailbox unavailable"))) {
                smtpSendError = "Mail Server Rejected (550): Recipient address does not exist or blocked";
            }

            if (current instanceof MailSendException) {
                MailSendException mse = (MailSendException) current;
                if (mse.getMessageExceptions() != null && mse.getMessageExceptions().length > 0) {
                    for (Exception subEx : mse.getMessageExceptions()) {
                        String subResult = extractDetailedSmtpError(subEx);
                        if (subResult != null && !subResult.startsWith("Unknown")) return subResult;
                    }
                }
            }

            current = current.getCause();
        }

        if (authError != null) return authError;
        if (connError != null) return connError;
        if (timeoutError != null) return timeoutError;
        if (smtpSendError != null) return smtpSendError;

        if (rawMsg != null && !rawMsg.trim().isEmpty()) {
            if (rawMsg.length() > 250) {
                return rawMsg.substring(0, 250) + "...";
            }
            return rawMsg;
        }

        return t.getClass().getSimpleName();
    }

    private String buildHiringEmailHtml(EmailCampaign c, String recipientEmail) {
        Context ctx = new Context();
        ctx.setVariable("campaignName",    c.getCampaignName());
        ctx.setVariable("hiringRole",      c.getHiringRole());
        ctx.setVariable("opportunityType", c.getOpportunityType());
        ctx.setVariable("conductMode",     c.getConductMode());
        ctx.setVariable("onlinePlatform",  c.getOnlinePlatform());
        ctx.setVariable("meetingLink",     c.getMeetingLink());
        ctx.setVariable("venueAddress",    c.getVenueAddress());
        ctx.setVariable("eventDate",       c.getEventDate());
        ctx.setVariable("eventTime",       c.getEventTime());
        ctx.setVariable("customNotes",     c.getCustomNotes());
        ctx.setVariable("recipientEmail",  recipientEmail);
        ctx.setVariable("year",            java.time.Year.now().getValue());
        return templateEngine.process("email/hiring-campaign", ctx);
    }

    private void sendHtmlEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
    }

    // =============================================
    // CSV EXPORT OF AUDIT LOG
    // =============================================
    public String exportCampaignRecipientsCsv(Long campaignId) {
        Optional<EmailCampaign> campaignOpt = campaignRepository.findById(campaignId);
        if (campaignOpt.isEmpty()) return "";

        EmailCampaign c = campaignOpt.get();
        List<EmailCampaignRecipient> recipients = recipientRepository.findByCampaignId(campaignId);

        StringWriter sw = new StringWriter();
        // UTF-8 BOM
        sw.write('\ufeff');
        sw.write("ID,Recipient Email,Status,Attempt Count,Failure Reason,Sent At,Campaign Name,Subject,Role,Sender Name\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String senderName = c.getCreatedByName() != null ? c.getCreatedByName() : resolveUserFullName(c.getCreatedBy());

        for (EmailCampaignRecipient r : recipients) {
            String sentAtStr = r.getSentAt() != null ? r.getSentAt().format(dtf) : "";
            String reasonStr = r.getFailureReason() != null ? r.getFailureReason().replace("\"", "\"\"") : "";
            sw.write(String.format("%d,\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    r.getId(),
                    r.getEmail(),
                    r.getStatus(),
                    r.getAttemptCount() != null ? r.getAttemptCount() : 0,
                    reasonStr,
                    sentAtStr,
                    c.getCampaignName().replace("\"", "\"\""),
                    c.getEmailSubject().replace("\"", "\"\""),
                    c.getHiringRole().replace("\"", "\"\""),
                    senderName.replace("\"", "\"\"")
            ));
        }

        return sw.toString();
    }

    // =============================================
    // SCHEDULER TRIGGER (called by scheduler)
    // =============================================
    public void processScheduledCampaigns() {
        List<EmailCampaign> due = campaignRepository.findDueScheduledCampaigns(LocalDateTime.now());
        log.info("[CAMPAIGN-SCHEDULER] Found {} due campaigns", due.size());
        for (EmailCampaign campaign : due) {
            doSendCampaign(campaign);
        }
    }
}
