package com.tts.sms.repository;

import com.tts.sms.model.EmailCampaignRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailCampaignRecipientRepository extends JpaRepository<EmailCampaignRecipient, Long> {

    List<EmailCampaignRecipient> findByCampaignId(Long campaignId);

    List<EmailCampaignRecipient> findByCampaignIdAndStatus(Long campaignId, String status);

    List<EmailCampaignRecipient> findByCampaignIdAndStatusIn(Long campaignId, List<String> statuses);

    long countByCampaignIdAndStatus(Long campaignId, String status);

    Page<EmailCampaignRecipient> findByCampaignId(Long campaignId, Pageable pageable);

    Page<EmailCampaignRecipient> findByCampaignIdAndStatus(Long campaignId, String status, Pageable pageable);

    @Query("SELECT r FROM EmailCampaignRecipient r WHERE r.campaign.id = :campaignId AND " +
           "(:status IS NULL OR :status = '' OR r.status = :status) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(r.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(r.failureReason, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<EmailCampaignRecipient> findByCampaignIdWithFilters(@Param("campaignId") Long campaignId,
                                                             @Param("search") String search,
                                                             @Param("status") String status,
                                                             Pageable pageable);
}
