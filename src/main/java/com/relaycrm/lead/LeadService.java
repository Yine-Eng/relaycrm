package com.relaycrm.lead;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;

import org.springframework.stereotype.Service;

@Service
public class LeadService extends OwnedRecordService<Lead, LeadRequest> {

    public LeadService(LeadRepository leads, UserRepository users, RequestReader requests,
                       AuditLog audit) {
        super(leads, users, requests, audit, LeadRequest.class, "Lead");
    }

    @Override
    protected Lead newEntity() {
        return new Lead();
    }

    @Override
    protected void apply(Lead lead, LeadRequest request, CurrentUser user) {
        lead.setName(request.name());
        lead.setCompany(request.company());
        lead.setEmail(request.email());
        lead.setSource(request.source());
        // Only a create can arrive without a status; updates are validated to carry one.
        lead.setStatus(request.status() != null ? request.status() : LeadStatus.NEW);
    }

    @Override
    protected LeadRequest toRequest(Lead lead) {
        return new LeadRequest(lead.getName(), lead.getCompany(), lead.getEmail(), lead.getSource(),
                lead.getStatus(), lead.getOwnerId());
    }
}
