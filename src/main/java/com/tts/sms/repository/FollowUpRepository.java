package com.tts.sms.repository;

import com.tts.sms.model.Enquiry;
import com.tts.sms.model.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    List<FollowUp> findByEnquiryIdOrderByFollowUpDateDesc(Long enquiryId);
}
