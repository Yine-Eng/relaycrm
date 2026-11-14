# Deploying the public demo

The public demo runs on three free services:

| Part | Service | What runs there |
|---|---|---|
| Database | Neon (Postgres 17, AWS US East 2, Ohio) | Project `relaycrm`, database `relaycrm` |
| API | Render (free web service, Docker, Ohio) | The `Dockerfile` at the repository root |
| Web app | Netlify | `frontend/`, built with `frontend/netlify.toml` |

The browser only talks to Netlify. Netlify forwards every `/api/...` request to Render, so the API needs no CORS
setup and the site and API share one origin. Render talks to Neon over TLS.

Do the steps in this order: the database first, then the API, then the web app.

## Secrets: where each one goes

There are two passwords. Neither is ever written into a file in this repository.

| Secret | Where it comes from | Where it goes |
|---|---|---|
| Owner role password | Neon creates it with the project, for the owner role shown under **Roles** | Render, environment variable `DB_OWNER_PASSWORD` |
| App role password | You generate it (a password manager, or `openssl rand -base64 32`) | 1. The Neon SQL Editor, once, typed over the placeholder in `neon-setup.sql` (do not save the file). 2. Render, environment variable `DB_APP_PASSWORD` |

The demo API keys (`acme-admin-key` and the others) are not secrets: they are the published demo keys from the
seed data, and the public site needs them in its bundle.

## 1. Neon: the database

The project `relaycrm` (Postgres 17, AWS US East 2) with the database `relaycrm` already exists.

1. **Find the direct connection details.** In the Neon Console, open the project, then **Connect**. Choose branch
   `production` (Neon's default branch), database `relaycrm`, and the owner role. Turn **Connection pooling off**:
   the host must not contain
   `-pooler`. Pooled connections break the migrations, which hold a lock for the whole session. Note:
   - the host, for example `ep-example-123456.us-east-2.aws.neon.tech`;
   - the owner role's name and its password: the owner role shown under **Roles** in the Neon Console (Neon names it after the database; here it is `relaycrm_owner`).
2. **Create the app role.** Open the **SQL Editor** (branch `production`, database `relaycrm`, as the owner role). Paste
   the contents of [`neon-setup.sql`](neon-setup.sql), replace `REPLACE_WITH_DB_APP_PASSWORD` with the app password
   you generated, and run it. The last statement prints one row: `superuser = f`, `bypass_rls = f`,
   `member_of = 0`. Each statement is explained in the file.
   - Create this role with SQL, not on the Console's **Roles** page: roles created there are added to
     `neon_superuser`.
   - If Neon rejects the password as too weak, generate a longer one.
3. **Do not create any tables.** The API creates them on its first start, as the owner role.

## 2. Render: the API

1. **New > Web Service**, connect the GitHub repository, branch `main`.
2. Settings:

   | Setting | Value |
   |---|---|
   | Language / runtime | Docker (Render finds `Dockerfile` at the repository root) |
   | Region | Ohio (US East), next to the database |
   | Instance type | Free |
   | Health check path | `/actuator/health` |
   | Auto-deploy | On commit (to `main`) |

3. **Environment variables** (Render > the service > **Environment**). Paste the two passwords here and nowhere
   else:

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `demo` (loads the seed data: two organizations, six users, sample records) |
   | `DB_URL` | `jdbc:postgresql://<direct host from step 1.1>/relaycrm?sslmode=require` |
   | `DB_OWNER_USER` | the owner role shown under **Roles** in Neon (here `relaycrm_owner`) |
   | `DB_OWNER_PASSWORD` | the owner role's password (secret) |
   | `DB_APP_USER` | `relaycrm_app` |
   | `DB_APP_PASSWORD` | the app password from step 1.2 (secret) |
   | `DB_POOL_SIZE` | `5` |

   Do not set `PORT`: Render sets it, and the API listens on it. `JAVA_TOOL_OPTIONS` is set in the image for
   512 MB; only override it on purpose.

4. **Deploy.** The first build takes a few minutes, and on the free plan the first start took about 75 seconds
   (it also creates every table and loads the seed). In the logs, look for:
   - `Successfully applied 5 migrations to schema "public", now at version v4`;
   - `Started RelayCrmApplication`.

   If the app role could get around row-level security (superuser, BYPASSRLS, or owner of a table), the API
   refuses to start and says why.
5. **Check it.** The service's URL is `https://<service-name>.onrender.com`:

   ```bash
   curl https://relaycrm-api.onrender.com/actuator/health
   # {"groups":["liveness","readiness"],"status":"UP"}   no API key needed

   curl https://relaycrm-api.onrender.com/api/me -H "X-Api-Key: acme-rep-rita-key"
   # {"id":3,"orgId":1,"role":"REP"}
   ```

**Cold starts.** A free Render service sleeps after 15 minutes without requests. The next request wakes it, which
takes about 75-100 seconds on the free plan's CPU (the very first start took about 75 seconds). While it wakes, the
web app retries reads for up to 2 minutes and says "Waking up the demo server… this can take up to 2 minutes on the
free plan." If the server takes longer than that, the app shows "API unreachable" and a reload tries again.

## 3. Netlify: the web app

1. `frontend/netlify.toml` already forwards `/api/*` to the Render service, `https://relaycrm-api.onrender.com`. If
   the service is ever renamed, change it there (through a pull request). The service URL is not a secret.
2. **Add new site > Import an existing project**, connect the repository, branch `main`.
3. Set **Base directory** to `frontend`. Everything else comes from `netlify.toml`:
   - build command `npm run build` and publish directory `dist`;
   - Node 24;
   - `VITE_DEMO_MODE=true` (the "View as" switcher) and `VITE_PUBLIC_DEMO=true` (the banner);
   - the `/api/*` proxy to Render;
   - the fallback that lets deep links such as `/deals` load the app.

   No environment variables need to be added in the Netlify UI, and no secrets go there.
4. **Deploy**, then open the site. The banner reads "Public demo: seeded data, anyone can change it." and the top
   bar turns to "API connected" (after "Waking up the demo server…", for up to 2 minutes, on a cold start).

## 4. Check the deployed demo

**ETag through Netlify.** The API's optimistic locking accepts only a strong ETag (`"3"`) in `If-Match`. Some
CDNs weaken ETags (`W/"3"`) when they compress responses. Check once after the first deploy:

```bash
curl -si https://YOUR-SITE.netlify.app/api/contacts/1 -H "X-Api-Key: acme-admin-key" | grep -i '^etag'
# expected: etag: "0"    (a W/ prefix would be the problem)
```

If it comes back weak, the app's later editing screens must take the version from the response body (`"version"`)
instead of the header. The API itself needs no change.

**The whole walkthrough.** `demo.sh` runs about 100 requests against any address, including every PUT and PATCH
with `If-Match`, so it also proves the ETag round trip through Netlify. Run it right after a deploy or a reset, before
visitors change the data:

```bash
BASE=https://YOUR-SITE.netlify.app bash demo.sh
```

The script deletes what it creates, but its audit history stays (the audit trail is append-only).

## 5. Resetting the demo

Visitors can change everything. To put the seed data back:

1. In the Neon SQL Editor (database `relaycrm`, as the owner role), run [`reset-demo.sql`](reset-demo.sql). It
   drops and recreates the `public` schema, which also removes the append-only audit trail. That is the only way
   to remove it, and why the reset is a deliberate, manual step.
2. In Render, **Manual Deploy > Restart service**. On start, the migrations run again and the seed data is loaded.

The app role and both passwords stay as they are.

**Alternative: Neon point-in-time restore.** In the Neon Console, restore the `production` branch to a moment
just after the first deploy (Branches > `production` > Restore). Nothing needs to run afterwards, but the moment must still
be inside Neon's history window, which is short on the free plan. The SQL reset works at any time.

## What was verified before this was committed

- The image builds and runs as a non-root user, under a 512 MB memory limit (about 250 MB used after start).
- It was run with these exact environment variables against a local Postgres 17 whose owner role is not a
  superuser, like Neon's, after running `neon-setup.sql` as that owner. Results:
  - the migrations and seed ran;
  - the role check passed;
  - health answered without a key;
  - `/api/me` needed one;
  - a cross-tenant read got 404.
- `reset-demo.sql` followed by a restart brought the data back to the seed.
- The production web build with both flags, served behind an `/api` proxy, loaded deep links, showed the banner
  and reached the API.
- Netlify's and Render's own platforms are not part of that check: run section 4 after the first deploy.
