package com.tts.sms.specification;

import com.tts.sms.dto.EnquirySearchDTO;
import com.tts.sms.model.Enquiry;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class EnquirySpecifications {

    public static Specification<Enquiry> getSearchSpecification(EnquirySearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Only non-deleted enquiries
            predicates.add(cb.equal(root.get("isDeleted"), false));

            if (searchDTO != null) {
                // 2. Search Term Token Parsing with robust field selector support
                if (searchDTO.getSearchTerm() != null && !searchDTO.getSearchTerm().trim().isEmpty()) {
                    String term = searchDTO.getSearchTerm().trim();
                    String cleanTerm = term.toLowerCase();
                    String digits = cleanTerm.replaceAll("[^0-9]", "");
                    String field = searchDTO.getSearchField() != null ? searchDTO.getSearchField().trim().toUpperCase() : "ALL";

                    if ("NAME".equals(field)) {
                        Predicate fullStringMatch = cb.or(
                            cb.like(cb.lower(root.get("firstName")), "%" + cleanTerm + "%"),
                            cb.like(cb.lower(root.get("middleName")), "%" + cleanTerm + "%"),
                            cb.like(cb.lower(root.get("lastName")), "%" + cleanTerm + "%"),
                            cb.like(cb.lower(root.get("fullName")), "%" + cleanTerm + "%")
                        );

                        String[] tokens = cleanTerm.split("\\s+");
                        if (tokens.length > 1) {
                            List<Predicate> tokenPreds = new ArrayList<>();
                            for (String tok : tokens) {
                                if (!tok.trim().isEmpty()) {
                                    tokenPreds.add(cb.or(
                                        cb.like(cb.lower(root.get("firstName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("middleName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("lastName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("fullName")), "%" + tok + "%")
                                    ));
                                }
                            }
                            predicates.add(cb.or(fullStringMatch, cb.and(tokenPreds.toArray(new Predicate[0]))));
                        } else {
                            predicates.add(fullStringMatch);
                        }
                    } else if ("ENQUIRY_NO".equals(field)) {
                        List<Predicate> enqPreds = new ArrayList<>();
                        enqPreds.add(cb.like(cb.lower(root.get("enquiryNo")), "%" + cleanTerm + "%"));

                        if (!digits.isEmpty()) {
                            try {
                                Long num = Long.parseLong(digits);
                                enqPreds.add(cb.equal(root.get("id"), num));
                                String padded = String.format("%06d", num);
                                enqPreds.add(cb.like(cb.lower(root.get("enquiryNo")), "%" + padded + "%"));
                            } catch (NumberFormatException ignored) {}
                        }
                        predicates.add(cb.or(enqPreds.toArray(new Predicate[0])));
                    } else if ("MOBILE".equals(field)) {
                        List<Predicate> mobPreds = new ArrayList<>();
                        mobPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + cleanTerm + "%"));
                        mobPreds.add(cb.like(cb.lower(root.get("secondaryMobile")), "%" + cleanTerm + "%"));
                        if (!digits.isEmpty() && !digits.equals(cleanTerm)) {
                            mobPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + digits + "%"));
                            mobPreds.add(cb.like(cb.lower(root.get("secondaryMobile")), "%" + digits + "%"));
                        }
                        predicates.add(cb.or(mobPreds.toArray(new Predicate[0])));
                    } else if ("ASSIGN_TO".equals(field)) {
                        predicates.add(cb.like(cb.lower(root.get("assignTo")), "%" + cleanTerm + "%"));
                    } else {
                        // ALL fields (default)
                        List<Predicate> allPreds = new ArrayList<>();

                        allPreds.add(cb.like(cb.lower(root.get("firstName")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("middleName")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("lastName")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("fullName")), "%" + cleanTerm + "%"));

                        String[] tokens = cleanTerm.split("\\s+");
                        if (tokens.length > 1) {
                            List<Predicate> tokenPreds = new ArrayList<>();
                            for (String tok : tokens) {
                                if (!tok.trim().isEmpty()) {
                                    tokenPreds.add(cb.or(
                                        cb.like(cb.lower(root.get("firstName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("middleName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("lastName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("fullName")), "%" + tok + "%")
                                    ));
                                }
                            }
                            allPreds.add(cb.and(tokenPreds.toArray(new Predicate[0])));
                        }

                        allPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("secondaryMobile")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("email")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("secondaryEmail")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("enquiryNo")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("assignTo")), "%" + cleanTerm + "%"));

                        if (!digits.isEmpty()) {
                            try {
                                Long num = Long.parseLong(digits);
                                allPreds.add(cb.equal(root.get("id"), num));
                            } catch (NumberFormatException ignored) {}
                            if (!digits.equals(cleanTerm)) {
                                allPreds.add(cb.like(cb.lower(root.get("mobile")), "%" + digits + "%"));
                                allPreds.add(cb.like(cb.lower(root.get("secondaryMobile")), "%" + digits + "%"));
                            }
                        }

                        predicates.add(cb.or(allPreds.toArray(new Predicate[0])));
                    }
                }

                // 3. Status
                if (searchDTO.getStatus() != null && !searchDTO.getStatus().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("status"), searchDTO.getStatus()));
                }

                // 4. Source
                if (searchDTO.getSource() != null && !searchDTO.getSource().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("source"), searchDTO.getSource()));
                }

                // 5. Course (JSON list containing course name)
                if (searchDTO.getCourse() != null && !searchDTO.getCourse().trim().isEmpty()) {
                    Expression<String> jsonQuote = cb.function("JSON_QUOTE", String.class, cb.literal(searchDTO.getCourse()));
                    Expression<Integer> jsonContains = cb.function("JSON_CONTAINS", Integer.class, root.get("courses"), jsonQuote);
                    predicates.add(cb.equal(jsonContains, 1));
                }

                // 6. Assign To
                if (searchDTO.getAssignTo() != null && !searchDTO.getAssignTo().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("assignTo"), searchDTO.getAssignTo()));
                }

                // 7. Date From & To Filters (enquiryDate with fallback to createdAt date)
                Expression<java.time.LocalDate> effectiveDate = cb.coalesce(
                        root.get("enquiryDate"),
                        cb.function("DATE", java.time.LocalDate.class, root.get("createdAt"))
                );

                if (searchDTO.getFromDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(effectiveDate, searchDTO.getFromDate()));
                }

                if (searchDTO.getToDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(effectiveDate, searchDTO.getToDate()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
