# Security — Student Experience Portal

Audited across the fourteen dimensions listed below over five iterations. **6 findings fixed,
1 BLOCKED, 5 residual by design.** Not a claim that the module is secure — a record of what
was probed, what was reproduced, and what was left standing on purpose.

Threat model: [`THREAT_MODEL.md`](THREAT_MODEL.md).

## Running the guard

```bash
cd modules/student-experience

mvn test                              # full suite — 103 tests
mvn test -Dgroups=security            # security-tagged group — 42 tests
./scripts/security-check.sh           # both suites + 25 live probes against a booted app
SKIP_BUILD=1 ./scripts/security-check.sh   # probes only, app already running
```

`security-check.sh` exits non-zero on the first failure. This was verified by breaking a
control on purpose — commenting out `@Component` on `SecurityHeaders` — which produced
`exit=1` and 7 failures across both the unit suite and the live probes. A guard that cannot
fail is decoration; this one fails.

CI runs all three on every push and pull request
([`.github/workflows/security.yml`](../../../.github/workflows/security.yml)), plus a second
job that fails the build if a Spring Boot upgrade silently drags Thymeleaf or Tomcat back
below their pinned versions.

## Severity rubric

| Severity | Meaning |
|---|---|
| **Critical** | Cross-tenant data or PII disclosure, a broken scoping boundary, credential forgery, RCE/SSTI |
| **High** | IDOR, reflected XSS, dependency RCE, meaningful information disclosure |
| **Medium** | Missing headers, a CSRF gap, log or audit-trail integrity, availability, misleading display |
| **Low** | Cosmetic, or internal detail with no practical consequence |
| **BLOCKED** | A real finding whose fix would require changing the frozen contract or weakening a test |

## Findings

| # | Sev | CWE / OWASP | Finding | Status |
|---|---|---|---|---|
| F1 | High | CWE-330, CWE-340 / A01+A02 | Verification ids had ~2^27.7 effective entropy | Fixed |
| F2 | High | CWE-1395 / A06 | Thymeleaf 3.1.3 carried three sandbox-bypass RCE CVEs | Fixed |
| F3 | Medium | CWE-1284, CWE-400 / A04 | Unbounded input fields | Fixed |
| F4 | Medium | CWE-248, CWE-20 / A05 | Uncaught `DateTimeParseException` → HTTP 500 | Fixed |
| F6 | Medium | CWE-117 / A09 | A request id could author its own log records | Fixed |
| F7 | Medium | CWE-1395 / A06 | Tomcat 10.1.46 below the 10.1.55 advisory floor | Fixed |
| F5 | Low | CWE-209 / A05 | Error handler echoes the internal exception message | **BLOCKED** |

### F1 — Verification ids were guessable bearer capabilities (High)
`/verify` is unauthenticated by design and returns a student's name, institution and credential
kind, so the id *is* the access control. It was the first 6 base-36 characters of
`Math.abs(UUID.randomUUID().getMostSignificantBits())` — the leading digits of a
variable-length number, which are not uniform and never `0`.

Measured over 500,000 draws of the exact expression: 499,436 distinct, 564 collisions, first
collision at draw 21,512 → an effective keyspace of **~2^27.7**, not the 2^31 the format
implies. The attack is not guessing a chosen id but *any* valid one, which costs
`keyspace / issued` attempts. Live: `/verify` answers 200 on a hit and 404 on a miss (a clean
oracle) and served 300 sequential guesses at 135 req/s with no throttling.

Fixed: 12 symbols from a 32-character alphabet via `SecureRandom` — exactly 60 bits, no
truncation of a biased source. The alphabet omits `I`, `L`, `O`, `U`.

### F2 / F7 — Vulnerable managed dependencies (High / Medium)
Thymeleaf 3.1.3.RELEASE: CVE-2026-40477 (9.1), CVE-2026-40478 (9.1), CVE-2026-41901 (9.0) —
expression-sandbox bypasses. 3.1.4 fixes only the first two; **3.1.5.RELEASE** is required.
Tomcat 10.1.46 → 10.1.55 (seven fixes).

Honest scope: **no reachable exploit was found in this app.** Every user-supplied field was
probed with `[[${7*7}]]`, `${7*7}` and a `T(java.lang.Runtime).getRuntime().exec` payload —
all rendered as literal text, none evaluated, no file created. View names are not
user-controlled either. This is version hygiene, which is the reason to upgrade, not a reason
to skip it.

### F3 — Unbounded input (Medium)
`company`, `role`, `certificateFilename` and both date fields had `@NotBlank` with no `@Size`.
A 100,000-character `company` was accepted, persisted, and re-rendered on every later page
load — a 118,447-byte page, repeatable without limit and without a session. `@Column(length =
8000)` is not a backstop: `@Lob` makes H2 ignore it. The `/actions` `input` parameter never
passes through `Forms` at all and was capped separately in `StudentActionController`.

### F4 — Uncaught parse exception (Medium)
`from=NOTADATE` produced HTTP 500 and a logged stack trace, because `DateTimeParseException`
does not extend `IllegalArgumentException` and so slipped past the controllers' catch. The
response body stayed clean (`include-message=never` held), making this a robustness,
log-flooding and fingerprinting defect rather than a disclosure. Fixed in
`RequestPayload.parseDate`, on the interface every payload's `validate()` already runs through.

### F6 — Log injection (Medium)
The caller-supplied `{id}` path variable was interpolated raw into a message that is thrown and
then logged.
`POST /sim/requests/req_x%0d%0a2026-01-01T00:00:00.000+05:30++ERROR+FORGED.../advance`
ended one record early and produced a **separate, genuine-looking forged record**. In a system
whose claim is a complete, tamper-evident audit trail, a caller who can author log lines can
fabricate history or bury their own activity. Fixed with
`RequestStateMachine.safeForMessage` (strips control characters, caps at 64).

### F5 — BLOCKED: error handler echoes the internal message (Low)
`GlobalErrors` returns `e.getMessage()` as the response body, so a denied cross-tenant download
answers `document not visible in scope`. The handler's own javadoc claims the message is
"logged, not echoed", which is wrong.

The one-line fix is blocked because **`CrossTenantWebTest` line 61 asserts the body *contains*
that phrase** — an existing test pins the current behaviour, and weakening a test to land a fix
is not permitted. The change was written, reverted, and recorded here instead.

Residual exposure is small and bounded: the string is fixed and identical whether or not the id
exists, so it is not an existence oracle, and the same test already proves the denial leaks
neither the serial nor the holder's name. Resolving it needs the test owner to agree the
assertion should change.

## Dimensions probed clean

No finding, each reproduced against the running app: cross-tenant IDOR on every route including
`/verify`, `/documents/{id}/download` and `/sim/*`; function-level authz (a student firing every
`Event` constant at an office-gated transcript moved nothing and minted no credential); mass
assignment (`state`, `tenantId`, `studentId`, `verifyId`, `serialNo`, `id` — all inert, forms
are DTOs not entities); `docType` tampering toward a system-only credential; XSS and SSTI in
every field; SQL/JPQL injection (no `@Query`, no string-concatenated queries anywhere); path
traversal and CRLF header injection; open redirect (7 bypass shapes, all fall back);
CSRF (no state-changing `GET` exists — all return 405 — and the cookie is `SameSite=Lax`);
security headers; H2 console and actuator unreachable; error bodies free of stack traces;
secrets in the working tree and full git history; the autopilot cap; and a concurrency race
(10 simultaneous identical transitions applied exactly once, 9 correctly rejected).

## Residual risk — accepted by design

1. **No authentication.** A declared non-goal. Anyone reaching the app is a student, and
   anonymous callers land on the primary demo identity. The boundary under test is
   tenant + student scoping, not login.
2. **`/sim` accepts an arbitrary `actor`.** Any caller can POST `actor=FACULTY` and approve
   their own request. Deliberate — a staff UI is a non-goal and the demo has no other way to
   show a Faculty or HOD move. Whoever builds real identity owns deleting or gating `/sim`.
3. **The identity switcher lists every demo identity**, so one student's page shows another's
   name in the `<select>`. That is the switcher's purpose — proving tenant isolation. No
   request, document or record data crosses; verified.
4. **No rate limiting on `/verify`.** 60 bits makes enumeration infeasible, so throttling is
   defence in depth rather than the control. Needs infrastructure this prototype lacks.
5. **The session cookie has no `Secure` flag** and the session id does not rotate. The app
   serves plain HTTP for a laptop demo; `Secure` would break it outright, and with no auth
   boundary there is no privilege for fixation to escalate across.

Also out of scope by declaration: real email/SMS, real file storage, a staff UI.

## If you find something

Re-derive it from the running app before filing it. Every finding above carries a reproduction
because a security claim without evidence is a guess.
