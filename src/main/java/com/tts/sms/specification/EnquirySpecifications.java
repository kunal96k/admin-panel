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
                // 2. Search Term Token Parsing (Multi-word search logic)
                if (searchDTO.getSearchTerm() != null && !searchDTO.getSearchTerm().trim().isEmpty()) {
                    String[] tokens = searchDTO.getSearchTerm().trim().split("\\s+");
                    if (tokens.length >= 3) {
                        Predicate first = cb.like(cb.lower(root.get("firstName")), "%" + tokens[0].toLowerCase() + "%");
                        Predicate middle = cb.like(cb.lower(root.get("middleName")), "%" + tokens[1].toLowerCase() + "%");
                        Predicate last = cb.like(cb.lower(root.get("lastName")), "%" + tokens[2].toLowerCase() + "%");
                        predicates.add(cb.and(first, middle, last));
                    } else if (tokens.length == 2) {
                        Predicate first = cb.like(cb.lower(root.get("firstName")), "%" + tokens[0].toLowerCase() + "%");
                        Predicate middleOrLast = cb.or(
                            cb.like(cb.lower(root.get("middleName")), "%" + tokens[1].toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("lastName")), "%" + tokens[1].toLowerCase() + "%")
                        );
                        predicates.add(cb.and(first, middleOrLast));
                    } else {
                        String token = tokens[0].toLowerCase();
                        predicates.add(cb.or(
                            cb.like(cb.lower(root.get("firstName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("middleName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("lastName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("fullName")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("mobile")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("secondaryMobile")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("email")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("secondaryEmail")), "%" + token + "%"),
                            cb.like(cb.lower(root.get("enquiryNo")), "%" + token + "%")
                        ));
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

                // 7. Date From
                if (searchDTO.getFromDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("enquiryDate"), searchDTO.getFromDate()));
                }

                // 8. Date To
                if (searchDTO.getToDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("enquiryDate"), searchDTO.getToDate()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
