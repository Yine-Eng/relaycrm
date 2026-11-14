package com.relaycrm.audit;

import java.util.List;

import com.relaycrm.auth.CurrentUser;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes and reads the audit trail. Every write joins the caller's
 * transaction (MANDATORY: it refuses to run without one), so the event and
 * the change it describes are saved together or not at all.
 *
 * A record's values are passed in "request form" (ContactRequest, LeadRequest,
 * OpportunityRequest): exactly the fields a client can set, including ownerId.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AuditLog {

    private final AuditEventRepository events;
    private final ObjectMapper mapper;

    public AuditLog(AuditEventRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    /** CREATE: every field that was given a value, as {"field": {"to": value}}. */
    public void created(CurrentUser actor, String entityType, Long entityId, long version, Record values) {
        ObjectNode changes = mapper.createObjectNode();
        fields(values).properties().forEach(field -> {
            if (!field.getValue().isNull()) {
                changes.putObject(field.getKey()).set("to", field.getValue());
            }
        });
        write(actor, entityType, entityId, AuditAction.CREATE, changes, version);
    }

    /**
     * UPDATE: only the fields whose value changed, as {"field": {"from": old, "to": new}}.
     * If nothing changed, no event is written.
     */
    public void updated(CurrentUser actor, String entityType, Long entityId, long version,
                        Record before, Record after) {
        ObjectNode old = fields(before);
        ObjectNode changes = mapper.createObjectNode();
        fields(after).properties().forEach(field -> {
            JsonNode from = old.get(field.getKey());
            JsonNode to = field.getValue();
            if (!sameValue(from, to)) {
                ObjectNode change = changes.putObject(field.getKey());
                change.set("from", from);
                change.set("to", to);
            }
        });
        if (!changes.isEmpty()) {
            write(actor, entityType, entityId, AuditAction.UPDATE, changes, version);
        }
    }

    /** DELETE: the record's last values, as {"field": {"from": value}}. */
    public void deleted(CurrentUser actor, String entityType, Long entityId, long lastVersion, Record values) {
        ObjectNode changes = mapper.createObjectNode();
        fields(values).properties().forEach(field -> {
            if (!field.getValue().isNull()) {
                changes.putObject(field.getKey()).set("from", field.getValue());
            }
        });
        write(actor, entityType, entityId, AuditAction.DELETE, changes, lastVersion);
    }

    public boolean hasEvents(Long orgId, String entityType, Long entityId) {
        return events.existsByOrgIdAndEntityTypeAndEntityId(orgId, entityType, entityId);
    }

    /** Newest first. Only the page number and size are taken from the request, never a sort order. */
    public List<AuditEvent> history(Long orgId, String entityType, Long entityId, Pageable page) {
        return events.findByOrgIdAndEntityTypeAndEntityIdOrderByIdDesc(orgId, entityType, entityId,
                PageRequest.of(page.getPageNumber(), page.getPageSize()));
    }

    private void write(CurrentUser actor, String entityType, Long entityId, AuditAction action,
                       ObjectNode changes, long version) {
        events.saveAndFlush(new AuditEvent(actor.orgId(), actor.id(), entityType, entityId, action,
                changes.toString(), version));
    }

    private ObjectNode fields(Record values) {
        return mapper.valueToTree(values);
    }

    /** Numbers are compared by value, so an amount of 3000 and one of 3000.00 are the same. */
    private static boolean sameValue(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        return a.equals(b);
    }
}
