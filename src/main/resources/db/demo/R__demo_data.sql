-- Demo data, loaded only with the "demo" profile. Safe to re-run.
-- API keys are stored hashed; the plain keys are listed in the README.

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

INSERT INTO contacts (id, org_id, owner_id, first_name, last_name, email, company, created_at, updated_at) VALUES
    (1, 1, 3, 'Carl',  'Customer', 'carl@roadrunner.test', 'Road Runner Ltd',     now(), now()),
    (2, 1, 4, 'Dana',  'Decider',  'dana@coyote.test',     'Coyote Supplies',     now(), now()),
    (3, 2, 6, 'Homer', 'Buyer',    'homer@plant.test',     'Springfield Nuclear', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO leads (id, org_id, owner_id, name, company, email, source, status, created_at, updated_at) VALUES
    (1, 1, 3, 'Wile E.', 'Coyote Supplies', 'wile@coyote.test',  'webinar',  'NEW',       now(), now()),
    (2, 2, 6, 'Hank S.', 'Scorpio Group',   'hank@scorpio.test', 'referral', 'CONTACTED', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO opportunities (id, org_id, owner_id, contact_id, name, amount, stage, close_date, created_at, updated_at) VALUES
    (1, 1, 3, 1, 'Road Runner - annual plan', 12000.00, 'PROPOSAL',    DATE '2027-01-31', now(), now()),
    (2, 2, 6, 3, 'Springfield - pilot',        4500.00, 'PROSPECTING', DATE '2027-02-28', now(), now())
ON CONFLICT DO NOTHING;

-- Keep generated ids clear of the fixed ids above.
SELECT setval(pg_get_serial_sequence('organizations', 'id'), GREATEST(100, (SELECT max(id) FROM organizations)));
SELECT setval(pg_get_serial_sequence('users', 'id'),         GREATEST(100, (SELECT max(id) FROM users)));
SELECT setval(pg_get_serial_sequence('contacts', 'id'),      GREATEST(100, (SELECT max(id) FROM contacts)));
SELECT setval(pg_get_serial_sequence('leads', 'id'),         GREATEST(100, (SELECT max(id) FROM leads)));
SELECT setval(pg_get_serial_sequence('opportunities', 'id'), GREATEST(100, (SELECT max(id) FROM opportunities)));
