package com.relaycrm.common;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relaycrm.audit.AuditEvent;
import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;
import com.relaycrm.auth.UserRepository;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

/**
 * The one place where the access rules for contacts, leads and opportunities
 * are applied. Each operation does the same things in the same order:
 *
 *   1. scope by organization - a record outside the caller's organization is "not found" (404);
 *   2. check the role rule   - otherwise 403;
 *   3. for PUT and PATCH, check If-Match against the record's version - 428 if missing, 412 if stale;
 *   4. do the work (a rule of the record type may still refuse it: 400, or 409 for a stage move);
 *   5. record what changed in the audit trail, in the same transaction;
 *   6. let the record type react (afterSave, beforeDelete), still in the same transaction.
 *
 * PUT (update) and PATCH (patch) share steps 1 to 3 (loadForEdit) and the
 * final write (applyAndSave); they differ only in how the new values arrive.
 * Request bodies are read and validated by {@link RequestReader} after the
 * access checks, so a caller learns nothing about a record they may not edit.
 *
 * Any exception rolls the transaction back, so a request that fails leaves
 * neither a change nor an audit event behind.
 */
@Transactional
public abstract class OwnedRecordService<E extends OwnedRecord, R extends Record & OwnedRecordRequest> {

    /** The only accepted If-Match value: one quoted version number, such as "3". */
    private static final Pattern IF_MATCH = Pattern.compile("\"(\\d{1,18})\"");

    private final OwnedRecordRepository<E> repository;
    private final UserRepository users;
    private final RequestReader requests;
    private final AuditLog audit;
    private final Class<R> requestType;
    private final String label;
    /** How this record type is named in the audit trail: CONTACT, LEAD or OPPORTUNITY. */
    private final String entityType;

    protected OwnedRecordService(OwnedRecordRepository<E> repository, UserRepository users,
                                 RequestReader requests, AuditLog audit, Class<R> requestType, String label) {
        this.repository = repository;
        this.users = users;
        this.requests = requests;
        this.audit = audit;
        this.requestType = requestType;
        this.label = label;
        this.entityType = label.toUpperCase();
    }

    protected abstract E newEntity();

    /** Copy the type-specific fields from the request onto the entity. */
    protected abstract void apply(E entity, R request, CurrentUser user);

    /**
     * The entity's current values in request form: the starting point for a PATCH,
     * and the "before" and "after" that the audit trail compares.
     */
    protected abstract R toRequest(E entity);

    /**
     * May this user see this record of their own organization? Yes by default.
     * A record the user may not see is "not found" (404) everywhere.
     */
    protected boolean canView(CurrentUser user, E entity) {
        return true;
    }

    /**
     * Called after a create (before is null) or an update has been saved and
     * audited, inside the same transaction. An exception here undoes the save.
     */
    protected void afterSave(CurrentUser user, R before, E saved) {
    }

    /**
     * Called just before a record is deleted, inside the same transaction, with
     * the row locked. An exception here stops the delete.
     */
    protected void beforeDelete(CurrentUser user, E entity) {
    }

    @Transactional(readOnly = true)
    public List<E> list(Pageable pageable) {
        return repository.findByOrgId(CurrentUserContext.require().orgId(), pageable);
    }

    @Transactional(readOnly = true)
    public E get(Long id) {
        return load(id, CurrentUserContext.require());
    }

    public E create(JsonNode body) {
        CurrentUser user = CurrentUserContext.require();
        R request = requests.read(body, requestType);
        E entity = newEntity();
        entity.setOrgId(user.orgId());
        entity.setOwnerId(resolveOwner(user, request.ownerId(), user.id()));
        apply(entity, request, user);
        E saved = repository.saveAndFlush(entity);
        audit.created(user, entityType, saved.getId(), saved.getVersion(), toRequest(saved));
        afterSave(user, null, saved);
        return saved;
    }

    /** PUT: the body replaces the whole record; optional fields left out are cleared. */
    public E update(Long id, String ifMatch, JsonNode body) {
        CurrentUser user = CurrentUserContext.require();
        E entity = loadForEdit(id, user, ifMatch);
        R request = requests.read(body, requestType, OnUpdate.class);
        return applyAndSave(user, entity, request);
    }

    /** PATCH: fields in the patch replace the current values; everything else stays as it is. */
    public E patch(Long id, String ifMatch, JsonNode patch) {
        CurrentUser user = CurrentUserContext.require();
        E entity = loadForEdit(id, user, ifMatch);
        // A record always has an owner: leave ownerId out to keep it, but it cannot be cleared.
        if (patch != null && patch.has("ownerId") && patch.get("ownerId").isNull()) {
            throw ApiErrors.invalidFields(Map.of("ownerId", "must not be null"));
        }
        R merged = requests.merge(toRequest(entity), patch, requestType);
        return applyAndSave(user, entity, merged);
    }

    public void delete(Long id) {
        CurrentUser user = CurrentUserContext.require();
        if (!AccessPolicy.canDelete(user)) {
            throw ApiErrors.forbidden("Only admins can delete records");
        }
        // Lock the row and keep its last values for the audit trail. If someone else deleted the
        // record first, there is nothing to lock: 404, and no audit event.
        E entity = repository.findLockedByIdAndOrgId(id, user.orgId())
                .orElseThrow(() -> ApiErrors.notFound(label, id));
        beforeDelete(user, entity);
        // One DELETE statement, no version check.
        if (repository.deleteByIdInOrg(id, user.orgId()) == 0) {
            throw ApiErrors.notFound(label, id);
        }
        audit.deleted(user, entityType, id, entity.getVersion(), toRequest(entity));
    }

    /**
     * Who changed this record, when, and what: newest first. A record that still
     * exists shows its history to everyone in its organization. After it is
     * deleted, only managers and admins can read the history; for anyone else,
     * and for other organizations, it is "not found" like the record itself.
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> history(Long id, Pageable pageable) {
        CurrentUser user = CurrentUserContext.require();
        E existing = repository.findByIdAndOrgId(id, user.orgId()).orElse(null);
        boolean visible = existing != null
                ? canView(user, existing)
                : AccessPolicy.canViewHistoryOfDeletedRecords(user) && audit.hasEvents(user.orgId(), entityType, id);
        if (!visible) {
            throw ApiErrors.notFound(label, id);
        }
        return audit.history(user.orgId(), entityType, id, pageable);
    }

    private E load(Long id, CurrentUser user) {
        return repository.findByIdAndOrgId(id, user.orgId())
                .filter(entity -> canView(user, entity))
                .orElseThrow(() -> ApiErrors.notFound(label, id));
    }

    /** Access first (404, then 403), and only then the version the caller says they are editing (428, 400, 412). */
    private E loadForEdit(Long id, CurrentUser user, String ifMatch) {
        E entity = load(id, user);
        if (!AccessPolicy.canEdit(user, entity)) {
            throw ApiErrors.forbidden("Reps can only edit records they own");
        }
        if (ifMatch == null) {
            throw ApiErrors.preconditionRequired();
        }
        Matcher version = IF_MATCH.matcher(ifMatch.trim());
        if (!version.matches()) {
            throw ApiErrors.badRequest("If-Match must be one quoted version number, for example \"3\"");
        }
        if (Long.parseLong(version.group(1)) != entity.getVersion()) {
            throw ApiErrors.preconditionFailed();
        }
        return entity;
    }

    private E applyAndSave(CurrentUser user, E entity, R request) {
        R before = toRequest(entity);
        entity.setOwnerId(resolveOwner(user, request.ownerId(), entity.getOwnerId()));
        apply(entity, request, user);
        E saved;
        try {
            saved = repository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException e) {
            // Someone saved between our version check and our write: the database refused the stale update.
            throw ApiErrors.preconditionFailed();
        }
        // Writes an event only if a field really changed, so a no-op update leaves no trace.
        audit.updated(user, entityType, saved.getId(), saved.getVersion(), before, toRequest(saved));
        afterSave(user, before, saved);
        return saved;
    }

    /** The owner stays as it is unless the request names someone else, who must be a user of the same organization. */
    private Long resolveOwner(CurrentUser user, Long requested, Long current) {
        if (requested == null || requested.equals(current)) {
            return current;
        }
        if (!AccessPolicy.canAssignToOthers(user)) {
            throw ApiErrors.forbidden("Reps cannot assign records to other users");
        }
        if (!users.existsByIdAndOrgId(requested, user.orgId())) {
            throw ApiErrors.badRequest("Owner " + requested + " not found");
        }
        return requested;
    }
}
