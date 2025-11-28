package com.tts.sms.repository;

import com.tts.sms.model.FeeInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, Long> {

    // Find by registration number instead of admission ID
    List<FeeInstallment> findByRegistrationNumberOrderByDueDateAsc(String registrationNumber);

    // Delete by registration number
    @Modifying
    @Query("DELETE FROM FeeInstallment f WHERE f.registrationNumber = :registrationNumber")
    void deleteByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    // Count total installments
    @Query("SELECT COUNT(f) FROM FeeInstallment f WHERE f.registrationNumber = :registrationNumber")
    Integer countByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    // Count paid installments
    @Query("SELECT COUNT(f) FROM FeeInstallment f WHERE f.registrationNumber = :registrationNumber AND f.status = 'Paid'")
    Integer countPaidByRegistrationNumber(@Param("registrationNumber") String registrationNumber);

    // Get total paid amount
    @Query("SELECT COALESCE(SUM(f.paidAmount), 0.0) FROM FeeInstallment f " +
            "WHERE f.registrationNumber = :registrationNumber AND f.status = 'Paid'")
    Double getTotalPaidAmount(@Param("registrationNumber") String registrationNumber);

    // Get total due amount
    @Query("SELECT COALESCE(SUM(f.amount - COALESCE(f.paidAmount, 0)), 0.0) FROM FeeInstallment f " +
            "WHERE f.registrationNumber = :registrationNumber AND f.status != 'Paid'")
    Double getTotalDueAmount(@Param("registrationNumber") String registrationNumber);

    // Find overdue installments
    @Query("SELECT f FROM FeeInstallment f WHERE f.registrationNumber = :registrationNumber " +
            "AND f.status = 'Pending' AND f.dueDate < CURRENT_DATE")
    List<FeeInstallment> findOverdueInstallments(@Param("registrationNumber") String registrationNumber);

    // Find installments due in date range
    @Query("SELECT f FROM FeeInstallment f WHERE f.status = 'Pending' " +
            "AND f.dueDate BETWEEN :startDate AND :endDate ORDER BY f.dueDate ASC")
    List<FeeInstallment> findInstallmentsDueInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}