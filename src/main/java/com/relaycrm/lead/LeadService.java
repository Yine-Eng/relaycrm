package com.relaycrm.lead;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.OwnedRecordService;

import org.springframework.stereotype.Service;

@Service
public class LeadService extends OwnedRecordService<Lead, LeadRequest> {

    public LeadService(LeadRepository leads, UserRepository users) {
        super(leads, users, "Lead");
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
        lead.setStatus(request.status() != null ? request.status() : LeadStatus.NEW);
    }
}
