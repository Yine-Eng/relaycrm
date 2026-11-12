# RelayCRM

Multi-tenant CRM core: a Spring Boot + PostgreSQL REST API where small sales teams track contacts, leads and
opportunities. Many companies (organizations) share one deployment, and one organization can never read or change
another's data.

## Run it

Needs JDK 21+, Maven and Docker.

```bash
docker compose up -d --wait                             # PostgreSQL on localhost:5433
mvn spring-boot:run -Dspring-boot.run.profiles=demo     # API on localhost:8080, with demo data
bash demo.sh                                            # live walkthrough (second terminal; Git Bash on Windows)
mvn test                                                # integration tests, own throwaway PostgreSQL via Testcontainers
```

`demo.sh` sends about 35 requests as different users and prints, for each, the expected and the actual HTTP status:
both organizations listing their own data, blocked cross-tenant reads, updates and deletes, and each role rule.

### Demo users

Send the key in the `X-Api-Key` header, for example
`curl -H "X-Api-Key: globex-admin-key" localhost:8080/api/contacts/1` (returns 404: contact 1 is Acme's).

| Organization   | User (id) | Role    | API key             |
|----------------|-----------|---------|---------------------|
| Acme Corp (1)  | Alice (1) | ADMIN   | `acme-admin-key`    |
| Acme Corp (1)  | Mark (2)  | MANAGER | `acme-manager-key`  |
| Acme Corp (1)  | Rita (3)  | REP     | `acme-rep-rita-key` |
| Acme Corp (1)  | Ravi (4)  | REP     | `acme-rep-ravi-key` |
| Globex Inc (2) | Gina (5)  | ADMIN   | `globex-admin-key`  |
| Globex Inc (2) | Greg (6)  | REP     | `globex-rep-key`    |

## API

The same five operations exist for `/api/contacts`, `/api/leads` and `/api/opportunities`. `GET /api/me` shows who
the caller is.

| Operation          | REP                | MANAGER | ADMIN | Record in another organization |
|--------------------|--------------------|---------|-------|--------------------------------|
| `GET /` (list)     | yes                | yes     | yes   | never listed                   |
| `GET /{id}`        | yes                | yes     | yes   | 404                            |
| `POST /`           | yes (owner = self) | yes     | yes   | not possible                   |
| `PUT /{id}`        | only if owner      | yes     | yes   | 404                            |
| `DELETE /{id}`     | 403                | 403     | yes   | 404 (403 for non-admins)       |

Details that the brief left open, and what I chose:

- **Everyone can view every record in their own organization.** The brief restricts editing and deleting by role, not
  viewing.
- **Cross-tenant access answers 404, not 403**, with the same body as an id that does not exist, so a caller cannot
  probe which ids exist elsewhere.
- **Reassigning ownership** (the optional `ownerId` field) is for managers and admins only, and the new owner must be
  in the same organization. Otherwise a rep could hand a record to a colleague, or take over someone else's.
- **An opportunity's `contactId`** must be a contact of the same organization.
- `PUT` replaces the whole record: fields left out become null. Lists are paged (`?page=0&size=50`).
- Errors use the standard `application/problem+json` format.

## How isolation is enforced

Isolation does not rest on a single check. There are three layers, and each one alone would stop a cross-tenant
request.

1. **Identity comes from the server.** `ApiKeyAuthFilter` looks the caller up by API key and takes the organization
   and role from the `users` table. No request field, header or path segment carries an organization id, so there is
   nothing to tamper with. Request bodies have no `orgId` field; one sent anyway is ignored.

2. **Every query is scoped in the application.** The services can only reach records through
   `findByIdAndOrgId` / `findByOrgId` (`OwnedRecordRepository`). All three record types share one service base class,
   `OwnedRecordService`, so the sequence "scope by organization, then check the role, then act" is written once
   and is the only path to the data.

3. **PostgreSQL enforces it again** (`V1__schema.sql`):
   - *Row-level security.* Each transaction starts by setting `app.current_org` to the caller's organization
     (`TenantAwareTransactionManager`). A policy on each table hides, and refuses writes to, any row with a different
     `org_id`. So even a future query that forgets its `where org_id = ?` returns nothing from other tenants. If the
     setting is missing, no rows are visible at all.
   - *A restricted database role.* The application connects as `relaycrm_app`, which is neither a superuser nor the
     table owner, so the policies apply to it. Migrations run as a separate owner role. The application refuses to
     start if its role could bypass row-level security.
   - *Composite foreign keys.* `(org_id, owner_id)` and `(org_id, contact_id)` reference `(org_id, id)`, so a record
     cannot point at a user or contact of another organization even if application code allowed it.

The role rules live in one small class, `AccessPolicy`, and are applied after the organization scoping.

## Code map

```
src/main/java/com/relaycrm
  auth/         ApiKeyAuthFilter (stub login), CurrentUser, CurrentUserContext, User, Role
  tenancy/      TenantAwareTransactionManager (sets app.current_org), startup check on the database role
  common/       OwnedRecord (base entity), OwnedRecordService (the access rules), AccessPolicy, ApiErrors
  contact/      entity, request, repository, service, controller
  lead/         same
  opportunity/  same, plus the same-organization contact check
src/main/resources/db
  migration/V1__schema.sql    tables, composite foreign keys, row-level security, application role
  demo/R__demo_data.sql       two organizations, six users, sample records ("demo" profile only)
src/test/java/com/relaycrm
  TenantIsolationTest         cross-tenant requests over HTTP, plus row-level security tested with raw SQL
  RolePermissionTest          role rules inside one organization
demo.sh                       live walkthrough
```

## Authentication: stub now, and in production

Now: a static API key per user in the `X-Api-Key` header, stored as a SHA-256 hash. It is enough to give every
request a trustworthy user, organization and role, which is all the rest of the system depends on.

In production I would:

- Use Spring Security as an OAuth2 resource server, validating short-lived JWT access tokens from an identity
  provider (OIDC: Keycloak, Auth0, Entra ID and similar), with SSO per organization.
- Still resolve organization and role on the server from the token's subject (or from signed claims the identity
  provider controls), never from anything the client can set.
- Replace only `ApiKeyAuthFilter`. Everything downstream reads `CurrentUser`, so the services, the transaction
  manager and the database policies stay as they are.

## Known gaps

- No endpoints to manage organizations or users; they come from seed data.
- No optimistic locking: two simultaneous edits of one record are last-write-wins.
- No audit trail of who changed what, and no rate limiting.
- The `users` table is not under row-level security, because the login lookup runs before the organization is
  known. The API never exposes other organizations' users, but that rests on application code alone.
- Entities are returned directly as JSON. Request bodies are separate types, so callers cannot set fields such as
  `orgId`, but response DTOs would be the next step as the model grows.
- Database passwords in `application.yml` and `docker-compose.yml` are local-development defaults, overridable by
  environment variables (`DB_URL`, `DB_APP_USER`, `DB_APP_PASSWORD`, `DB_OWNER_USER`, `DB_OWNER_PASSWORD`).
