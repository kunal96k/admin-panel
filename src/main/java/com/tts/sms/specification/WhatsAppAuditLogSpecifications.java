package com.tts.sms.specification;

import com.tts.sms.model.WhatsAppAuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable JPA Specifications for querying WhatsApp Audit Logs.
 * Handles dynamic filtering, multi-field search keyword matching, and date constraints.
 */
public class WhatsAppAuditLogSpecifications {

    /**
     * Builds dynamic JPA Specification for WhatsAppAuditLog entity.
     *
     * @param startDate   start date (inclusive)
     * @param endDate     end date (inclusive)
     * @param moduleType  module or template type (COURSE_INFORMATION, FEE_REMINDER, TEST)
     * @param status      dispatch status (SUCCESS, FAILED, BLOCKED)
     * @param triggerType trigger source (MANUAL, AUTOMATED_SCHEDULER, TEST)
     * @param search      multi-keyword search query
     * @return JPA Specification
     */
    public static Specification<WhatsAppAuditLog> createSpecification(
            LocalDate startDate,
            LocalDate endDate,
            String moduleType,
            String status,
            String triggerType,
            String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Date range filter (handles both, one, or none)
            if (startDate != null && endDate != null) {
                predicates.add(cb.between(
                        root.get("createdAt"),
                        startDate.atStartOfDay(),
                        endDate.atTime(LocalTime.MAX)
                ));
            } else if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        startDate.atStartOfDay()
                ));
            } else if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        endDate.atTime(LocalTime.MAX)
                ));
            }
            // If both are null -> no date restriction (supports "All Time")

            // 2. Module Type
            if (moduleType != null && !moduleType.isBlank() && !"ALL".equalsIgnoreCase(moduleType)) {
                predicates.add(cb.equal(root.get("moduleType"), moduleType.trim()));
            }

            // 3. Status
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }

            // 4. Trigger Source
            if (triggerType != null && !triggerType.isBlank() && !"ALL".equalsIgnoreCase(triggerType)) {
                predicates.add(cb.equal(root.get("triggerType"), triggerType.trim()));
            }

            // 5. Multi-field Search Keyword
            if (search != null && !search.isBlank()) {
                String term = search.trim();
                String cleanTerm = "%" + term.toLowerCase() + "%";

                List<Predicate> searchPreds = new ArrayList<>();
                searchPreds.add(cb.like(cb.lower(root.get("recipientMobile")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("studentName")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("referenceNumber")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("courseName")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("templateName")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("metaMessageId")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("triggeredByUsername")), cleanTerm));
                searchPreds.add(cb.like(cb.lower(root.get("triggeredByEmployeeName")), cleanTerm));

                // If numeric, also search exact ID or referenceId
                try {
                    String digitsOnly = term.replaceAll("[^0-9]", "");
                    if (!digitsOnly.isEmpty()) {
                        Long num = Long.parseLong(digitsOnly);
                        searchPreds.add(cb.equal(root.get("id"), num));
                        searchPreds.add(cb.equal(root.get("referenceId"), num));
                    }
                } catch (NumberFormatException ignored) {
                }

                predicates.add(cb.or(searchPreds.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
