package com.tts.sms.specification;

import com.tts.sms.dto.FeesSearchDTO;
import com.tts.sms.model.Fees;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class FeesSpecifications {

    public static Specification<Fees> getSearchSpecification(FeesSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Only non-deleted fees
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (searchDTO != null) {
                // 2. Search Term Token Parsing with robust field selector support
                if (searchDTO.getSearchTerm() != null && !searchDTO.getSearchTerm().trim().isEmpty()) {
                    String term = searchDTO.getSearchTerm().trim();
                    String cleanTerm = term.toLowerCase();
                    String digits = cleanTerm.replaceAll("[^0-9]", "");
                    String field = searchDTO.getSearchField() != null ? searchDTO.getSearchField().trim().toUpperCase() : "ALL";

                    if ("NAME".equals(field)) {
                        Predicate fullStringMatch = cb.like(cb.lower(root.get("studentName")), "%" + cleanTerm + "%");

                        String[] tokens = cleanTerm.split("\\s+");
                        if (tokens.length > 1) {
                            List<Predicate> tokenPreds = new ArrayList<>();
                            for (String tok : tokens) {
                                if (!tok.trim().isEmpty()) {
                                    tokenPreds.add(cb.like(cb.lower(root.get("studentName")), "%" + tok.toLowerCase() + "%"));
                                }
                            }
                            predicates.add(cb.or(fullStringMatch, cb.and(tokenPreds.toArray(new Predicate[0]))));
                        } else {
                            predicates.add(fullStringMatch);
                        }
                    } else if ("ADMISSION_NO".equals(field) || "REG_NO".equals(field)) {
                        List<Predicate> admPreds = new ArrayList<>();
                        admPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + cleanTerm + "%"));

                        if (!digits.isEmpty()) {
                            try {
                                Long num = Long.parseLong(digits);
                                admPreds.add(cb.equal(root.get("admissionId"), num));
                            } catch (NumberFormatException ignored) {}
                            if (!digits.equals(cleanTerm)) {
                                admPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + digits + "%"));
                            }
                        }
                        predicates.add(cb.or(admPreds.toArray(new Predicate[0])));
                    } else if ("MOBILE_PRI".equals(field) || "PRIMARY_MOBILE".equals(field) || "MOBILE".equals(field) || "MOBILE_SEC".equals(field) || "SECONDARY_MOBILE".equals(field)) {
                        List<Predicate> mobPreds = new ArrayList<>();
                        mobPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + cleanTerm + "%"));
                        if (!digits.isEmpty() && !digits.equals(cleanTerm)) {
                            mobPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + digits + "%"));
                        }
                        predicates.add(cb.or(mobPreds.toArray(new Predicate[0])));
                    } else if ("ASSIGN_TO".equals(field) || "COUNSELOR".equals(field)) {
                        predicates.add(cb.like(cb.lower(root.get("createdBy")), "%" + cleanTerm + "%"));
                    } else {
                        // ALL fields (default)
                        List<Predicate> allPreds = new ArrayList<>();

                        allPreds.add(cb.like(cb.lower(root.get("studentName")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("createdBy")), "%" + cleanTerm + "%"));

                        String[] tokens = cleanTerm.split("\\s+");
                        if (tokens.length > 1) {
                            List<Predicate> tokenPreds = new ArrayList<>();
                            for (String tok : tokens) {
                                if (!tok.trim().isEmpty()) {
                                    tokenPreds.add(cb.like(cb.lower(root.get("studentName")), "%" + tok.toLowerCase() + "%"));
                                }
                            }
                            allPreds.add(cb.and(tokenPreds.toArray(new Predicate[0])));
                        }

                        if (!digits.isEmpty()) {
                            try {
                                Long num = Long.parseLong(digits);
                                allPreds.add(cb.equal(root.get("admissionId"), num));
                            } catch (NumberFormatException ignored) {}
                            if (!digits.equals(cleanTerm)) {
                                allPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + digits + "%"));
                                allPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + digits + "%"));
                            }
                        }

                        predicates.add(cb.or(allPreds.toArray(new Predicate[0])));
                    }
                }

                // 3. Status filter
                if (searchDTO.getStatus() != null && !searchDTO.getStatus().trim().isEmpty()
                        && !"all".equalsIgnoreCase(searchDTO.getStatus())) {
                    predicates.add(cb.equal(cb.lower(root.get("status")), searchDTO.getStatus().trim().toLowerCase()));
                }

                // 4. Course filter
                if (searchDTO.getCourse() != null && !searchDTO.getCourse().trim().isEmpty()) {
                    predicates.add(cb.like(cb.lower(root.get("course")), "%" + searchDTO.getCourse().trim().toLowerCase() + "%"));
                }

                // 5. Fees Due range
                if (searchDTO.getMinFeesDue() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("feesDue"), searchDTO.getMinFeesDue()));
                }
                if (searchDTO.getMaxFeesDue() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("feesDue"), searchDTO.getMaxFeesDue()));
                }

                // 6. Total Fees range
                if (searchDTO.getMinTotalFees() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("totalFees"), searchDTO.getMinTotalFees()));
                }
                if (searchDTO.getMaxTotalFees() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("totalFees"), searchDTO.getMaxTotalFees()));
                }

                // 7. Due Date range
                if (searchDTO.getDueDateFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), searchDTO.getDueDateFrom()));
                }
                if (searchDTO.getDueDateTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), searchDTO.getDueDateTo()));
                }

                // 8. Overdue filter
                if (Boolean.TRUE.equals(searchDTO.getOverdue())) {
                    predicates.add(cb.lessThan(root.get("dueDate"), java.time.LocalDate.now()));
                    predicates.add(cb.notEqual(cb.lower(root.get("status")), "clear"));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
