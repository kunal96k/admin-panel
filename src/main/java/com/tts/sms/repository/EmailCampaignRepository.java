package com.tts.sms.repository;

import com.tts.sms.model.EmailCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

    List<EmailCampaign> findAllByOrderByCreatedAtDesc();

    Page<EmailCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT c FROM EmailCampaign c WHERE " +
           "(:status IS NULL OR :status = '' OR c.status = :status) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(c.campaignName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.emailSubject) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.hiringRole) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(c.createdByName, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(c.createdBy, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<EmailCampaign> findWithFilters(@Param("search") String search,
                                       @Param("status") String status,
                                       Pageable pageable);

    @Query("SELECT c FROM EmailCampaign c WHERE c.status = 'SCHEDULED' AND c.scheduledAt <= :now")
    List<EmailCampaign> findDueScheduledCampaigns(@Param("now") LocalDateTime now);

    List<EmailCampaign> findByStatus(String status);
}
