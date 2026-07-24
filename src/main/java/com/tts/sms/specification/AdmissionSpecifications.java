package com.tts.sms.specification;

import com.tts.sms.dto.AdmissionSearchDTO;
import com.tts.sms.model.Admission;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AdmissionSpecifications {

    public static Specification<Admission> getSearchSpecification(AdmissionSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Only non-deleted admissions
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
                            cb.like(cb.lower(root.get("lastName")), "%" + cleanTerm + "%")
                        );

                        String[] tokens = cleanTerm.split("\\s+");
                        if (tokens.length > 1) {
                            List<Predicate> tokenPreds = new ArrayList<>();
                            for (String tok : tokens) {
                                if (!tok.trim().isEmpty()) {
                                    tokenPreds.add(cb.or(
                                        cb.like(cb.lower(root.get("firstName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("middleName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("lastName")), "%" + tok + "%")
                                    ));
                                }
                            }
                            predicates.add(cb.or(fullStringMatch, cb.and(tokenPreds.toArray(new Predicate[0]))));
                        } else {
                            predicates.add(fullStringMatch);
                        }
                    } else if ("ADMISSION_NO".equals(field)) {
                        List<Predicate> admPreds = new ArrayList<>();
                        admPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + cleanTerm + "%"));
                        admPreds.add(cb.like(cb.lower(root.get("rollNumber")), "%" + cleanTerm + "%"));

                        if (!digits.isEmpty()) {
                            try {
                                Long num = Long.parseLong(digits);
                                admPreds.add(cb.equal(root.get("id"), num));
                            } catch (NumberFormatException ignored) {}
                            if (!digits.equals(cleanTerm)) {
                                admPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + digits + "%"));
                                admPreds.add(cb.like(cb.lower(root.get("rollNumber")), "%" + digits + "%"));
                            }
                        }
                        predicates.add(cb.or(admPreds.toArray(new Predicate[0])));
                    } else if ("MOBILE".equals(field)) {
                        List<Predicate> mobPreds = new ArrayList<>();
                        mobPreds.add(cb.like(cb.lower(root.get("mobilePrimary")), "%" + cleanTerm + "%"));
                        mobPreds.add(cb.like(cb.lower(root.get("mobileSecondary")), "%" + cleanTerm + "%"));
                        if (!digits.isEmpty() && !digits.equals(cleanTerm)) {
                            mobPreds.add(cb.like(cb.lower(root.get("mobilePrimary")), "%" + digits + "%"));
                            mobPreds.add(cb.like(cb.lower(root.get("mobileSecondary")), "%" + digits + "%"));
                        }
                        predicates.add(cb.or(mobPreds.toArray(new Predicate[0])));
                    } else if ("MOBILE_SEC".equals(field) || "SECONDARY_MOBILE".equals(field)) {
                        List<Predicate> secMobPreds = new ArrayList<>();
                        secMobPreds.add(cb.like(cb.lower(root.get("mobileSecondary")), "%" + cleanTerm + "%"));
                        if (!digits.isEmpty() && !digits.equals(cleanTerm)) {
                            secMobPreds.add(cb.like(cb.lower(root.get("mobileSecondary")), "%" + digits + "%"));
                        }
                        predicates.add(cb.or(secMobPreds.toArray(new Predicate[0])));
                    } else if ("ASSIGN_TO".equals(field)) {
                        Join<Admission, com.tts.sms.model.Enquiry> enquiryJoin = root.join("enquiry", JoinType.LEFT);
                        predicates.add(cb.or(
                            cb.like(cb.lower(root.get("createdBy")), "%" + cleanTerm + "%"),
                            cb.like(cb.lower(enquiryJoin.get("assignTo")), "%" + cleanTerm + "%")
                        ));
                    } else {
                        // ALL fields (default)
                        List<Predicate> allPreds = new ArrayList<>();

                        allPreds.add(cb.like(cb.lower(root.get("firstName")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("middleName")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("lastName")), "%" + cleanTerm + "%"));

                        String[] tokens = cleanTerm.split("\\s+");
                        if (tokens.length > 1) {
                            List<Predicate> tokenPreds = new ArrayList<>();
                            for (String tok : tokens) {
                                if (!tok.trim().isEmpty()) {
                                    tokenPreds.add(cb.or(
                                        cb.like(cb.lower(root.get("firstName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("middleName")), "%" + tok + "%"),
                                        cb.like(cb.lower(root.get("lastName")), "%" + tok + "%")
                                    ));
                                }
                            }
                            allPreds.add(cb.and(tokenPreds.toArray(new Predicate[0])));
                        }

                        allPreds.add(cb.like(cb.lower(root.get("mobilePrimary")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("mobileSecondary")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("rollNumber")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("emailPrimary")), "%" + cleanTerm + "%"));
                        allPreds.add(cb.like(cb.lower(root.get("createdBy")), "%" + cleanTerm + "%"));

                        Join<Admission, com.tts.sms.model.Enquiry> enquiryJoin = root.join("enquiry", JoinType.LEFT);
                        allPreds.add(cb.like(cb.lower(enquiryJoin.get("assignTo")), "%" + cleanTerm + "%"));

                        if (!digits.isEmpty()) {
                            try {
                                Long num = Long.parseLong(digits);
                                allPreds.add(cb.equal(root.get("id"), num));
                            } catch (NumberFormatException ignored) {}
                            if (!digits.equals(cleanTerm)) {
                                allPreds.add(cb.like(cb.lower(root.get("mobilePrimary")), "%" + digits + "%"));
                                allPreds.add(cb.like(cb.lower(root.get("registrationNumber")), "%" + digits + "%"));
                            }
                        }

                        predicates.add(cb.or(allPreds.toArray(new Predicate[0])));
                    }
                }

                // 3. Status
                if (searchDTO.getStatus() != null && !searchDTO.getStatus().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("status"), searchDTO.getStatus()));
                }

                // 4. Student Category
                if (searchDTO.getStudentCategory() != null && !searchDTO.getStudentCategory().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("studentCategory"), searchDTO.getStudentCategory()));
                }

                // 5. Course (JSON list containing course name)
                if (searchDTO.getCourse() != null && !searchDTO.getCourse().trim().isEmpty()) {
                    Expression<String> jsonQuote = cb.function("JSON_QUOTE", String.class, cb.literal(searchDTO.getCourse()));
                    Expression<Integer> jsonContains = cb.function("JSON_CONTAINS", Integer.class, root.get("courses"), jsonQuote);
                    predicates.add(cb.equal(jsonContains, 1));
                }

                // 6. Batch (JSON list containing batch name)
                if (searchDTO.getBatch() != null && !searchDTO.getBatch().trim().isEmpty()) {
                    Expression<String> jsonQuote = cb.function("JSON_QUOTE", String.class, cb.literal(searchDTO.getBatch()));
                    Expression<Integer> jsonContains = cb.function("JSON_CONTAINS", Integer.class, root.get("batches"), jsonQuote);
                    predicates.add(cb.equal(jsonContains, 1));
                }

                // 7. Academic Year
                if (searchDTO.getAcademicYear() != null && !searchDTO.getAcademicYear().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("academicYear"), searchDTO.getAcademicYear()));
                }

                // 8. Date From
                if (searchDTO.getAdmissionDateFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("admissionDate"), searchDTO.getAdmissionDateFrom()));
                }

                // 9. Date To
                if (searchDTO.getAdmissionDateTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("admissionDate"), searchDTO.getAdmissionDateTo()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
