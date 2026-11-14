package com.relaycrm.task;

import java.util.List;

import com.relaycrm.common.OwnedRecordRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends OwnedRecordRepository<Task> {

    /**
     * The task list: always one organization, soonest due first. Every other
     * filter is optional (null means "any").
     */
    @Query("""
            select t from Task t
            where t.orgId = :orgId
              and (:ownerId is null or t.ownerId = :ownerId)
              and (:status is null or t.status = :status)
              and (:opportunityId is null or t.opportunityId = :opportunityId)
              and (:leadId is null or t.leadId = :leadId)
            order by t.dueAt asc, t.id asc
            """)
    List<Task> search(@Param("orgId") Long orgId, @Param("ownerId") Long ownerId,
                      @Param("status") TaskStatus status, @Param("opportunityId") Long opportunityId,
                      @Param("leadId") Long leadId, Pageable page);

    List<Task> findByOrgIdAndOpportunityIdAndStatusOrderById(Long orgId, Long opportunityId, TaskStatus status);

    List<Task> findByOrgIdAndLeadIdAndStatusOrderById(Long orgId, Long leadId, TaskStatus status);
}
