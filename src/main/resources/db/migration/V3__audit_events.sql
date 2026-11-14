-- Audit trail: one row per saved change to a contact, lead or opportunity.
-- The row is written in the same transaction as the change, so an event exists
-- only if the change was saved.

CREATE TABLE audit_events (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id        bigint NOT NULL REFERENCES organizations (id),
    actor_user_id bigint NOT NULL,                   -- who made the change
    entity_type   text   NOT NULL CHECK (entity_type IN ('CONTACT', 'LEAD', 'OPPORTUNITY')),
    entity_id     bigint NOT NULL,                   -- no foreign key: events outlive a deleted record
    action        text   NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    changes       jsonb  NOT NULL,                   -- {"stage": {"from": "PROPOSAL", "to": "WON"}}
    version_after bigint NOT NULL,                   -- the record's version after the change (for DELETE: its last version)
    created_at    timestamptz NOT NULL,
    FOREIGN KEY (org_id, actor_user_id) REFERENCES users (org_id, id)
);

-- A record's history, newest first.
CREATE INDEX audit_events_entity_idx ON audit_events (org_id, entity_type, entity_id, id DESC);

-- Row-level security: the same rule as contacts, leads and opportunities ----

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON audit_events
    USING      (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Append-only, enforced twice ----------------------------------------------
-- 1. The application's role may add and read events, nothing else.

GRANT SELECT, INSERT ON audit_events TO ${appUser};

-- 2. A trigger refuses UPDATE, DELETE and TRUNCATE for every role, including
--    the table owner. The triggers are per statement, not per row, so they
--    fire even when row-level security leaves the statement no rows to touch.
--    (A database superuser can still disable triggers; nothing inside the
--    database can prevent that.)

CREATE FUNCTION audit_events_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: % is not allowed', TG_OP;
END
$$;

CREATE TRIGGER audit_events_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_append_only();

CREATE TRIGGER audit_events_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_append_only();
