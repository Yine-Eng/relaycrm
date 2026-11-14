package com.relaycrm.opportunity;

import com.relaycrm.common.OwnedRecordRepository;

public interface OpportunityRepository extends OwnedRecordRepository<Opportunity> {

    /** How many deals of this organization link to the contact. */
    long countByOrgIdAndContactId(Long orgId, Long contactId);
}
