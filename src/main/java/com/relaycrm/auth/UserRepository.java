package com.relaycrm.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByApiKeyHash(String apiKeyHash);

    boolean existsByIdAndOrgId(Long id, Long orgId);
}
