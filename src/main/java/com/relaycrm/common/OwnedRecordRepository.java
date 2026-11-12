package com.relaycrm.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Every lookup the services use is scoped by organization. */
@NoRepositoryBean
public interface OwnedRecordRepository<E extends OwnedRecord> extends JpaRepository<E, Long> {

    Optional<E> findByIdAndOrgId(Long id, Long orgId);

    List<E> findByOrgId(Long orgId, Pageable pageable);

    boolean existsByIdAndOrgId(Long id, Long orgId);
}
