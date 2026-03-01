package com.opencrm.configuration.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationSessionRepository extends JpaRepository<ConfigurationSession, Long> {
    
    List<ConfigurationSession> findByProductIdAndStatus(String productId, ConfigurationStatus status);
    
    List<ConfigurationSession> findByUserIdAndStatus(String userId, ConfigurationStatus status);
    
    Optional<ConfigurationSession> findByIdAndUserId(Long id, String userId);
}
