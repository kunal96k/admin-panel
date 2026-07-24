package com.tts.sms.repository;

import com.tts.sms.model.CombinedFeeCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CombinedFeeCollectionRepository extends JpaRepository<CombinedFeeCollection, String>, JpaSpecificationExecutor<CombinedFeeCollection> {
}
