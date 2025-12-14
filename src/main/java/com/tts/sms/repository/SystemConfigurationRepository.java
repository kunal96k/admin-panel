package com.tts.sms.repository;

import com.tts.sms.model.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {

    @Query("SELECT sc FROM SystemConfiguration sc WHERE sc.configKey = :key")
    Optional<SystemConfiguration> findByConfigKey(@Param("key") String configKey);
}