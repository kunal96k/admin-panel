package com.tts.sms.repository;

import com.tts.sms.model.Bank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {

    @Query("SELECT b FROM Bank b WHERE b.isActive = true AND " +
            "LOWER(b.bankName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Bank> findAllActiveWithSearch(@Param("search") String search, Pageable pageable);

    @Query("SELECT b FROM Bank b WHERE b.isActive = true")
    Page<Bank> findAllActive(Pageable pageable);

    Optional<Bank> findByBankNameIgnoreCaseAndIsActiveTrue(String bankName);

    boolean existsByBankNameIgnoreCaseAndIsActiveTrue(String bankName);

    boolean existsByBankNameIgnoreCaseAndIdNotAndIsActiveTrue(String bankName, Long id);
}