#!/usr/bin/env bash
# Smoke-test: validates all critical PocketWardrobe API flows via curl.
# Usage:
#   ./scripts/smoke-test.sh                              # prod (api.clothis.tech)
#   ./scripts/smoke-test.sh --url http://localhost:8080  # local

set -euo pipefail

BASE_URL="${SMOKE_BASE_URL:-https://api.clothis.tech}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --url) BASE_URL="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
PASS=0; FAIL=0
TMPBODY=$(mktemp)
trap 'rm -f "$TMPBODY"' EXIT

pass() { echo -e "${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}✗${NC} $1"; FAIL=$((FAIL+1)); }
section() { echo -e "\n${YELLOW}── $1 ──${NC}"; }

# curl wrapper: writes body to TMPBODY, returns HTTP status code
api() {
  local code
  code=$(curl -s --max-time 30 -o "$TMPBODY" -w "%{http_code}" "$@") || code="000"
  echo "$code"
}

check_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then pass "$label (HTTP $actual)"
  else fail "$label — expected HTTP $expected, got HTTP $actual"; fi
}

check_field() {
  local label="$1" field="$2" body="$3"
  if echo "$body" | grep -q "\"$field\""; then pass "$label"
  else fail "$label — field '$field' missing"; fi
}

SUFFIX=$(date +%s)
TEST_EMAIL="smoketest_${SUFFIX}@clothis.test"
TEST_PASS="SmokeTest123!"

echo -e "${YELLOW}PocketWardrobe Smoke Test${NC}"
echo "Target: $BASE_URL"
echo "Time:   $(date -u '+%Y-%m-%dT%H:%M:%SZ')"

# ── Health ──────────────────────────────────────────────────────────────────
section "Health"
# Retry once — first request after a connection gap can time out
code=$(api "$BASE_URL/health")
if [[ "$code" == "000" ]]; then
  sleep 2
  code=$(api "$BASE_URL/health")
fi
check_status "GET /health" "200" "$code"

# ── Register ─────────────────────────────────────────────────────────────────
section "Auth / Register"
code=$(api -X POST "$BASE_URL/api/v1/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASS\"}")
reg_json=$(cat "$TMPBODY")
check_status "POST /register" "201" "$code"
check_field "Register → accessToken" "accessToken" "$reg_json"
check_field "Register → refreshToken" "refreshToken" "$reg_json"

ACCESS_TOKEN=$(echo "$reg_json" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4 || true)
REFRESH_TOKEN=$(echo "$reg_json" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4 || true)

if [[ -z "$ACCESS_TOKEN" ]]; then
  fail "Cannot extract access token — skipping remaining checks"
  FAIL=$((FAIL+5))
else

# ── Login ────────────────────────────────────────────────────────────────────
section "Auth / Login"
code=$(api -X POST "$BASE_URL/api/v1/login" \
  -H "Content-Type: application/json" \
  -d "{\"login\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASS\"}")
check_status "POST /login" "200" "$code"
check_field "Login → accessToken" "accessToken" "$(cat "$TMPBODY")"

# ── Authenticated endpoints ───────────────────────────────────────────────────
section "Authenticated endpoints"
code=$(api "$BASE_URL/api/v1/clothes" -H "Authorization: Bearer $ACCESS_TOKEN")
check_status "GET /api/v1/clothes" "200" "$code"

code=$(api "$BASE_URL/api/v1/profile" -H "Authorization: Bearer $ACCESS_TOKEN")
profile_json=$(cat "$TMPBODY")
check_status "GET /api/v1/profile" "200" "$code"
check_field "Profile → name" "name" "$profile_json"

code=$(api "$BASE_URL/api/v1/looks" -H "Authorization: Bearer $ACCESS_TOKEN")
check_status "GET /api/v1/looks" "200" "$code"

# ── Token refresh ─────────────────────────────────────────────────────────────
section "Token refresh"
code=$(api -X POST "$BASE_URL/api/v1/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
refresh_json=$(cat "$TMPBODY")
check_status "POST /refresh" "200" "$code"
NEW_REFRESH=$(echo "$refresh_json" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4 || true)

# ── Logout + token revocation ─────────────────────────────────────────────────
section "Logout + revocation"
if [[ -n "$NEW_REFRESH" ]]; then
  code=$(api -X POST "$BASE_URL/api/v1/logout" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$NEW_REFRESH\"}")
  check_status "POST /logout" "204" "$code"

  code=$(api -X POST "$BASE_URL/api/v1/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$NEW_REFRESH\"}")
  check_status "POST /refresh with revoked token" "401" "$code"
else
  fail "No new refresh token after rotation — skipping logout check"
fi

# ── Negative cases ────────────────────────────────────────────────────────────
section "Negative cases"
code=$(api -X POST "$BASE_URL/api/v1/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"nobody@clothis.test","password":"wrongpass"}')
check_status "POST /login with bad creds" "401" "$code"

fi  # end ACCESS_TOKEN check

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────"
echo -e "  ${GREEN}Passed: $PASS${NC}   ${RED}Failed: $FAIL${NC}"
echo "────────────────────────────"
[[ $FAIL -eq 0 ]] && echo -e "${GREEN}All checks passed.${NC}" || { echo -e "${RED}Some checks failed.${NC}"; exit 1; }
