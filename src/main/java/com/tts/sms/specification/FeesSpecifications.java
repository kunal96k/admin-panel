package com.tts.sms.specification;

import com.tts.sms.dto.FeesSearchDTO;
import com.tts.sms.model.Admission;
import com.tts.sms.model.FeeInstallment;
import com.tts.sms.model.Fees;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
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

                // 3. Dashboard Filter & Status Handling
                String dashFilter = searchDTO.getDashFilter() != null ? searchDTO.getDashFilter().trim().toLowerCase() : null;
                boolean isOverdue = "overdue".equals(dashFilter)
                        || "overdue".equalsIgnoreCase(searchDTO.getStatus())
                        || Boolean.TRUE.equals(searchDTO.getOverdue());

                if (isOverdue) {
                    // Overdue installments: students with pending installments whose dueDate < today
                    // MUST exclude Clear students and students without outstanding feesDue
                    predicates.add(cb.notEqual(cb.lower(root.get("status")), "clear"));
                    predicates.add(cb.greaterThan(root.get("feesDue"), 0.01));

                    Subquery<String> overdueSubquery = query.subquery(String.class);
                    Root<FeeInstallment> installmentRoot = overdueSubquery.from(FeeInstallment.class);
                    overdueSubquery.select(installmentRoot.get("registrationNumber")).distinct(true);
                    overdueSubquery.where(
                            cb.equal(cb.lower(installmentRoot.get("status")), "pending"),
                            cb.lessThan(installmentRoot.get("dueDate"), LocalDate.now())
                    );
                    predicates.add(root.get("registrationNumber").in(overdueSubquery));
                } else if ("upcoming5days".equals(dashFilter)) {
                    // Due in 5 Days: pending installments due between today and today+5
                    // MUST exclude Clear students and students without outstanding feesDue
                    predicates.add(cb.notEqual(cb.lower(root.get("status")), "clear"));
                    predicates.add(cb.greaterThan(root.get("feesDue"), 0.01));

                    Subquery<String> upcomingSubquery = query.subquery(String.class);
                    Root<FeeInstallment> installmentRoot = upcomingSubquery.from(FeeInstallment.class);
                    upcomingSubquery.select(installmentRoot.get("registrationNumber")).distinct(true);
                    upcomingSubquery.where(
                            cb.equal(cb.lower(installmentRoot.get("status")), "pending"),
                            cb.greaterThanOrEqualTo(installmentRoot.get("dueDate"), LocalDate.now()),
                            cb.lessThanOrEqualTo(installmentRoot.get("dueDate"), LocalDate.now().plusDays(5))
                    );
                    predicates.add(root.get("registrationNumber").in(upcomingSubquery));
                } else if ("last6months".equals(dashFilter)) {
                    // Pending - Last 6 Months: pending fees for students admitted in the last 6 months
                    predicates.add(cb.equal(cb.lower(root.get("status")), "pending"));

                    Subquery<String> admSubquery = query.subquery(String.class);
                    Root<Admission> admRoot = admSubquery.from(Admission.class);
                    admSubquery.select(admRoot.get("registrationNumber")).distinct(true);
                    admSubquery.where(
                            cb.isFalse(admRoot.get("isDeleted")),
                            cb.greaterThanOrEqualTo(admRoot.get("admissionDate"), LocalDate.now().minusMonths(6)),
                            cb.lessThanOrEqualTo(admRoot.get("admissionDate"), LocalDate.now())
                    );
                    predicates.add(root.get("registrationNumber").in(admSubquery));
                } else if ("pending".equals(dashFilter)) {
                    // All pending fees
                    predicates.add(cb.equal(cb.lower(root.get("status")), "pending"));
                } else if (searchDTO.getStatus() != null && !searchDTO.getStatus().trim().isEmpty()
                        && !"all".equalsIgnoreCase(searchDTO.getStatus())) {
                    // Normal status filter (Pending, Clear, Refund, etc.)
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

                // 7. Due Date range (ignored if dashFilter is upcoming5days to avoid clashing with table due_date)
                if (!"upcoming5days".equals(dashFilter)) {
                    if (searchDTO.getDueDateFrom() != null || searchDTO.getDueDateTo() != null) {
                        List<Predicate> datePreds = new ArrayList<>();

                        List<Predicate> feesDueDatePreds = new ArrayList<>();
                        if (searchDTO.getDueDateFrom() != null) {
                            feesDueDatePreds.add(cb.greaterThanOrEqualTo(root.get("dueDate"), searchDTO.getDueDateFrom()));
                        }
                        if (searchDTO.getDueDateTo() != null) {
                            feesDueDatePreds.add(cb.lessThanOrEqualTo(root.get("dueDate"), searchDTO.getDueDateTo()));
                        }
                        datePreds.add(cb.and(feesDueDatePreds.toArray(new Predicate[0])));

                        Subquery<String> instDateSubquery = query.subquery(String.class);
                        Root<FeeInstallment> instDateRoot = instDateSubquery.from(FeeInstallment.class);
                        instDateSubquery.select(instDateRoot.get("registrationNumber")).distinct(true);
                        List<Predicate> instPreds = new ArrayList<>();
                        instPreds.add(cb.equal(cb.lower(instDateRoot.get("status")), "pending"));
                        if (searchDTO.getDueDateFrom() != null) {
                            instPreds.add(cb.greaterThanOrEqualTo(instDateRoot.get("dueDate"), searchDTO.getDueDateFrom()));
                        }
                        if (searchDTO.getDueDateTo() != null) {
                            instPreds.add(cb.lessThanOrEqualTo(instDateRoot.get("dueDate"), searchDTO.getDueDateTo()));
                        }
                        instDateSubquery.where(cb.and(instPreds.toArray(new Predicate[0])));
                        datePreds.add(root.get("registrationNumber").in(instDateSubquery));

                        predicates.add(cb.or(datePreds.toArray(new Predicate[0])));
                    }
                }

                // 8. Created Date range (ignored if dashFilter is last6months)
                if (!"last6months".equals(dashFilter)) {
                    if (searchDTO.getCreatedDateFrom() != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), searchDTO.getCreatedDateFrom().atStartOfDay()));
                    }
                    if (searchDTO.getCreatedDateTo() != null) {
                        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), searchDTO.getCreatedDateTo().atTime(23, 59, 59)));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
