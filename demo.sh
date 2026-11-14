#!/usr/bin/env bash
# Live walkthrough of RelayCRM's isolation and role rules.
# Needs the app running with the demo profile (see README). Run with:  bash demo.sh
# Each step prints the request, the HTTP status we expect, and what the API actually answered.

BASE="${BASE:-http://localhost:8080}"
FAILED=0

# Demo users (API keys from db/demo/R__demo_data.sql)
ACME_ADMIN="acme-admin-key"        # Alice, ADMIN,   Acme   (user 1)
ACME_MANAGER="acme-manager-key"    # Mark,  MANAGER, Acme   (user 2)
ACME_RITA="acme-rep-rita-key"      # Rita,  REP,     Acme   (user 3)
ACME_RAVI="acme-rep-ravi-key"      # Ravi,  REP,     Acme   (user 4)
GLOBEX_ADMIN="globex-admin-key"    # Gina,  ADMIN,   Globex (user 5)
GLOBEX_REP="globex-rep-key"        # Greg,  REP,     Globex (user 6)

BODY=""

# call <who> <api key> <expected status> <method> <path> [json body]
call() {
  local who="$1" key="$2" expected="$3" method="$4" path="$5" data="${6:-}"
  local args=(-s -w $'\n%{http_code}' -X "$method" "$BASE$path")
  local type="application/json"
  [ "$method" = "PATCH" ] && type="application/merge-patch+json"
  [ -n "$key" ] && args+=(-H "X-Api-Key: $key")
  [ -n "$data" ] && args+=(-H "Content-Type: $type" -d "$data")
  local out; out=$(curl "${args[@]}")
  local status="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
  local verdict="OK  "
  if [ "$status" != "$expected" ]; then verdict="FAIL"; FAILED=1; fi
  printf '[%s] %-14s %-6s %-22s -> %s (expected %s)\n' "$verdict" "$who" "$method" "$path" "$status" "$expected"
  [ -n "$data" ] && printf '         sent: %s\n' "$data"
  [ -n "$BODY" ] && printf '         got:  %s\n' "$BODY"
}

section() { printf '\n=== %s ===\n' "$1"; }

section "1. Who is who (organization and role come from the server, not from the request)"
call "Acme admin"   "$ACME_ADMIN"   200 GET /api/me
call "Acme rep Rita" "$ACME_RITA"   200 GET /api/me
call "Globex admin" "$GLOBEX_ADMIN" 200 GET /api/me
call "nobody"       ""              401 GET /api/contacts

section "2. Each organization sees only its own records"
call "Acme rep Rita" "$ACME_RITA"   200 GET /api/contacts
call "Globex rep"    "$GLOBEX_REP"  200 GET /api/contacts

section "3. BLOCKED: cross-tenant requests with a valid id (contact 1 belongs to Acme)"
call "Acme admin"   "$ACME_ADMIN"   200 GET    /api/contacts/1
call "Globex admin" "$GLOBEX_ADMIN" 404 GET    /api/contacts/1
call "Globex admin" "$GLOBEX_ADMIN" 404 PUT    /api/contacts/1 '{"firstName":"Hijacked","lastName":"Hijacked"}'
call "Globex admin" "$GLOBEX_ADMIN" 404 DELETE /api/contacts/1
call "Globex admin" "$GLOBEX_ADMIN" 404 GET    /api/leads/1
call "Globex admin" "$GLOBEX_ADMIN" 404 GET    /api/opportunities/1
echo "    ...and Acme's contact is unchanged:"
call "Acme admin"   "$ACME_ADMIN"   200 GET    /api/contacts/1

section "4. BLOCKED: cross-tenant references"
call "Acme admin" "$ACME_ADMIN" 400 POST /api/opportunities '{"name":"Sneaky deal","contactId":3}'
call "Acme admin" "$ACME_ADMIN" 400 POST /api/contacts '{"firstName":"A","lastName":"B","ownerId":5}'

section "5. Role rules inside Acme: reps edit only what they own"
call "Acme rep Rita" "$ACME_RITA" 201 POST /api/contacts '{"firstName":"Nina","lastName":"New","company":"Fresh Co"}'
ID=$(printf '%s' "$BODY" | sed -E 's/.*"id":([0-9]+).*/\1/')
call "Acme rep Rita" "$ACME_RITA"    200 PUT /api/contacts/$ID '{"firstName":"Nina","lastName":"Edited-by-owner"}'
call "Acme rep Ravi" "$ACME_RAVI"    403 PUT /api/contacts/$ID '{"firstName":"Nina","lastName":"Edited-by-other-rep"}'
call "Acme manager"  "$ACME_MANAGER" 200 PUT /api/contacts/$ID '{"firstName":"Nina","lastName":"Edited-by-manager"}'
call "Acme admin"    "$ACME_ADMIN"   200 PUT /api/contacts/$ID '{"firstName":"Nina","lastName":"Edited-by-admin"}'

section "6. Role rules inside Acme: only admins delete"
call "Acme rep Rita" "$ACME_RITA"    403 DELETE /api/contacts/$ID
call "Acme manager"  "$ACME_MANAGER" 403 DELETE /api/contacts/$ID
call "Acme admin"    "$ACME_ADMIN"   204 DELETE /api/contacts/$ID
call "Acme admin"    "$ACME_ADMIN"   404 GET    /api/contacts/$ID

section "7. Leads and opportunities follow the same rules"
call "Acme rep Rita" "$ACME_RITA"    201 POST /api/leads '{"name":"Lena Lead","company":"Prospect Co","source":"webinar"}'
LEAD=$(printf '%s' "$BODY" | sed -E 's/.*"id":([0-9]+).*/\1/')
call "Acme rep Ravi" "$ACME_RAVI"    403 PUT    /api/leads/$LEAD '{"name":"Lena Lead","status":"QUALIFIED"}'
call "Acme rep Rita" "$ACME_RITA"    200 PUT    /api/leads/$LEAD '{"name":"Lena Lead","company":"Prospect Co","source":"webinar","status":"QUALIFIED"}'
call "Globex admin"  "$GLOBEX_ADMIN" 404 GET    /api/leads/$LEAD
call "Acme rep Rita" "$ACME_RITA"    201 POST /api/opportunities '{"name":"Road Runner - upsell","contactId":1,"amount":3000,"stage":"PROPOSAL"}'
OPP=$(printf '%s' "$BODY" | sed -E 's/.*"id":([0-9]+).*/\1/')
call "Globex admin"  "$GLOBEX_ADMIN" 404 DELETE /api/opportunities/$OPP
call "Acme manager"  "$ACME_MANAGER" 403 DELETE /api/opportunities/$OPP
call "Acme admin"    "$ACME_ADMIN"   204 DELETE /api/opportunities/$OPP
call "Acme admin"    "$ACME_ADMIN"   204 DELETE /api/leads/$LEAD

section "8. PUT replaces the whole record; PATCH changes only what is sent"
call "Acme rep Rita" "$ACME_RITA" 201 POST /api/contacts '{"firstName":"Pat","lastName":"Patch","email":"pat@fresh.test","phone":"555-0100","company":"Fresh Co"}'
ID=$(printf '%s' "$BODY" | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "    PATCH one field: everything else stays"
call "Acme rep Rita" "$ACME_RITA" 200 PATCH /api/contacts/$ID '{"company":"Renamed Co"}'
echo "    PATCH null: clears an optional field"
call "Acme rep Rita" "$ACME_RITA" 200 PATCH /api/contacts/$ID '{"phone":null}'
echo "    PATCH null on a required field: refused"
call "Acme rep Rita" "$ACME_RITA" 400 PATCH /api/contacts/$ID '{"lastName":null}'
echo "    PUT without a required field: refused, and the field is named"
call "Acme rep Rita" "$ACME_RITA" 400 PUT   /api/contacts/$ID '{"firstName":"Pat"}'
echo "    PUT with only the required fields: optional fields are cleared"
call "Acme rep Rita" "$ACME_RITA" 200 PUT   /api/contacts/$ID '{"firstName":"Pat","lastName":"Replaced"}'
echo "    PATCH follows the same permission rules as PUT"
call "Acme rep Ravi" "$ACME_RAVI"    403 PATCH /api/contacts/$ID '{"company":"Ravis"}'
call "Globex admin"  "$GLOBEX_ADMIN" 404 PATCH /api/contacts/$ID '{"company":"Hijacked"}'
call "Acme rep Rita" "$ACME_RITA"    403 PATCH /api/contacts/$ID '{"ownerId":4}'
call "Acme manager"  "$ACME_MANAGER" 200 PATCH /api/contacts/$ID '{"ownerId":4}'
echo "    A record always has an owner: it cannot be set to null"
call "Acme admin"    "$ACME_ADMIN"   400 PATCH /api/contacts/$ID '{"ownerId":null}'
call "Acme admin"    "$ACME_ADMIN"   204 DELETE /api/contacts/$ID

section "9. A lead's status is optional on create but cannot be dropped by an update"
call "Acme rep Rita" "$ACME_RITA" 201 POST /api/leads '{"name":"Sam Status"}'
LEAD=$(printf '%s' "$BODY" | sed -E 's/.*"id":([0-9]+).*/\1/')
call "Acme rep Rita" "$ACME_RITA" 200 PATCH /api/leads/$LEAD '{"status":"QUALIFIED"}'
call "Acme rep Rita" "$ACME_RITA" 400 PUT   /api/leads/$LEAD '{"name":"Sam Status"}'
call "Acme rep Rita" "$ACME_RITA" 400 PATCH /api/leads/$LEAD '{"status":null}'
call "Acme admin"    "$ACME_ADMIN" 204 DELETE /api/leads/$LEAD

section "10. BLOCKED: fields the API does not know (a tenant override attempt, and a typo)"
call "Globex rep"    "$GLOBEX_REP"  400 POST  /api/contacts '{"firstName":"A","lastName":"B","orgId":1}'
call "Globex admin"  "$GLOBEX_ADMIN" 400 PATCH /api/contacts/3 '{"orgId":1}'
call "Acme rep Rita" "$ACME_RITA"   400 POST  /api/leads '{"name":"Typo","stauts":"QUALIFIED"}'
call "Acme rep Rita" "$ACME_RITA"   400 PUT   /api/leads/1 '{"name":"Wile E.","status":"NEW","stauts":"QUALIFIED"}'

echo
if [ "$FAILED" = 0 ]; then echo "All steps behaved as expected."; else echo "SOME STEPS DID NOT BEHAVE AS EXPECTED."; exit 1; fi
