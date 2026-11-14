package com.relaycrm.task;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The automatic side of tasks: what happens to a deal's or a lead's tasks
 * when that deal or lead changes. The opportunity and lead services call this
 * from inside their own transaction (MANDATORY: it refuses to run without
 * one), so the stage change and its tasks are saved together or not at all.
 * Everything done here is written to the audit trail, with the person who
 * changed the deal or lead as the actor.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class FollowUpTasks {

    private static final String TASK = "TASK";

    /**
     * Inserts an OPEN automatic task unless the organization already has an
     * OPEN task with the same dedupe key. The decision is made by the database
     * (the partial unique index tasks_one_open_per_key), not by a check in this
     * code, so it also holds when two transactions try at the same moment: the
     * second waits for the first to finish and then inserts nothing.
     */
    private static final String INSERT_UNLESS_OPEN = """
            insert into tasks (org_id, owner_id, title, due_at, status, source,
                               opportunity_id, lead_id, dedupe_key, created_at, updated_at, version)
            values (?, ?, ?, ?, 'OPEN', 'AUTO', ?, ?, ?, ?, ?, 0)
            on conflict (org_id, dedupe_key) where status = 'OPEN' do nothing
            returning id
            """;

    private final TaskRepository tasks;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FollowUpTasks(TaskRepository tasks, AuditLog audit, DataSource dataSource, Clock clock) {
        this.tasks = tasks;
        this.audit = audit;
        this.jdbc = new JdbcTemplate(dataSource);
        this.clock = clock;
    }

    /**
     * Makes sure the record has an open follow-up task for this rule.
     * Returns the new task's id, or empty if an open one already existed.
     */
    public Optional<Long> ensureOpen(CurrentUser actor, TaskLink link, String rule, String title,
                                     Long ownerId, Duration dueIn) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<Long> inserted = jdbc.queryForList(INSERT_UNLESS_OPEN, Long.class,
                actor.orgId(), ownerId, title, now.plus(dueIn),
                link.opportunityId(), link.leadId(), link.dedupeKey(rule), now, now);
        if (inserted.isEmpty()) {
            return Optional.empty();
        }
        Task task = tasks.findById(inserted.get(0)).orElseThrow();
        audit.created(actor, TASK, task.getId(), task.getVersion(), TaskRequest.of(task));
        return Optional.of(task.getId());
    }

    /**
     * Cancels the record's open tasks: only the automatic ones (the deal was won
     * or lost, the lead disqualified), or all of them (the record is being deleted).
     */
    public void cancelOpen(CurrentUser actor, TaskLink link, boolean automaticOnly) {
        for (Task task : openTasks(actor, link)) {
            if (automaticOnly && task.getSource() != TaskSource.AUTO) {
                continue;
            }
            TaskRequest before = TaskRequest.of(task);
            task.setStatus(TaskStatus.CANCELLED);
            saveAndAudit(actor, task, before);
        }
    }

    /** The record has a new owner: its open automatic tasks follow it. */
    public void moveOpenAutomaticTasks(CurrentUser actor, TaskLink link, Long newOwnerId) {
        for (Task task : openTasks(actor, link)) {
            if (task.getSource() != TaskSource.AUTO || task.getOwnerId().equals(newOwnerId)) {
                continue;
            }
            TaskRequest before = TaskRequest.of(task);
            task.moveTo(newOwnerId);
            saveAndAudit(actor, task, before);
        }
    }

    private List<Task> openTasks(CurrentUser actor, TaskLink link) {
        return link.kind() == TaskLink.Kind.OPPORTUNITY
                ? tasks.findByOrgIdAndOpportunityIdAndStatusOrderById(actor.orgId(), link.id(), TaskStatus.OPEN)
                : tasks.findByOrgIdAndLeadIdAndStatusOrderById(actor.orgId(), link.id(), TaskStatus.OPEN);
    }

    private void saveAndAudit(CurrentUser actor, Task task, TaskRequest before) {
        Task saved = tasks.saveAndFlush(task);
        audit.updated(actor, TASK, saved.getId(), saved.getVersion(), before, TaskRequest.of(saved));
    }
}
