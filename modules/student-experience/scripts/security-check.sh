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

# CSRF is enforced on every POST since the Faculty module added Spring Security. These probes
# have to drive the app the way a browser does: fetch a page, read the token Thymeleaf put in
# the form, send it back on the same session. Without this the POSTs 403 and the probes below
# would silently test nothing — a green run that proves the setup failed, not that the app is safe.
csrf() { # jar [path]  -- /login works for an anonymous jar; /leave needs a session
  curl -s -b "$1" -c "$1" "${BASE}${2:-/login}" \
    | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//'
}
post() { # jar path [curl args...]
  local jar="$1" path="$2"; shift 2
  curl -s -b "$jar" -c "$jar" -X POST "${BASE}${path}" -d "_csrf=$(csrf "$jar")" "$@"
}

# Students authenticate now; /switch is gone. login() FAILS CLOSED: it asserts the greeting
# immediately, so a setup that silently did not log in fails the run here instead of letting
# every scoped probe below read the wrong session and pass for the wrong reason.
login() { # jar username
  curl -s -o /dev/null -b "$1" -c "$1" -X POST "${BASE}/login" \
    -d "_csrf=$(csrf "$1" /login)&username=$2&password=campus123"
}

login "$JAR_HARI"  snit21cs042
login "$JAR_MEERA" ace22ec118

check "student login actually took effect (hari)" \
      "$(curl -s -b "$JAR_HARI" "${BASE}/home" | grep -c '<h1>Hello, Hari')" "1"
check "student login actually took effect (meera)" \
      "$(curl -s -b "$JAR_MEERA" "${BASE}/home" | grep -c '<h1>Hello, Meera')" "1"
check "CSRF token is issued into student forms" \
      "$([ -n "$(csrf "$JAR_HARI" /leave)" ] && echo 1 || echo 0)" "1"
check "a student POST with no CSRF token is refused" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" -X POST "${BASE}/grievance" \
         -d 'category=EXAM&subject=x&description=y')" "403"

VERIFY_PATH="$(curl -s -b "$JAR_HARI" "${BASE}/academic" \
  | grep -oE '/verify/[A-Za-z0-9-]+' | head -1)"

# --- student authentication ---
check "/switch is retired (404, controller absent)" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" -X POST "${BASE}/switch" \
         -d "_csrf=$(csrf "$JAR_HARI" /leave)&studentId=s_divya")" "404"
check "anonymous student route redirects to the login form" \
      "$(curl -s -o /dev/null -w '%{redirect_url}' "${BASE}/requests" | grep -c '/login')" "1"
check "anonymous student route leaks no page content" \
      "$(curl -s "${BASE}/academic" | grep -c 'Hari Prasad')" "0"
check "a studentId parameter cannot move a student's scope" \
      "$(curl -s -b "$JAR_HARI" "${BASE}/requests?studentId=s_divya" | grep -c 'Divya Rajan')" "0"
check "no other student appears on a student's page" \
      "$(curl -s -b "$JAR_MEERA" "${BASE}/requests" | grep -c 'Hari Prasad')" "0"
check "a student cannot reach the staff portal" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" "${BASE}/faculty/tasks")" "403"
check "refusals still carry the security headers" \
      "$(curl -s -D - -o /dev/null "${BASE}/requests" | grep -ci 'X-Frame-Options: DENY')" "1"

# --- deny-by-default did not break what the pages actually load ---
check "the only static asset still loads" \
      "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/css/app.css")" "200"
check "the login page loads its stylesheet anonymously" \
      "$(curl -s "${BASE}/login" | grep -c '/css/app.css')" "1"
check "an unmapped path is denied by default" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" "${BASE}/admin")" "403"
check "the public verify page still loads with no session at all" \
      "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}${VERIFY_PATH}")" "200"
check "the public verify page still loads its stylesheet" \
      "$(curl -s "${BASE}${VERIFY_PATH}" | grep -c '/css/app.css')" "1"
check "the public verify page still renders its QR" \
      "$(curl -s "${BASE}${VERIFY_PATH}" | grep -c '<svg')" "1"
check "favicon is not a 403 under deny-by-default" \
      "$([ "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/favicon.ico")" = "403" ] \
         && echo bad || echo ok)" "ok"

# --- the staff surface added by the Faculty module ---
JAR_STAFF="$(mktemp)"
curl -s -o /dev/null -b "$JAR_STAFF" -c "$JAR_STAFF" -X POST "${BASE}/login" \
  -d "_csrf=$(csrf "$JAR_STAFF" /login)&username=anjali.menon&password=campus123"

check "staff form login succeeds" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_STAFF" "${BASE}/faculty/tasks")" "200"
check "anonymous staff route redirects to the login form" \
      "$(curl -s -o /dev/null -w '%{redirect_url}' "${BASE}/faculty/tasks" | grep -c '/login')" "1"
check "anonymous staff route leaks no page content" \
      "$(curl -s "${BASE}/faculty/students" | grep -c 'Hari Prasad')" "0"
check "/sim is retired (404, controller absent)" \
      "$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/sim/requests/x/advance")" "404"
check "no form anywhere posts a client-supplied actor" \
      "$(curl -s -b "$JAR_STAFF" "${BASE}/faculty/tasks" | grep -c 'name="actor"')" "0"
check "wrong staff password is refused" \
      "$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR_STAFF" -c "$JAR_STAFF" \
         -X POST "${BASE}/login" -d "_csrf=$(csrf "$JAR_STAFF" /login)&username=anjali.menon&password=nope" \
         | grep -c 'error')" "1"

hdr() { curl -s -D - -o /dev/null "${BASE}/" | grep -ci -- "$1"; }

# --- A05 security headers ---
check "header X-Content-Type-Options: nosniff" "$(hdr 'X-Content-Type-Options: nosniff')" "1"
check "header X-Frame-Options: DENY"           "$(hdr 'X-Frame-Options: DENY')"           "1"
check "header Referrer-Policy: no-referrer"    "$(hdr 'Referrer-Policy: no-referrer')"    "1"
check "CSP frame-ancestors 'none'"             "$(hdr "frame-ancestors 'none'")"          "1"
check "CSP script-src 'none'"                  "$(hdr "script-src 'none'")"               "1"
check "session cookie HttpOnly + SameSite"     "$(hdr 'HttpOnly; SameSite=Lax')"          "1"

# --- A05 config exposure ---
# These used to assert exactly 404, which held only because anyRequest() was permitAll and
# nothing was mapped. Under deny-by-default an anonymous request is redirected to the login
# form and an authenticated one gets 403, so a bare "== 404" would now fail for the RIGHT
# reason and hide the property. The property is "never served", asserted three ways:
#   1. anonymous never gets a 200
#   2. an authenticated student never gets a 200 either — no login unlocks it
#   3. whatever the status, no console or actuator content comes back
for path in /h2-console /actuator /actuator/env; do
  anon="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}${path}")"
  authed="$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" "${BASE}${path}")"
  check "unreachable ${path} (anonymous)" \
        "$([ "$anon" = "200" ] && echo served || echo refused)" "refused"
  check "unreachable ${path} (authenticated)" \
        "$([ "$authed" = "200" ] && echo served || echo refused)" "refused"
  check "no content leaks from ${path}" \
        "$(curl -s -b "$JAR_HARI" "${BASE}${path}" \
           | grep -ciE 'h2 console|jdbc:|"_links"|healthEndpoint' || true)" "0"
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
      "$(post "$JAR_HARI" /actions/req_x -o /dev/null -D - \
           -d 'event=RESUBMIT&back=https://evil.test' | grep -c "Location: ${BASE}/requests")" "1"
check "no state-changing GET on /switch (it does not exist at all)" \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_HARI" "${BASE}/switch?studentId=s_meera")" "404"

# --- A03 injection ---
post "$JAR_HARI" /grievance -o /dev/null \
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
      "$(post "$JAR_HARI" /documents \
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
post "$JAR_HARI" /internship -o /dev/null \
  --data-urlencode "company=${BIG}" --data-urlencode 'role=r' \
  --data-urlencode 'from=2026-01-01' --data-urlencode 'to=2026-03-01' \
  --data-urlencode 'details=d' --data-urlencode 'certificateFilename=c.pdf'
check "oversized field never reaches a rendered page" \
      "$(curl -s -b "$JAR_HARI" "${BASE}/internship" | grep -c 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')" "0"
check "malformed date is not a 500" \
      "$(post "$JAR_HARI" /leave -o /dev/null -w '%{http_code}' \
           --data-urlencode 'leaveType=PERSONAL' --data-urlencode 'from=NOTADATE' \
           --data-urlencode 'to=NOTADATE' --data-urlencode 'reason=probe')" "200"

# --- F6 log injection ---
# The original probe drove /sim, which the Faculty module retired. The property is
# unchanged, so it now drives the endpoint that replaced it — with a real staff session,
# because an anonymous POST would be refused before the id was ever logged.
LOG_BEFORE="$(wc -l < "$LOG")"
curl -s -o /dev/null -b "$JAR_STAFF" -c "$JAR_STAFF" -X POST \
  "${BASE}/faculty/requests/req_x%0d%0a2026-01-01T00:00:00.000+05:30++ERROR+FORGED/act" \
  -d "_csrf=$(csrf "$JAR_STAFF" /faculty/tasks)&event=APPROVE"
check "a request id cannot author a log record" \
      "$(tail -n +$((LOG_BEFORE+1)) "$LOG" | grep -acE '^2026-01-01')" "0"

rm -f "$JAR_HARI" "$JAR_MEERA" "$JAR_STAFF" "$JAR_HARI.jar" "$JAR_MEERA.jar"

section "Result"
if [ "$FAIL" -gt 0 ]; then
  red "  ${PASS} passed, ${FAIL} FAILED"
  exit 1
fi
green "  ${PASS} passed, 0 failed"
