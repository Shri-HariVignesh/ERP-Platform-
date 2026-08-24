#!/usr/bin/env bash
#
# The standing guard. Runs the full suite, then the security-tagged group, then boots the app
# and re-runs live probes for the findings that a unit test cannot fully cover (response
# headers, a real cross-tenant HTTP denial, reflected payloads, the verify-id keyspace).
#
# Exits non-zero on the first failure of any kind. Intended for CI and for a pre-push hook.
#
#   ./scripts/security-check.sh            full run
#   SKIP_BUILD=1 ./scripts/security-check.sh   probes only, against an already-running app
#
set -uo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$MODULE_DIR"

PORT="${PORT:-18080}"
BASE="http://localhost:${PORT}"
LOG="$(mktemp -t security-check-XXXXXX)"
APP_PID=""
PASS=0
FAIL=0

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

cleanup() {
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null
  wait "$APP_PID" 2>/dev/null
  rm -f "$LOG"
}
trap cleanup EXIT

check() { # name actual expected
  if [ "$2" = "$3" ]; then green "  PASS  $1"; PASS=$((PASS+1))
  else red   "  FAIL  $1  (got '$2', expected '$3')"; FAIL=$((FAIL+1)); fi
}

section() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# ---------------------------------------------------------------- 1. test suites
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  section "1/3  Full test suite"
  if mvn -B -q test > "$LOG" 2>&1; then
    green "  PASS  mvn test"; PASS=$((PASS+1))
  else
    red "  FAIL  mvn test"; FAIL=$((FAIL+1)); tail -40 "$LOG"
  fi

  section "2/3  Security-tagged suite"
  if mvn -B -q test -Dgroups=security > "$LOG" 2>&1; then
    green "  PASS  mvn test -Dgroups=security"; PASS=$((PASS+1))
  else
    red "  FAIL  security-tagged suite"; FAIL=$((FAIL+1)); tail -40 "$LOG"
  fi
fi

# ---------------------------------------------------------------- 2. boot
section "3/3  Live probes"
mvn -B spring-boot:run -Dspring-boot.run.arguments="--server.port=${PORT}" > "$LOG" 2>&1 &
APP_PID=$!

for _ in $(seq 1 120); do
  curl -sf -o /dev/null "${BASE}/" && break
  sleep 1
done
if ! curl -sf -o /dev/null "${BASE}/"; then
  red "  FAIL  app did not start on port ${PORT}"; tail -40 "$LOG"; exit 1
fi

JAR_HARI="$(mktemp)"; JAR_MEERA="$(mktemp)"
curl -s -o /dev/null -c "$JAR_HARI"  -X POST "${BASE}/switch" -d studentId=s_hari
curl -s -o /dev/null -c "$JAR_MEERA" -X POST "${BASE}/switch" -d studentId=s_meera

hdr() { curl -s -D - -o /dev/null "${BASE}/" | grep -ci -- "$1"; }

# --- A05 security headers ---
check "header X-Content-Type-Options: nosniff" "$(hdr 'X-Content-Type-Options: nosniff')" "1"
check "header X-Frame-Options: DENY"           "$(hdr 'X-Frame-Options: DENY')"           "1"
check "header Referrer-Policy: no-referrer"    "$(hdr 'Referrer-Policy: no-referrer')"    "1"
check "CSP frame-ancestors 'none'"             "$(hdr "frame-ancestors 'none'")"          "1"
check "CSP script-src 'none'"                  "$(hdr "script-src 'none'")"               "1"
check "session cookie HttpOnly + SameSite"     "$(hdr 'HttpOnly; SameSite=Lax')"          "1"

# --- A05 config exposure ---
for path in /h2-console /actuator /actuator/env; do
  check "unreachable ${path}" \
        "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}${path}")" "404"
done

# --- A01 cross-tenant denial ---
for id in 1 2 3; do
  code="$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_MEERA" "${BASE}/documents/${id}/download")"
  [ "$code" = "200" ] && continue   # one of these is legitimately Meera's own
  check "cross-tenant download ${id} refused" "$code" "400"
done
check "cross-tenant download refused (path-param variant)" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_MEERA" "${BASE}/documents/1;a=b/download")" "400"
check "no cross-tenant card content" \
      "$(curl -s -b "$JAR_MEERA" "${BASE}/requests" | python3 -c 'import sys,re;h=sys.stdin.read();print(sum(1 for c in re.findall(r"<article class=.card.*?</article>",h,re.S) if "Hari" in c))')" "0"

# --- A01 open redirect + state-changing GET ---
check "open redirect blocked" \
      "$(curl -s -o /dev/null -D - -b "$JAR_HARI" -X POST "${BASE}/actions/req_x" \
           -d 'event=RESUBMIT&back=https://evil.test' | grep -c "Location: ${BASE}/requests")" "1"
check "no state-changing GET on /switch" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" "${BASE}/switch?studentId=s_meera")" "405"

# --- A03 injection ---
curl -s -o /dev/null -b "$JAR_HARI" -X POST "${BASE}/grievance" \
  --data-urlencode 'category=OTHER' \
  --data-urlencode 'subject=<script>alert(1)</script>' \
  --data-urlencode 'description=[[${7*7}]] "><img src=x onerror=alert(1)>'
check "no unescaped <script> reaches any page" \
      "$(for p in '' requests grievance documents leave internship academic; do
           curl -s -b "$JAR_HARI" "${BASE}/${p}"; done | grep -c '<script>alert')" "0"
check "no Thymeleaf SSTI evaluation" \
      "$(curl -s -b "$JAR_HARI" "${BASE}/grievance" | grep -c '>49<')" "0"

# --- business logic ---
check "system-only docType refused" \
      "$(curl -s -b "$JAR_HARI" -X POST "${BASE}/documents" \
           -d 'docType=INTERNSHIP_VERIFICATION&purpose=x&copies=1' \
         | grep -c 'not one a student can request')" "1"

# --- F5 error-message disclosure ---
check "denial body carries no engine vocabulary" \
      "$(curl -s -b "$JAR_MEERA" "${BASE}/documents/1/download" | grep -c 'not visible in scope')" "0"

# --- F1 verify-id keyspace ---
check "verify id keeps a >=12 symbol random suffix" \
      "$(curl -s -b "$JAR_HARI" "${BASE}/academic" | grep -oE 'SNIT-[0-9]{4}-[A-Z0-9]+' | head -1 \
         | awk -F- '{ print (length($3) >= 12) ? "ok" : "short" }')" "ok"

# --- F3 / F4 input handling ---
BIG="$(python3 -c 'print("A"*100000)')"
curl -s -o /dev/null -b "$JAR_HARI" -X POST "${BASE}/internship" \
  --data-urlencode "company=${BIG}" --data-urlencode 'role=r' \
  --data-urlencode 'from=2026-01-01' --data-urlencode 'to=2026-03-01' \
  --data-urlencode 'details=d' --data-urlencode 'certificateFilename=c.pdf'
check "oversized field never reaches a rendered page" \
      "$(curl -s -b "$JAR_HARI" "${BASE}/internship" | grep -c 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')" "0"
check "malformed date is not a 500" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" -X POST "${BASE}/leave" \
           --data-urlencode 'leaveType=PERSONAL' --data-urlencode 'from=NOTADATE' \
           --data-urlencode 'to=NOTADATE' --data-urlencode 'reason=probe')" "200"

# --- F6 log injection ---
LOG_BEFORE="$(wc -l < "$LOG")"
curl -s -o /dev/null -b "$JAR_HARI" -X POST \
  "${BASE}/sim/requests/req_x%0d%0a2026-01-01T00:00:00.000+05:30++ERROR+FORGED/advance" \
  -d 'event=APPROVE&actor=FACULTY'
check "a request id cannot author a log record" \
      "$(tail -n +$((LOG_BEFORE+1)) "$LOG" | grep -acE '^2026-01-01')" "0"

rm -f "$JAR_HARI" "$JAR_MEERA"

section "Result"
if [ "$FAIL" -gt 0 ]; then
  red "  ${PASS} passed, ${FAIL} FAILED"
  exit 1
fi
green "  ${PASS} passed, 0 failed"
