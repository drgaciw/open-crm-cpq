package com.opencrm.authorization.repository;

import com.opencrm.authorization.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByActionAndResource(String action, String resource);
}
