-- Optimistic locking: every update must name the version it was based on.
-- JPA (@Version on OwnedRecord) adds "AND version = ?" to each UPDATE and
-- increments the column, so a save based on stale data changes no row.
-- Existing rows start at version 0.

ALTER TABLE contacts      ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE leads         ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE opportunities ADD COLUMN version bigint NOT NULL DEFAULT 0;
