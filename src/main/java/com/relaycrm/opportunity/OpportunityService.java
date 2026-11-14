package com.relaycrm.opportunity;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.ApiErrors;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;
import com.relaycrm.contact.ContactRepository;

import org.springframework.stereotype.Service;

@Service
public class OpportunityService extends OwnedRecordService<Opportunity, OpportunityRequest> {

    private final ContactRepository contacts;

    public OpportunityService(OpportunityRepository opportunities, UserRepository users, RequestReader requests,
                              AuditLog audit, ContactRepository contacts) {
        super(opportunities, users, requests, audit, OpportunityRequest.class, "Opportunity");
        this.contacts = contacts;
    }

    @Override
    protected Opportunity newEntity() {
        return new Opportunity();
    }

    @Override
    protected void apply(Opportunity opportunity, OpportunityRequest request, CurrentUser user) {
        // A linked contact must belong to the caller's organization.
        if (request.contactId() != null && !contacts.existsByIdAndOrgId(request.contactId(), user.orgId())) {
            throw ApiErrors.badRequest("Contact " + request.contactId() + " not found");
        }
        opportunity.setName(request.name());
        opportunity.setContactId(request.contactId());
        opportunity.setAmount(request.amount());
        // Only a create can arrive without a stage; updates are validated to carry one.
        opportunity.setStage(request.stage() != null ? request.stage() : OpportunityStage.PROSPECTING);
        opportunity.setCloseDate(request.closeDate());
    }

    @Override
    protected OpportunityRequest toRequest(Opportunity opportunity) {
        return new OpportunityRequest(opportunity.getName(), opportunity.getContactId(), opportunity.getAmount(),
                opportunity.getStage(), opportunity.getCloseDate(), opportunity.getOwnerId());
    }
}
