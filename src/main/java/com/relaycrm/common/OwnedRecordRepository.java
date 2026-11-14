package com.relaycrm.common;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.NoRepositoryBean;

/** Every lookup the services use is scoped by organization. */
@NoRepositoryBean
public interface OwnedRecordRepository<E extends OwnedRecord> extends JpaRepository<E, Long> {

    Optional<E> findByIdAndOrgId(Long id, Long orgId);

    /**
     * Loads the record and locks its row (SELECT ... FOR UPDATE) until the
     * transaction ends, so nobody can change or delete it in the meantime.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<E> findLockedByIdAndOrgId(Long id, Long orgId);

    List<E> findByOrgId(Long orgId, Pageable pageable);

    boolean existsByIdAndOrgId(Long id, Long orgId);

    /**
     * Deletes with one statement and returns how many rows went (0 or 1). It does
     * not compare versions: of two simultaneous deletes, the second finds nothing.
     */
    @Modifying
    @Query("delete from #{#entityName} e where e.id = :id and e.orgId = :orgId")
    int deleteByIdInOrg(@Param("id") Long id, @Param("orgId") Long orgId);
}
