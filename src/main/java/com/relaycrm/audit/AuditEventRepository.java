package com.relaycrm.audit;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every lookup is scoped by organization, like the record repositories. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByOrgIdAndEntityTypeAndEntityIdOrderByIdDesc(
            Long orgId, String entityType, Long entityId, Pageable pageable);

    boolean existsByOrgIdAndEntityTypeAndEntityId(Long orgId, String entityType, Long entityId);
}
