# RelayCRM

Multi-tenant CRM core: a Spring Boot + PostgreSQL REST API where small sales teams track contacts, leads and
opportunities. Many companies (organizations) share one deployment, and one organization can never read or change
another's data.

## Run it

Needs JDK 21+ and Docker. Maven does not need to be installed: the `mvnw` wrapper downloads it on first use.

```bash
docker compose up -d --wait                                # PostgreSQL on localhost:5433
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo     # API on localhost:8080, with demo data
bash demo.sh                                               # live walkthrough (second terminal; Git Bash on Windows)
./mvnw verify                                              # build + integration tests, own throwaway PostgreSQL via Testcontainers
```

In PowerShell or cmd, use `.\mvnw.cmd` in place of `./mvnw`, and quote the profile argument:
`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`.

Every pull request, and every merge to `main`, runs `./mvnw verify` in GitHub Actions (`.github/workflows/ci.yml`).

`demo.sh` sends about 80 requests as different users and prints, for each, the expected and the actual HTTP status:
both organizations listing their own data, blocked cross-tenant reads, updates and deletes, each role rule, the
PUT / PATCH update rules, two people editing the same record, and the audit trail.

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

The same seven operations exist for `/api/contacts`, `/api/leads` and `/api/opportunities`. `GET /api/me` shows who
the caller is.

| Operation          | REP                | MANAGER | ADMIN | Record in another organization |
|--------------------|--------------------|---------|-------|--------------------------------|
| `GET /` (list)     | yes                | yes     | yes   | never listed                   |
| `GET /{id}`        | yes                | yes     | yes   | 404                            |
| `POST /`           | yes (owner = self) | yes     | yes   | not possible                   |
| `PUT /{id}`        | only if owner      | yes     | yes   | 404                            |
| `PATCH /{id}`      | only if owner      | yes     | yes   | 404                            |
| `DELETE /{id}`     | 403                | 403     | yes   | 404 (403 for non-admins)       |
| `GET /{id}/history` | yes               | yes     | yes   | 404                            |

### Updating a record: PUT or PATCH

| | `PUT /{id}` | `PATCH /{id}` |
|---|---|---|
| Meaning | Replace the whole record | Change only the fields sent |
| `Content-Type` | `application/json` | `application/merge-patch+json` (JSON Merge Patch, RFC 7396); anything else is 415 |
| Field left out | Optional field: cleared. Required field: 400 | Unchanged |
| Field sent as `null` | Optional field: cleared. Required field: 400 | Optional field: cleared. Required field: 400 |
| `If-Match` | Required: the version the update is based on | Required: the version the update is based on |

Example, for a contact that has a phone number and a company and is at version 0 (`If-Match` is explained in the
next section):

```bash
# Change the company; the name, email and phone stay as they are. The contact moves to version 1.
curl -X PATCH localhost:8080/api/contacts/1 -H "X-Api-Key: acme-admin-key" -H 'If-Match: "0"' \
     -H "Content-Type: application/merge-patch+json" -d '{"company": "Road Runner Group"}'

# Clear the phone number.
curl -X PATCH localhost:8080/api/contacts/1 -H "X-Api-Key: acme-admin-key" -H 'If-Match: "1"' \
     -H "Content-Type: application/merge-patch+json" -d '{"phone": null}'
```

- **Required fields.** Contact: `firstName`, `lastName`. Lead: `name`. Opportunity: `name`. A lead's `status` and
  an opportunity's `stage` may be left out on create (they default to `NEW` and `PROSPECTING`) but are required on
  update, so an update can never silently reset a won deal to a prospect.
- **PATCH is validated like PUT.** The patch is laid over the record's current values and the result must pass the
  same checks, then it is saved through the same code path, with the same permission rules. Changing `ownerId` by
  PATCH is therefore still for managers and admins only.
- **The owner is never cleared.** Every record has an owner. On PATCH, leaving `ownerId` out keeps the owner and
  `"ownerId": null` is a 400. On PUT, leaving `ownerId` out (or sending `null`) also keeps the current owner: this is
  the one exception to "PUT replaces everything", so that a client, and in particular a rep, does not have to
  repeat the owner on every update.
- **Unknown fields are rejected** with 400 on POST, PUT and PATCH: a typo such as `stauts`, or an attempt to send
  `orgId` or `id`. Silently ignoring them would let a client believe a change was made when it was not.
- **Every 400 names the fields**, for example
  `{"status": 400, "detail": "Invalid fields: lastName", "errors": {"lastName": "must not be blank"}}`.
- **Access is checked before the body.** A request for a record the caller may not edit gets 404 or 403 whatever the
  body or the `If-Match` header contains. The order is: access (404, 403), then the version (428, 412), then the
  body (400).

### Two people editing the same record: If-Match

Every record has a `version` number that starts at 0 and goes up by one each time the record is changed. It is in
the JSON body and in the `ETag` response header of `GET /{id}`, POST, PUT and PATCH, for example `ETag: "3"`.

A PUT or PATCH must send the version it is based on in the `If-Match` header:

| Request | Answer |
|---|---|
| `If-Match` is the record's current version | 200, with the new version in the body and in `ETag` |
| `If-Match` is an older (or any other) version | 412 Precondition Failed; the record is not changed |
| No `If-Match` header | 428 Precondition Required |
| Anything but one quoted number: `*`, `W/"3"`, `"1", "2"`, `"abc"` | 400 |

```bash
# 1. Read the record and note its ETag.
curl -i localhost:8080/api/contacts/1 -H "X-Api-Key: acme-admin-key"
#    HTTP/1.1 200
#    ETag: "0"

# 2. Save, saying which version the change is based on. The answer carries ETag: "1".
curl -i -X PATCH localhost:8080/api/contacts/1 -H "X-Api-Key: acme-admin-key" -H 'If-Match: "0"' \
     -H "Content-Type: application/merge-patch+json" -d '{"phone": "555-0111"}'

# 3. Someone else who also read version 0 now tries to save: 412, and the phone number above survives.
#    They read the record again (ETag: "1"), look at what changed, and retry with If-Match: "1".
curl -i -X PATCH localhost:8080/api/contacts/1 -H "X-Api-Key: acme-manager-key" -H 'If-Match: "0"' \
     -H "Content-Type: application/merge-patch+json" -d '{"company": "Road Runner Group"}'
```

- **The header is the only way to send the version.** `version` in a request body is an unknown field (400).
- **`If-Match: *` is refused.** In plain HTTP it means "whatever is there now", which would bring back the silent
  overwrite this feature exists to prevent.
- **An update that changes nothing keeps the version.** A PATCH with `{}`, or with the values the record already has,
  answers 200 with the same `version` and `ETag`: the record is the same, so its ETag is too.
- **Two saves at the very same moment** are both based on the current version and both pass the check above. The
  database decides: each UPDATE only applies `WHERE version = <the version read>`, so exactly one succeeds and
  the other gets the same 412.
- **DELETE takes no `If-Match`** (see Known gaps). Of two simultaneous deletes, the second gets 404.

Details that the brief left open, and what I chose:

- **Everyone can view every record in their own organization.** The brief restricts editing and deleting by role, not
  viewing.
- **Cross-tenant access answers 404, not 403**, with the same body as an id that does not exist, so a caller cannot
  probe which ids exist elsewhere.
- **Reassigning ownership** (the optional `ownerId` field) is for managers and admins only, and the new owner must be
  in the same organization. Otherwise a rep could hand a record to a colleague, or take over someone else's.
- **An opportunity's `contactId`** must be a contact of the same organization.
- Lists are paged (`?page=0&size=50`).
- Errors use the standard `application/problem+json` format.

### Audit trail: who changed what, and when

Every saved change to a contact, lead or opportunity writes one event. `GET /{id}/history` returns a record's
events, newest first, paged (`?page=0&size=50`).

```bash
# A deal moved from PROPOSAL to WON. Who did it, and what was it before?
curl localhost:8080/api/opportunities/101/history -H "X-Api-Key: acme-rep-rita-key"
```

```json
[
  {"id": 12, "action": "UPDATE", "actorUserId": 2, "entityType": "OPPORTUNITY", "entityId": 101,
   "changes": {"stage": {"from": "PROPOSAL", "to": "WON"}},
   "versionAfter": 1, "createdAt": "2026-11-14T10:15:30Z"},
  {"id": 11, "action": "CREATE", "actorUserId": 3, "entityType": "OPPORTUNITY", "entityId": 101,
   "changes": {"name": {"to": "Coyote - anvil contract"}, "stage": {"to": "PROPOSAL"}, "ownerId": {"to": 3}},
   "versionAfter": 0, "createdAt": "2026-11-14T10:02:11Z"}
]
```

Mark (user 2) moved it to WON; before that it was PROPOSAL, as Rita (user 3) created it.

- **What an event holds.** `CREATE`: every field that was given a value (`to`). `UPDATE`: only the fields that
  changed, with the old and the new value (`from`, `to`), including a change of owner. `DELETE`: the record's last
  values (`from`). `versionAfter` is the record's version after the change, the same number as its ETag.
- **An event exists only if the change was saved.** The event is written in the same database transaction as the
  change. A request that is refused (400, 403, 404, 412, 428) writes nothing, and so does an update that changes
  nothing. If the event itself cannot be written, the change is rolled back too.
- **Who can read a history.** While the record exists: everyone who can view the record, that is, everyone in its
  organization. After the record is deleted: managers and admins only, so that "who deleted this, and what was in
  it" can still be answered; a rep gets 404, as for the record itself. Another organization always gets 404.
- **Events cannot be changed or removed.** There is no API for it, and the database refuses it twice: the
  application's role is granted only `SELECT` and `INSERT` on `audit_events`, and a trigger raises an error on
  `UPDATE`, `DELETE` and `TRUNCATE` for every role, including the table's owner. A database superuser can still
  disable triggers, which nothing inside the database can prevent; protecting against that takes measures outside
  it (restricted superuser access, shipping events to separate write-once storage).
- **Other organizations' events are invisible** by the same row-level security policy as the records.
- **The seeded demo records have an empty history** until someone changes them: they were inserted by SQL, not
  through the API.

### Design decision: controllers take raw JSON

The POST, PUT and PATCH endpoints receive the body as raw JSON and pass it to `RequestReader`, instead of using
Spring's usual typed `@RequestBody` with `@Valid`. This gives strict unknown-field rejection for the three request
types only (not for JSON handling globally), and one path for all three verbs in which access is checked before the
body is read, for PUT and PATCH alike. Typed `@RequestBody` for POST and PUT was considered and rejected: PATCH
needs the raw body anyway, so it would have added a second error-handling path that must produce identical
responses, and PUT would have validated the body before checking access.

## How isolation is enforced

Isolation does not rest on a single check. There are three layers, and each one alone would stop a cross-tenant
request.

1. **Identity comes from the server.** `ApiKeyAuthFilter` looks the caller up by API key and takes the organization
   and role from the `users` table. No request field, header or path segment carries an organization id, so there is
   nothing to tamper with. Request bodies have no `orgId` field; one sent anyway is rejected with 400.

2. **Every query is scoped in the application.** The services can only reach records through
   `findByIdAndOrgId` / `findByOrgId` (`OwnedRecordRepository`). All three record types share one service base class,
   `OwnedRecordService`, so the sequence "scope by organization, then check the role, then act" is written once
   and is the only path to the data.

3. **PostgreSQL enforces it again** (`V1__schema.sql`):
   - *Row-level security.* Each transaction starts by setting `app.current_org` to the caller's organization
     (`TenantAwareTransactionManager`). A policy on each table hides, and refuses writes to, any row with a different
     `org_id`. So even a future query that forgets its `where org_id = ?` returns nothing from other tenants. If the
     setting is missing, no rows are visible at all. The audit trail (`audit_events`) is under the same policy.
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
  audit/        AuditEvent, AuditLog (works out what changed and writes the event), AuditEventRepository
  common/       OwnedRecord (base entity, with the version), OwnedRecordService (the access rules and the
                If-Match check), AccessPolicy, ApiErrors, RecordResponse (adds the ETag header),
                RequestReader (reads and validates POST / PUT / PATCH bodies)
  contact/      entity, request, repository, service, controller
  lead/         same
  opportunity/  same, plus the same-organization contact check
src/main/resources/db
  migration/V1__schema.sql    tables, composite foreign keys, row-level security, application role
  migration/V2__version.sql   version column for optimistic locking
  migration/V3__audit_events.sql   audit trail: table, row-level security, append-only grants and trigger
  demo/R__demo_data.sql       two organizations, six users, sample records ("demo" profile only)
src/test/java/com/relaycrm
  TenantIsolationTest         cross-tenant requests over HTTP, plus row-level security tested with raw SQL
  RolePermissionTest          role rules inside one organization
  UpdateSemanticsTest         PUT replacement, PATCH merging, required and unknown fields
  OptimisticLockingTest       If-Match: stale, missing and malformed versions, and two saves forced to race
  AuditTrailTest              one event per saved change, none otherwise; history visibility; append-only in SQL
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

- **Planned next: per-record visibility.** Today everyone can view every record in their organization. The next
  step is a visibility setting on each record: "organization" (the default, as now) or "restricted" (owner, managers
  and admins only). A restricted record would answer 404 to anyone else and never appear in their lists, and only
  the owner, a manager or an admin could change the setting. Sharing with named people or teams is deliberately
  left for later.
- No endpoints to manage organizations or users; they come from seed data.
- DELETE does not check If-Match, so a delete can remove a record someone just edited.
- Deleting a record keeps its last values in the audit history; a real erasure request would need an admin-only
  redaction path that blanks personal fields in past events.
- The audit trail can only be read per record (`/{id}/history`). There is no organization-wide feed, so the history
  of a deleted record can only be found by someone who already knows its id.
- Audit events name the actor by user id only, and record changes made through the API, not changes made directly
  in the database.
- No rate limiting.
- The `users` table is not under row-level security, because the login lookup runs before the organization is
  known. The API never exposes other organizations' users, but that rests on application code alone.
- Entities are returned directly as JSON. Request bodies are separate types, so callers cannot set fields such as
  `orgId`, but response DTOs would be the next step as the model grows.
- Database passwords in `application.yml` and `docker-compose.yml` are local-development defaults, overridable by
  environment variables (`DB_URL`, `DB_APP_USER`, `DB_APP_PASSWORD`, `DB_OWNER_USER`, `DB_OWNER_PASSWORD`).
