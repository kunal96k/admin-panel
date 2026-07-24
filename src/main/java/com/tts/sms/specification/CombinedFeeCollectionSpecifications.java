package com.tts.sms.specification;

import com.tts.sms.model.CombinedFeeCollection;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CombinedFeeCollectionSpecifications {

    public static Specification<CombinedFeeCollection> getSearchSpecification(
            LocalDate fromDate,
            LocalDate toDate,
            String dataSource,
            String paymentMode,
            String searchType,
            String searchQuery) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Date Range
            if (fromDate != null && toDate != null) {
                predicates.add(cb.between(root.get("receiptDate"), fromDate, toDate));
            } else if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receiptDate"), fromDate));
            } else if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receiptDate"), toDate));
            }

            // 2. Data Source
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("dataSource"), dataSource));
            }

            // 3. Payment Mode
            if (paymentMode != null && !paymentMode.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("paymentMode"), paymentMode));
            }

            // 4. Text Search based on selector (searchType) or global fallback
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String queryText = searchQuery.trim();
                String val = "%" + queryText.toLowerCase() + "%";
                String digitsOnly = queryText.replaceAll("\\D+", "");
                String digitVal = digitsOnly.length() >= 7 ? "%" + digitsOnly + "%" : null;

                String type = searchType != null ? searchType.trim() : "";

                switch (type) {
                    case "studentName":
                        predicates.add(cb.like(cb.lower(root.get("studentName")), val));
                        break;
                    case "mobileNo":
                        if (digitVal != null) {
                            predicates.add(cb.or(
                                cb.like(cb.lower(root.get("mobileNo")), val),
                                cb.like(cb.lower(root.get("mobileNo")), digitVal)
                            ));
                        } else {
                            predicates.add(cb.like(cb.lower(root.get("mobileNo")), val));
                        }
                        break;
                    case "receiptNo":
                        predicates.add(cb.like(cb.lower(root.get("receiptNo")), val));
                        break;
                    default:
                        // Global search ("All Fields" or empty/unknown searchType)
                        List<Predicate> globalPredicates = new ArrayList<>();
                        globalPredicates.add(cb.like(cb.lower(root.get("studentName")), val));
                        globalPredicates.add(cb.like(cb.lower(root.get("mobileNo")), val));
                        if (digitVal != null) {
                            globalPredicates.add(cb.like(cb.lower(root.get("mobileNo")), digitVal));
                        }
                        globalPredicates.add(cb.like(cb.lower(root.get("receiptNo")), val));
                        globalPredicates.add(cb.like(cb.lower(root.get("registrationNumber")), val));
                        predicates.add(cb.or(globalPredicates.toArray(new Predicate[0])));
                        break;
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
