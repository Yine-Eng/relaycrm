-- RelayCRM: one-time setup of the application's database role on Neon.
--
-- Run ONCE, before the first deploy, in the Neon SQL Editor:
--   project "relaycrm", branch "production" (Neon's default branch), database "relaycrm",
--   as the project's owner role: the one shown under Roles in the Neon Console. Neon names it
--   after the database; for this project it is "relaycrm_owner".
--
-- Before running: replace REPLACE_WITH_DB_APP_PASSWORD below with the password you generated
-- for the app role, in the SQL Editor only. Do not save the real password in this file.
-- The same password goes into Render's DB_APP_PASSWORD environment variable.
--
-- Why a second role at all: the owner role runs the migrations and owns every table. The
-- application connects as relaycrm_app, which PostgreSQL's row-level security applies to.
-- This is the same arrangement as local development and the tests.


-- 1. The role the application logs in as.
--    LOGIN            it connects with a password (Render's DB_APP_USER / DB_APP_PASSWORD).
--    NOSUPERUSER      a superuser skips every row-level security policy.
--    NOBYPASSRLS      the attribute that skips row-level security without being a superuser.
--    NOCREATEDB, NOCREATEROLE, NOREPLICATION
--                     it never needs to create databases or roles, or read the replication stream.
--    NOINHERIT        it does not pick up the privileges of any role it might later be added to.
--    CONNECTION LIMIT the app keeps at most DB_POOL_SIZE (5 on Render) connections; 10 leaves room
--                     for a deploy where the old and new instance overlap.
--
--    Create it here, with SQL, not in the Neon Console: roles created in the Console are added to
--    Neon's neon_superuser role, which this one must not be a member of.
--    It owns nothing: every table is created later by the migrations, running as the owner role.
CREATE ROLE relaycrm_app WITH
    LOGIN
    PASSWORD 'REPLACE_WITH_DB_APP_PASSWORD'
    NOSUPERUSER
    NOBYPASSRLS
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOINHERIT
    CONNECTION LIMIT 10;

-- 2. Let it connect to this database.
GRANT CONNECT ON DATABASE relaycrm TO relaycrm_app;

-- 3. Let it see the objects in the public schema. This does not give access to any table: the
--    first migration (V1__schema.sql) grants SELECT, INSERT, UPDATE and DELETE on exactly the tables
--    it needs, and the later migrations add their own grants (the audit table gets only SELECT and
--    INSERT). The migration finds the role already present and does not create it again.
GRANT USAGE ON SCHEMA public TO relaycrm_app;

-- 4. Check. Expected: one row with superuser = false, bypass_rls = false, member_of = 0.
--    The application runs the same check (plus "owns no table") every time it starts, and
--    refuses to start if it fails.
SELECT r.rolname     AS role,
       r.rolsuper     AS superuser,
       r.rolbypassrls AS bypass_rls,
       r.rolcanlogin  AS can_login,
       (SELECT count(*) FROM pg_auth_members m WHERE m.member = r.oid) AS member_of
FROM pg_roles r
WHERE r.rolname = 'relaycrm_app';
