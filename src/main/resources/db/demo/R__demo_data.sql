-- Demo data, loaded only with the "demo" profile. Safe to re-run.
-- API keys are stored hashed; the plain keys are listed in the README.
--
-- Contacts, leads and opportunities are under row-level security, and FORCE makes it apply to
-- the role running this script too, unless that role is a superuser. Locally and in the tests it
-- is one; on a hosted database (Neon) it is not. So each organization's rows are inserted with
-- app.current_org set to that organization, exactly as the application does it. The setting is
-- local to Flyway's transaction for this script.

INSERT INTO organizations (id, name) VALUES
    (1, 'Acme Corp'),
    (2, 'Globex Inc')
ON CONFLICT DO NOTHING;

INSERT INTO users (id, org_id, email, name, role, api_key_hash) VALUES
    (1, 1, 'alice@acme.test',  'Alice Admin',  'ADMIN',   encode(sha256('acme-admin-key'::bytea), 'hex')),
    (2, 1, 'mark@acme.test',   'Mark Manager', 'MANAGER', encode(sha256('acme-manager-key'::bytea), 'hex')),
    (3, 1, 'rita@acme.test',   'Rita Rep',     'REP',     encode(sha256('acme-rep-rita-key'::bytea), 'hex')),
    (4, 1, 'ravi@acme.test',   'Ravi Rep',     'REP',     encode(sha256('acme-rep-ravi-key'::bytea), 'hex')),
    (5, 2, 'gina@globex.test', 'Gina Admin',   'ADMIN',   encode(sha256('globex-admin-key'::bytea), 'hex')),
    (6, 2, 'greg@globex.test', 'Greg Rep',     'REP',     encode(sha256('globex-rep-key'::bytea), 'hex'))
ON CONFLICT DO NOTHING;

-- Acme Corp (organization 1) ------------------------------------------------
SELECT set_config('app.current_org', '1', true);

INSERT INTO contacts (id, org_id, owner_id, first_name, last_name, email, company, created_at, updated_at) VALUES
    (1, 1, 3, 'Carl',  'Customer', 'carl@roadrunner.test', 'Road Runner Ltd',     now(), now()),
    (2, 1, 4, 'Dana',  'Decider',  'dana@coyote.test',     'Coyote Supplies',     now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO leads (id, org_id, owner_id, name, company, email, source, status, created_at, updated_at) VALUES
    (1, 1, 3, 'Wile E.', 'Coyote Supplies', 'wile@coyote.test',  'webinar',  'NEW',       now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO opportunities (id, org_id, owner_id, contact_id, name, amount, stage, close_date, created_at, updated_at) VALUES
    (1, 1, 3, 1, 'Road Runner - annual plan', 12000.00, 'PROPOSAL',    DATE '2027-01-31', now(), now())
ON CONFLICT DO NOTHING;

-- Globex Inc (organization 2) -----------------------------------------------
SELECT set_config('app.current_org', '2', true);

INSERT INTO contacts (id, org_id, owner_id, first_name, last_name, email, company, created_at, updated_at) VALUES
    (3, 2, 6, 'Homer', 'Buyer',    'homer@plant.test',     'Springfield Nuclear', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO leads (id, org_id, owner_id, name, company, email, source, status, created_at, updated_at) VALUES
    (2, 2, 6, 'Hank S.', 'Scorpio Group',   'hank@scorpio.test', 'referral', 'CONTACTED', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO opportunities (id, org_id, owner_id, contact_id, name, amount, stage, close_date, created_at, updated_at) VALUES
    (2, 2, 6, 3, 'Springfield - pilot',        4500.00, 'PROSPECTING', DATE '2027-02-28', now(), now())
ON CONFLICT DO NOTHING;

SELECT set_config('app.current_org', '', true);

-- Keep generated ids clear of the fixed ids above, and never move a sequence backwards: when this
-- script runs again on a database in use, the ids already handed out must not be handed out again.
-- (max(id) alone is not enough: under row-level security, without an organization set, it sees no
-- rows on the tables above; the sequence's own last value always counts.)
SELECT setval(pg_get_serial_sequence('organizations', 'id'),
              GREATEST(100, (SELECT max(id) FROM organizations), pg_sequence_last_value(pg_get_serial_sequence('organizations', 'id')::regclass)));
SELECT setval(pg_get_serial_sequence('users', 'id'),
              GREATEST(100, (SELECT max(id) FROM users), pg_sequence_last_value(pg_get_serial_sequence('users', 'id')::regclass)));
SELECT setval(pg_get_serial_sequence('contacts', 'id'),
              GREATEST(100, (SELECT max(id) FROM contacts), pg_sequence_last_value(pg_get_serial_sequence('contacts', 'id')::regclass)));
SELECT setval(pg_get_serial_sequence('leads', 'id'),
              GREATEST(100, (SELECT max(id) FROM leads), pg_sequence_last_value(pg_get_serial_sequence('leads', 'id')::regclass)));
SELECT setval(pg_get_serial_sequence('opportunities', 'id'),
              GREATEST(100, (SELECT max(id) FROM opportunities), pg_sequence_last_value(pg_get_serial_sequence('opportunities', 'id')::regclass)));
