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
                // 2. Search Term Token Parsing (Multi-word search logic)
                if (searchDTO.getSearchTerm() != null && !searchDTO.getSearchTerm().trim().isEmpty()) {
                    String[] tokens = searchDTO.getSearchTerm().trim().split("\\s+");
                    if (tokens.length >= 3) {
                        // First word -> firstName, Second word -> middleName, Third word -> lastName
                        Predicate first = cb.like(cb.lower(root.get("firstName")), "%" + tokens[0].toLowerCase() + "%");
                        Predicate middle = cb.like(cb.lower(root.get("middleName")), "%" + tokens[1].toLowerCase() + "%");
                        Predicate last = cb.like(cb.lower(root.get("lastName")), "%" + tokens[2].toLowerCase() + "%");
                        predicates.add(cb.and(first, middle, last));
                    } else if (tokens.length == 2) {
                        // First word -> firstName, Second word -> middleName OR lastName
                        Predicate first = cb.like(cb.lower(root.get("firstName")), "%" + tokens[0].toLowerCase() + "%");
                        Predicate middleOrLast = cb.or(
                            cb.like(cb.lower(root.get("middleName")), "%" + tokens[1].toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("lastName")), "%" + tokens[1].toLowerCase() + "%")
                        );
                        predicates.add(cb.and(first, middleOrLast));
                    } else {
                        // Single word: Search any relevant columns
                        String token = tokens[0].toLowerCase();
                        predicates.add(cb.or(
                            cb.like(cb.lower(root.get("firstName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("middleName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("lastName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("mobilePrimary")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("registrationNumber")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("emailPrimary")), "%" + token + "%")
                        ));
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
