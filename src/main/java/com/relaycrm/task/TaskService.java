package com.relaycrm.task;

import java.util.List;
import java.util.Map;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.AccessPolicy;
import com.relaycrm.common.ApiErrors;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;
import com.relaycrm.lead.LeadRepository;
import com.relaycrm.opportunity.OpportunityRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasks through the API. Organization scoping, the edit and delete rules,
 * If-Match and the audit trail all come from OwnedRecordService. Two things
 * are specific to tasks: a rep sees only their own, and a finished task
 * cannot be reopened.
 */
@Service
public class TaskService extends OwnedRecordService<Task, TaskRequest> {

    private final TaskRepository tasks;
    private final OpportunityRepository opportunities;
    private final LeadRepository leads;

    public TaskService(TaskRepository tasks, UserRepository users, RequestReader requests, AuditLog audit,
                       OpportunityRepository opportunities, LeadRepository leads) {
        super(tasks, users, requests, audit, TaskRequest.class, "Task");
        this.tasks = tasks;
        this.opportunities = opportunities;
        this.leads = leads;
    }

    /**
     * Soonest due first. A rep gets their own tasks; managers and admins get
     * the whole organization's. status, opportunityId and leadId narrow the list.
     */
    @Transactional(readOnly = true)
    public List<Task> list(TaskStatus status, Long opportunityId, Long leadId, Pageable page) {
        CurrentUser user = CurrentUserContext.require();
        Long onlyOwner = AccessPolicy.canViewTasksOfOthers(user) ? null : user.id();
        // Only the page number and size come from the request; the order is fixed.
        return tasks.search(user.orgId(), onlyOwner, status, opportunityId, leadId,
                PageRequest.of(page.getPageNumber(), page.getPageSize()));
    }

    /** To a rep, someone else's task does not exist: 404 on read, update and history. */
    @Override
    protected boolean canView(CurrentUser user, Task task) {
        return AccessPolicy.canViewTasksOfOthers(user) || task.getOwnerId().equals(user.id());
    }

    @Override
    protected Task newEntity() {
        return new Task();   // source = MANUAL
    }

    @Override
    protected void apply(Task task, TaskRequest request, CurrentUser user) {
        if (request.opportunityId() != null && request.leadId() != null) {
            throw ApiErrors.invalidFields(Map.of("leadId", "a task is about an opportunity or a lead, not both"));
        }
        // A linked deal or lead must belong to the caller's organization.
        if (request.opportunityId() != null
                && !opportunities.existsByIdAndOrgId(request.opportunityId(), user.orgId())) {
            throw ApiErrors.badRequest("Opportunity " + request.opportunityId() + " not found");
        }
        if (request.leadId() != null && !leads.existsByIdAndOrgId(request.leadId(), user.orgId())) {
            throw ApiErrors.badRequest("Lead " + request.leadId() + " not found");
        }
        // Only a create can arrive without a status; updates are validated to carry one.
        TaskStatus current = task.getStatus();
        TaskStatus requested = request.status() != null ? request.status() : TaskStatus.OPEN;
        if (current != null && current != requested && !current.allowedNext().contains(requested)) {
            throw ApiErrors.invalidTransition("status", current, requested, current.allowedNext());
        }
        task.setTitle(request.title());
        task.setDueAt(request.dueAt());
        task.setStatus(requested);
        task.setOpportunityId(request.opportunityId());
        task.setLeadId(request.leadId());
    }

    @Override
    protected TaskRequest toRequest(Task task) {
        return TaskRequest.of(task);
    }
}
