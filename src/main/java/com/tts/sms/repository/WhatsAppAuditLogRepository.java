package com.tts.sms.repository;

import com.tts.sms.model.WhatsAppAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface WhatsAppAuditLogRepository extends JpaRepository<WhatsAppAuditLog, Long>, JpaSpecificationExecutor<WhatsAppAuditLog> {

    @Query("SELECT COUNT(l) FROM WhatsAppAuditLog l WHERE l.createdAt >= :startDate AND l.createdAt <= :endDate")
    long countTotalBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(l) FROM WhatsAppAuditLog l WHERE l.status = 'SUCCESS' AND l.createdAt >= :startDate AND l.createdAt <= :endDate")
    long countSuccessfulBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(l) FROM WhatsAppAuditLog l WHERE l.status = 'FAILED' AND l.createdAt >= :startDate AND l.createdAt <= :endDate")
    long countFailedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(l) FROM WhatsAppAuditLog l WHERE l.status = 'BLOCKED' AND l.createdAt >= :startDate AND l.createdAt <= :endDate")
    long countBlockedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(l) FROM WhatsAppAuditLog l WHERE l.billingCategory = :category AND l.status = 'SUCCESS' AND l.createdAt >= :startDate AND l.createdAt <= :endDate")
    long countSuccessfulByCategoryBetween(
            @Param("category") String category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(l) FROM WhatsAppAuditLog l WHERE l.moduleType = :moduleType AND l.createdAt >= :startDate AND l.createdAt <= :endDate")
    long countByModuleTypeBetween(
            @Param("moduleType") String moduleType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
