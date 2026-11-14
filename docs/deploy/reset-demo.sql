-- RelayCRM: reset the public demo to its seed data.
--
-- Run in the Neon SQL Editor (database "relaycrm"), as the owner role. Then restart the Render
-- service (Manual Deploy > Restart service): on start, Flyway finds an empty schema, runs every
-- migration again and loads the demo seed data.
--
-- Why drop the whole schema instead of deleting rows: the audit trail is append-only by design.
-- A trigger refuses DELETE and TRUNCATE on audit_events for every role, the owner included.
-- Dropping the table (with its schema) is the one way to start over, and it is deliberate.
--
-- The app role (relaycrm_app) is not touched: roles belong to the Neon project, not the schema.
-- Its password stays the same.

BEGIN;

-- Every table, sequence, function, trigger and policy of the app, and Flyway's own history table.
DROP SCHEMA public CASCADE;

-- An empty public schema again, owned by the role running this script (the owner role).
CREATE SCHEMA public;

-- Give the app role back what neon-setup.sql gave it on the schema; the migrations grant the rest.
GRANT USAGE ON SCHEMA public TO relaycrm_app;

COMMIT;
