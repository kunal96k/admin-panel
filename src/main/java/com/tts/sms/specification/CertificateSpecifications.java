package com.tts.sms.specification;

import com.tts.sms.model.Admission;
import com.tts.sms.model.Certificate;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CertificateSpecifications {

    /**
     * Build a composed Specification from individual filter params.
     * All non-null / non-blank params are AND-ed together.
     *
     * @param search    General search term (matches name tokens, reg no, cert no, course, batch)
     * @param course    Exact course name filter
     * @param status    Exact status filter ("Issued" / "Not Issued")
     * @param mobileNo  Mobile number partial match (joined to Admission table)
     * @param fromDate  Issue date range start (inclusive)
     * @param toDate    Issue date range end (inclusive)
     */
    public static Specification<Certificate> build(
            String search,
            String course,
            String status,
            String mobileNo,
            LocalDate fromDate,
            LocalDate toDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Always filter by active records
            predicates.add(cb.equal(root.get("isActive"), true));

            // 2. General search term - multi-token aware
            if (hasValue(search)) {
                String clean = search.trim().toLowerCase();
                String[] tokens = clean.split("\\s+");
                String digits = clean.replaceAll("[^0-9]", "");

                if (tokens.length > 1) {
                    // Multi-word: match each token against name parts (AND logic)
                    // So "kunal pat" finds students where firstName contains "kunal" AND lastName contains "pat"
                    List<Predicate> tokenPreds = new ArrayList<>();
                    for (String tok : tokens) {
                        if (!tok.isEmpty()) {
                            tokenPreds.add(cb.or(
                                    cb.like(cb.lower(root.get("studentName")), "%" + tok + "%"),
                                    cb.like(cb.lower(root.get("registrationNo")), "%" + tok + "%"),
                                    cb.like(cb.lower(root.get("certificateNo")), "%" + tok + "%"),
                                    cb.like(cb.lower(root.get("courseName")), "%" + tok + "%"),
                                    cb.like(cb.lower(root.get("batch")), "%" + tok + "%")
                            ));
                        }
                    }
                    // Also include a direct full-string match as fallback
                    Predicate fullMatch = cb.or(
                            cb.like(cb.lower(root.get("studentName")), "%" + clean + "%"),
                            cb.like(cb.lower(root.get("registrationNo")), "%" + clean + "%"),
                            cb.like(cb.lower(root.get("certificateNo")), "%" + clean + "%")
                    );
                    predicates.add(cb.or(
                            fullMatch,
                            cb.and(tokenPreds.toArray(new Predicate[0]))
                    ));
                } else {
                    // Single token: broad OR search
                    List<Predicate> singlePreds = new ArrayList<>();
                    singlePreds.add(cb.like(cb.lower(root.get("studentName")), "%" + clean + "%"));
                    singlePreds.add(cb.like(cb.lower(root.get("registrationNo")), "%" + clean + "%"));
                    singlePreds.add(cb.like(cb.lower(root.get("certificateNo")), "%" + clean + "%"));
                    singlePreds.add(cb.like(cb.lower(root.get("courseName")), "%" + clean + "%"));
                    singlePreds.add(cb.like(cb.lower(root.get("batch")), "%" + clean + "%"));

                    // Also try digits-only variant for numeric reg nos
                    if (!digits.isEmpty() && !digits.equals(clean)) {
                        singlePreds.add(cb.like(cb.lower(root.get("registrationNo")), "%" + digits + "%"));
                        singlePreds.add(cb.like(cb.lower(root.get("certificateNo")), "%" + digits + "%"));
                    }
                    predicates.add(cb.or(singlePreds.toArray(new Predicate[0])));
                }
            }

            // 3. Course filter (exact match)
            if (hasValue(course)) {
                predicates.add(cb.equal(cb.lower(root.get("courseName")), course.trim().toLowerCase()));
            }

            // 4. Status filter (exact match)
            if (hasValue(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }

            // 5. Mobile No filter - join to Admission via registrationNo
            if (hasValue(mobileNo)) {
                String mob = mobileNo.trim();
                // Subquery: find registrationNumbers in Admission where mobilePrimary or mobileSecondary matches
                Subquery<String> subquery = query.subquery(String.class);
                Root<Admission> admRoot = subquery.from(Admission.class);
                subquery.select(admRoot.get("registrationNumber"))
                        .where(cb.and(
                                cb.equal(admRoot.get("isDeleted"), false),
                                cb.or(
                                        cb.like(admRoot.get("mobilePrimary"), "%" + mob + "%"),
                                        cb.like(admRoot.get("mobileSecondary"), "%" + mob + "%")
                                )
                        ));
                predicates.add(root.get("registrationNo").in(subquery));
            }

            // 6. Issue date from (inclusive)
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("issueDate"), fromDate));
            }

            // 7. Issue date to (inclusive)
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("issueDate"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasValue(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
