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

    /**
     * Find all installments for an admission ordered by due date
     */
    List<FeeInstallment> findByAdmissionIdOrderByDueDateAsc(Long admissionId);

    /**
     * Delete all installments for an admission
     */
    @Modifying
    @Query("DELETE FROM FeeInstallment f WHERE f.admissionId = :admissionId")
    void deleteByAdmissionId(@Param("admissionId") Long admissionId);

    /**
     * Get total paid amount for an admission
     */
    @Query("SELECT COALESCE(SUM(f.amount), 0.0) FROM FeeInstallment f " +
            "WHERE f.admissionId = :admissionId AND f.status = 'Paid'")
    Double getTotalPaidAmount(@Param("admissionId") Long admissionId);

    /**
     * Get total due amount for an admission
     */
    @Query("SELECT COALESCE(SUM(f.amount), 0.0) FROM FeeInstallment f " +
            "WHERE f.admissionId = :admissionId AND f.status = 'Pending'")
    Double getTotalDueAmount(@Param("admissionId") Long admissionId);

    /**
     * Find overdue installments
     */
    @Query("SELECT f FROM FeeInstallment f " +
            "WHERE f.status = 'Pending' AND f.dueDate < :currentDate " +
            "ORDER BY f.dueDate ASC")
    List<FeeInstallment> findOverdueInstallments(@Param("currentDate") LocalDate currentDate);

    /**
     * Find installments due in a date range
     */
    @Query("SELECT f FROM FeeInstallment f " +
            "WHERE f.status = 'Pending' " +
            "AND f.dueDate BETWEEN :startDate AND :endDate " +
            "ORDER BY f.dueDate ASC")
    List<FeeInstallment> findInstallmentsDueInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Count pending installments for admission
     */
    @Query("SELECT COUNT(f) FROM FeeInstallment f " +
            "WHERE f.admissionId = :admissionId AND f.status = 'Pending'")
    Long countPendingInstallments(@Param("admissionId") Long admissionId);

    List<FeeInstallment> findByAdmissionIdIn(List<Long> admissionIds);

    /**
     * Update installment status
     */
    @Modifying
    @Query("UPDATE FeeInstallment f SET f.status = :status, f.paidDate = :paidDate " +
            "WHERE f.id = :id")
    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("paidDate") LocalDate paidDate);
}