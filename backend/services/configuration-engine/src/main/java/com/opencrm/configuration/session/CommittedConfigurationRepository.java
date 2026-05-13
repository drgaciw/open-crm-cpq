package com.opencrm.configuration.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommittedConfigurationRepository extends JpaRepository<CommittedConfiguration, Long> {
    Optional<CommittedConfiguration> findBySessionId(Long sessionId);
}
