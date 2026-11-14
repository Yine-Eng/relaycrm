package com.relaycrm.lead;

import java.time.Duration;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.ApiErrors;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;
import com.relaycrm.task.FollowUpTasks;
import com.relaycrm.task.TaskLink;

import org.springframework.stereotype.Service;

@Service
public class LeadService extends OwnedRecordService<Lead, LeadRequest> {

    private final FollowUpTasks followUps;

    public LeadService(LeadRepository leads, UserRepository users, RequestReader requests,
                       AuditLog audit, FollowUpTasks followUps) {
        super(leads, users, requests, audit, LeadRequest.class, "Lead");
        this.followUps = followUps;
    }

    @Override
    protected Lead newEntity() {
        return new Lead();
    }

    @Override
    protected void apply(Lead lead, LeadRequest request, CurrentUser user) {
        // Only a create can arrive without a status; updates are validated to carry one.
        LeadStatus current = lead.getStatus();   // null while the lead is being created
        LeadStatus requested = request.status() != null ? request.status() : LeadStatus.NEW;
        // A new lead may start at any status. An existing one may only follow the allowed moves;
        // sending the status it already has is not a move.
        if (current != null && current != requested && !current.allowedNext().contains(requested)) {
            throw ApiErrors.invalidTransition("status", current, requested, current.allowedNext());
        }
        lead.setName(request.name());
        lead.setCompany(request.company());
        lead.setEmail(request.email());
        lead.setSource(request.source());
        lead.setStatus(requested);
    }

    /** The follow-up rules. They run in the transaction that saved the lead: all of it is saved, or none. */
    @Override
    protected void afterSave(CurrentUser user, LeadRequest before, Lead saved) {
        TaskLink lead = TaskLink.lead(saved.getId());

        // A new owner takes over the lead's open automatic tasks.
        if (before != null && !before.ownerId().equals(saved.getOwnerId())) {
            followUps.moveOpenAutomaticTasks(user, lead, saved.getOwnerId());
        }

        LeadStatus was = before != null ? before.status() : null;
        LeadStatus now = saved.getStatus();
        if (was == now) {
            return;
        }
        // A disqualified lead needs no more automatic follow-ups.
        if (now == LeadStatus.DISQUALIFIED) {
            followUps.cancelOpen(user, lead, true);
        }
        // Becoming QUALIFIED (by a move, or by being created so): get in touch within a day,
        // unless an open task for that already exists.
        if (now == LeadStatus.QUALIFIED) {
            followUps.ensureOpen(user, lead, "qualified-contact",
                    "Contact qualified lead: " + saved.getName(), saved.getOwnerId(), Duration.ofDays(1));
        }
    }

    /** A deleted lead's open tasks are cancelled; the database then clears their link to it. */
    @Override
    protected void beforeDelete(CurrentUser user, Lead lead) {
        followUps.cancelOpen(user, TaskLink.lead(lead.getId()), false);
    }

    @Override
    protected LeadRequest toRequest(Lead lead) {
        return new LeadRequest(lead.getName(), lead.getCompany(), lead.getEmail(), lead.getSource(),
                lead.getStatus(), lead.getOwnerId());
    }
}
