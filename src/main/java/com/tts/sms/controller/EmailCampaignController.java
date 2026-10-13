package com.tts.sms.controller;

import com.tts.sms.model.EmailCampaign;
import com.tts.sms.model.EmailCampaignRecipient;
import com.tts.sms.model.EmailTemplate;
import com.tts.sms.repository.EmailTemplateRepository;
import com.tts.sms.service.EmailCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/email-campaign")
@RequiredArgsConstructor
@Slf4j
public class EmailCampaignController {

    private final EmailCampaignService emailCampaignService;
    private final EmailTemplateRepository emailTemplateRepository;

    // =============================================
    // CSV & MANUAL EMAIL PARSING
    // =============================================

    @PostMapping("/import-csv")
    public ResponseEntity<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File is empty"));
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only CSV files are allowed"));
        }

        Map<String, Object> result = emailCampaignService.parseEmailCsv(file);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parse-manual-emails")
    public ResponseEntity<Map<String, Object>> parseManualEmails(@RequestBody Map<String, Object> request) {
        String rawInput = (String) request.getOrDefault("rawInput", "");
        @SuppressWarnings("unchecked")
        List<String> existingEmails = (List<String>) request.getOrDefault("existingEmails", Collections.emptyList());

        Map<String, Object> result = emailCampaignService.parseManualEmails(rawInput, existingEmails);
        return ResponseEntity.ok(result);
    }

    // =============================================
    // CREATE & SEND
    // =============================================

    @PostMapping("/create-and-send")
    public ResponseEntity<Map<String, Object>> createAndSend(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            String campaignName    = (String) request.getOrDefault("campaignName", "Hiring Campaign");
            String emailSubject    = (String) request.getOrDefault("emailSubject", "Exciting Opportunity at TechnoKraft");
            String conductMode     = (String) request.getOrDefault("conductMode", "ONLINE");
            String onlinePlatform  = (String) request.getOrDefault("onlinePlatform", "GOOGLE_MEET");
            String meetingLink     = (String) request.getOrDefault("meetingLink", "");
            String venueAddress    = (String) request.getOrDefault("venueAddress", "");
            String hiringRole      = (String) request.getOrDefault("hiringRole", "AI/ML Engineer");
            String opportunityType = (String) request.getOrDefault("opportunityType", "JOB");
            String eventDate       = (String) request.getOrDefault("eventDate", "");
            String eventTime       = (String) request.getOrDefault("eventTime", "");
            String customNotes     = (String) request.getOrDefault("customNotes", "");
            String sendMode        = (String) request.getOrDefault("sendMode", "IMMEDIATE");
            String scheduledAt     = (String) request.getOrDefault("scheduledAt", null);

            @SuppressWarnings("unchecked")
            List<String> emails = (List<String>) request.get("emails");
            if (emails == null || emails.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No email recipients provided"));
            }

            String username = authentication != null ? authentication.getName() : "system";
            String userFullName = emailCampaignService.resolveUserFullName(username);

            EmailCampaign campaign = EmailCampaign.builder()
                    .campaignName(campaignName)
                    .emailSubject(emailSubject)
                    .conductMode(conductMode)
                    .onlinePlatform(onlinePlatform)
                    .meetingLink(meetingLink)
                    .venueAddress(venueAddress)
                    .hiringRole(hiringRole)
                    .opportunityType(opportunityType)
                    .eventDate(eventDate)
                    .eventTime(eventTime)
                    .customNotes(customNotes)
                    .sendMode(sendMode)
                    .status("SCHEDULED".equals(sendMode) ? "SCHEDULED" : "DRAFT")
                    .createdBy(username)
                    .createdByName(userFullName)
                    .build();

            if ("SCHEDULED".equals(sendMode) && scheduledAt != null && !scheduledAt.isEmpty()) {
                try {
                    campaign.setScheduledAt(LocalDateTime.parse(scheduledAt.replace("T", "T").substring(0, 16) + ":00"));
                } catch (Exception e) {
                    log.warn("[CAMPAIGN] Could not parse scheduledAt: {}", scheduledAt);
                }
            }

            EmailCampaign saved = emailCampaignService.createCampaign(campaign, emails);

            if ("IMMEDIATE".equals(sendMode)) {
                emailCampaignService.sendCampaignAsync(saved.getId());
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Campaign created and sending started for " + emails.size() + " recipient(s)",
                        "campaignId", saved.getId()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Campaign scheduled for " + emails.size() + " recipient(s)",
                        "campaignId", saved.getId()
                ));
            }

        } catch (Exception e) {
            log.error("[CAMPAIGN] Error creating campaign: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    // =============================================
    // LIST, SERVER-SIDE PAGINATION & AUDIT
    // =============================================

    @GetMapping("/history/paged")
    public ResponseEntity<Map<String, Object>> getPagedCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Map<String, Object> result = emailCampaignService.getPagedCampaigns(page, size, search, status, sortBy, sortDir);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/recipients/paged")
    public ResponseEntity<Map<String, Object>> getPagedRecipients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        Map<String, Object> result = emailCampaignService.getPagedRecipients(id, page, size, search, status);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailedRecipients(@PathVariable Long id) {
        try {
            emailCampaignService.retryFailedCampaignAsync(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Retry process started in background for failed recipients."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Error initiating retry: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/{id}/export-csv")
    public ResponseEntity<byte[]> exportCampaignCsv(@PathVariable Long id) {
        String csvContent = emailCampaignService.exportCampaignRecipientsCsv(id);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campaign-" + id + "-recipients-audit.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listCampaigns() {
        List<EmailCampaign> campaigns = emailCampaignService.getAllCampaigns();
        List<Map<String, Object>> result = new ArrayList<>();
        for (EmailCampaign c : campaigns) {
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
            map.put("createdByName",   c.getCreatedByName() != null ? c.getCreatedByName() : emailCampaignService.resolveUserFullName(c.getCreatedBy()));
            map.put("createdAt",       c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            map.put("sentAt",          c.getSentAt() != null ? c.getSentAt().toString() : "");
            map.put("scheduledAt",     c.getScheduledAt() != null ? c.getScheduledAt().toString() : "");
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<List<Map<String, Object>>> getRecipients(@PathVariable Long id) {
        List<EmailCampaignRecipient> recipients = emailCampaignService.getCampaignRecipients(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (EmailCampaignRecipient r : recipients) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id",            r.getId());
            map.put("email",         r.getEmail());
            map.put("status",        r.getStatus());
            map.put("attemptCount",  r.getAttemptCount() != null ? r.getAttemptCount() : 0);
            map.put("failureReason", r.getFailureReason());
            map.put("sentAt",        r.getSentAt() != null ? r.getSentAt().toString() : "");
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getCampaignStatus(@PathVariable Long id) {
        return emailCampaignService.getCampaign(id)
                .map(c -> ResponseEntity.ok(Map.of(
                        "status",          (Object) c.getStatus(),
                        "totalRecipients", c.getTotalRecipients(),
                        "sentCount",       c.getSentCount(),
                        "failedCount",     c.getFailedCount(),
                        "skippedCount",    c.getSkippedCount(),
                        "createdByName",   (Object) (c.getCreatedByName() != null ? c.getCreatedByName() : emailCampaignService.resolveUserFullName(c.getCreatedBy()))
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // =============================================
    // TEMPLATE MASTER CRUD
    // =============================================

    @GetMapping("/templates")
    public ResponseEntity<List<EmailTemplate>> getAllTemplates() {
        return ResponseEntity.ok(emailTemplateRepository.findByIsActiveTrueOrderByCreatedAtDesc());
    }

    @PostMapping("/templates")
    public ResponseEntity<Map<String, Object>> saveTemplate(
            @RequestBody EmailTemplate template,
            Authentication authentication) {
        try {
            if (template.getTemplateName() == null || template.getTemplateName().trim().isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("message", "Template name is required");
                return ResponseEntity.badRequest().body(err);
            }
            if (template.getEmailSubject() == null || template.getEmailSubject().trim().isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("message", "Email subject is required");
                return ResponseEntity.badRequest().body(err);
            }

            template.setCreatedBy(authentication != null ? authentication.getName() : "system");
            template.setIsActive(true);
            EmailTemplate saved = emailTemplateRepository.save(template);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Template saved successfully");
            resp.put("template", saved);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("[TEMPLATE] Error saving template", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Failed to save template: " + e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable Long id,
            @RequestBody EmailTemplate incoming) {
        Optional<EmailTemplate> opt = emailTemplateRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        EmailTemplate existing = opt.get();
        if (incoming.getTemplateName() != null) existing.setTemplateName(incoming.getTemplateName());
        if (incoming.getEmailSubject() != null) existing.setEmailSubject(incoming.getEmailSubject());
        if (incoming.getConductMode() != null) existing.setConductMode(incoming.getConductMode());
        if (incoming.getOnlinePlatform() != null) existing.setOnlinePlatform(incoming.getOnlinePlatform());
        if (incoming.getMeetingLink() != null) existing.setMeetingLink(incoming.getMeetingLink());
        if (incoming.getVenueAddress() != null) existing.setVenueAddress(incoming.getVenueAddress());
        if (incoming.getHiringRole() != null) existing.setHiringRole(incoming.getHiringRole());
        if (incoming.getOpportunityType() != null) existing.setOpportunityType(incoming.getOpportunityType());
        if (incoming.getEventDate() != null) existing.setEventDate(incoming.getEventDate());
        if (incoming.getEventTime() != null) existing.setEventTime(incoming.getEventTime());
        if (incoming.getCustomNotes() != null) existing.setCustomNotes(incoming.getCustomNotes());
        EmailTemplate updated = emailTemplateRepository.save(existing);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Template updated successfully");
        resp.put("template", updated);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable Long id) {
        Optional<EmailTemplate> opt = emailTemplateRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        EmailTemplate existing = opt.get();
        existing.setIsActive(false);
        emailTemplateRepository.save(existing);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Template deleted successfully");
        return ResponseEntity.ok(resp);
    }
}
