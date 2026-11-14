package com.relaycrm.opportunity;

import java.time.Duration;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.ApiErrors;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;
import com.relaycrm.contact.ContactRepository;
import com.relaycrm.task.FollowUpTasks;
import com.relaycrm.task.TaskLink;

import org.springframework.stereotype.Service;

@Service
public class OpportunityService extends OwnedRecordService<Opportunity, OpportunityRequest> {

    private final ContactRepository contacts;
    private final FollowUpTasks followUps;

    public OpportunityService(OpportunityRepository opportunities, UserRepository users, RequestReader requests,
                              AuditLog audit, ContactRepository contacts, FollowUpTasks followUps) {
        super(opportunities, users, requests, audit, OpportunityRequest.class, "Opportunity");
        this.contacts = contacts;
        this.followUps = followUps;
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
        // Only a create can arrive without a stage; updates are validated to carry one.
        OpportunityStage current = opportunity.getStage();   // null while the deal is being created
        OpportunityStage requested = request.stage() != null ? request.stage() : OpportunityStage.PROSPECTING;
        // A new deal may start at any stage. An existing one may only follow the pipeline;
        // sending the stage it already has is not a move.
        if (current != null && current != requested && !current.allowedNext().contains(requested)) {
            throw ApiErrors.invalidTransition("stage", current, requested, current.allowedNext());
        }
        opportunity.setName(request.name());
        opportunity.setContactId(request.contactId());
        opportunity.setAmount(request.amount());
        opportunity.setStage(requested);
        opportunity.setCloseDate(request.closeDate());
    }

    /** The follow-up rules. They run in the transaction that saved the deal: all of it is saved, or none. */
    @Override
    protected void afterSave(CurrentUser user, OpportunityRequest before, Opportunity saved) {
        TaskLink deal = TaskLink.opportunity(saved.getId());

        // A new owner takes over the deal's open automatic tasks.
        if (before != null && !before.ownerId().equals(saved.getOwnerId())) {
            followUps.moveOpenAutomaticTasks(user, deal, saved.getOwnerId());
        }

        OpportunityStage was = before != null ? before.stage() : null;
        OpportunityStage now = saved.getStage();
        if (was == now) {
            return;
        }
        // A closed deal needs no more automatic follow-ups.
        if (now.isClosed()) {
            followUps.cancelOpen(user, deal, true);
        }
        // Entering PROPOSAL (by a move, or by being created there): follow up in 3 days,
        // unless an open follow-up for this deal already exists.
        if (now == OpportunityStage.PROPOSAL) {
            followUps.ensureOpen(user, deal, "proposal-follow-up",
                    "Follow up on proposal: " + saved.getName(), saved.getOwnerId(), Duration.ofDays(3));
        }
    }

    /** A deleted deal's open tasks are cancelled; the database then clears their link to it. */
    @Override
    protected void beforeDelete(CurrentUser user, Opportunity opportunity) {
        followUps.cancelOpen(user, TaskLink.opportunity(opportunity.getId()), false);
    }

    @Override
    protected OpportunityRequest toRequest(Opportunity opportunity) {
        return new OpportunityRequest(opportunity.getName(), opportunity.getContactId(), opportunity.getAmount(),
                opportunity.getStage(), opportunity.getCloseDate(), opportunity.getOwnerId());
    }
}
