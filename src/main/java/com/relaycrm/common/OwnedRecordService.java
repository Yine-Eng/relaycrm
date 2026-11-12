package com.relaycrm.common;

import java.util.List;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;
import com.relaycrm.auth.UserRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place where the access rules for contacts, leads and opportunities
 * are applied. Each operation does the same things in the same order:
 *
 *   1. scope by organization - a record outside the caller's organization is "not found" (404);
 *   2. check the role rule   - otherwise 403;
 *   3. do the work.
 */
@Transactional
public abstract class OwnedRecordService<E extends OwnedRecord, R extends OwnedRecordRequest> {

    private final OwnedRecordRepository<E> repository;
    private final UserRepository users;
    private final String label;

    protected OwnedRecordService(OwnedRecordRepository<E> repository, UserRepository users, String label) {
        this.repository = repository;
        this.users = users;
        this.label = label;
    }

    protected abstract E newEntity();

    /** Copy the type-specific fields from the request onto the entity. */
    protected abstract void apply(E entity, R request, CurrentUser user);

    @Transactional(readOnly = true)
    public List<E> list(Pageable pageable) {
        return repository.findByOrgId(CurrentUserContext.require().orgId(), pageable);
    }

    @Transactional(readOnly = true)
    public E get(Long id) {
        return load(id, CurrentUserContext.require());
    }

    public E create(R request) {
        CurrentUser user = CurrentUserContext.require();
        E entity = newEntity();
        entity.setOrgId(user.orgId());
        entity.setOwnerId(resolveOwner(user, request.ownerId(), user.id()));
        apply(entity, request, user);
        return repository.saveAndFlush(entity);
    }

    public E update(Long id, R request) {
        CurrentUser user = CurrentUserContext.require();
        E entity = load(id, user);
        if (!AccessPolicy.canEdit(user, entity)) {
            throw ApiErrors.forbidden("Reps can only edit records they own");
        }
        entity.setOwnerId(resolveOwner(user, request.ownerId(), entity.getOwnerId()));
        apply(entity, request, user);
        return repository.saveAndFlush(entity);
    }

    public void delete(Long id) {
        CurrentUser user = CurrentUserContext.require();
        if (!AccessPolicy.canDelete(user)) {
            throw ApiErrors.forbidden("Only admins can delete records");
        }
        repository.delete(load(id, user));
    }

    private E load(Long id, CurrentUser user) {
        return repository.findByIdAndOrgId(id, user.orgId())
                .orElseThrow(() -> ApiErrors.notFound(label, id));
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
