package com.tts.sms.repository;

import com.tts.sms.model.LeadSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {

    @Query("SELECT l FROM LeadSource l WHERE l.isActive = true AND " +
            "LOWER(l.sourceTitle) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<LeadSource> findAllActiveWithSearch(@Param("search") String search, Pageable pageable);

    @Query("SELECT l FROM LeadSource l WHERE l.isActive = true")
    Page<LeadSource> findAllActive(Pageable pageable);

    Optional<LeadSource> findBySourceTitleIgnoreCaseAndIsActiveTrue(String sourceTitle);

    boolean existsBySourceTitleIgnoreCaseAndIsActiveTrue(String sourceTitle);

    boolean existsBySourceTitleIgnoreCaseAndIdNotAndIsActiveTrue(String sourceTitle, Long id);
}