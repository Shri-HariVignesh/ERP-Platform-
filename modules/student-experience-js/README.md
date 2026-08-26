# CampusOS — Student Experience Portal (JavaScript port)

A line-for-line JavaScript port of [`modules/student-experience`](../student-experience) (the
Spring Boot / Java original). Same engine, same frozen state contract, same seven student views
plus the eight-view faculty portal, same security posture — Node/Express/EJS/better-sqlite3
instead of Spring Boot/Thymeleaf/H2. Nothing was changed, added, or removed at the behaviour
level; only the runtime differs.

## Run

```bash
npm install
npm start           # http://localhost:8080
npm run dev          # same, with --watch
npm test             # unit + engine + scoping tests
```

The database is in-memory (`better-sqlite3` against `:memory:`) and reseeded on every start,
via the real state machine — exactly like the Java module's `CommandLineRunner` against H2. Set
`CAMPUSOS_DB_FILE=./campusos.db` to persist across restarts instead.

## Demo accounts

Same seeded identities as the Java module. Password for every account: `campus123`.

| Username | Who |
|---|---|
| `snit21cs042` | Hari Prasad — SNIT, CSE Sem 5 (the primary demo student) |
| `snit21cs051` | Divya Rajan — SNIT, CSE Sem 5 (classmate) |
| `snit21ec017` | Nikhil Varma — SNIT, ECE Sem 5 (different department) |
| `ace22ec118` | Meera Nair — ACE (a different tenant entirely) |
| `anjali.menon` | Faculty · CSE Sem 5 A |
| `krishnakumar` | HOD + Faculty · CSE |
| `registrar.snit` | Institution |
| `exam.office` | Examination Office |

## Architecture — identical to the Java module

* **One polymorphic engine.** [`src/engine/RequestStateMachine.js`](src/engine/RequestStateMachine.js)
  is the only code that changes a request's state, exactly as `RequestStateMachine.java` is.
  [`src/engine/TransitionMatrix.js`](src/engine/TransitionMatrix.js) is the frozen contract,
  transcribed edge-for-edge, guard-for-guard, side-effect-for-side-effect.
* **Mandatory scoping.** [`src/repo/*.js`](src/repo) exposes only the same scoped query
  functions the Java repositories declare — nothing equivalent to `findAll()` exists to be
  called by accident. See [`src/repo/README.md`](src/repo/README.md) for the same four
  exceptions the Java module documents.
* **Typed payloads.** [`src/payload/*.js`](src/payload) mirrors `LeavePayload`,
  `InternshipPayload`, `DocumentPayload`, `GrievancePayload` and `PayloadCodec` field-for-field.
* **Normalized read model.** [`src/service/PresentationService.js`](src/service/PresentationService.js)
  builds the same `RequestCard` shape the Java `PresentationService` does — the `timeline`,
  `skipped` states and headline logic are ported line-by-line from the source, not
  reinterpreted.
* **Staff scope, never a client-supplied actor.** [`src/service/StaffScopeResolver.js`](src/service/StaffScopeResolver.js)
  derives the acting `Actor` from the authenticated principal's roles intersected with the
  frozen matrix, exactly as `StaffScopeResolver.java` does — there is nowhere in any form for a
  client to name who it is acting as.

## What differs from the Java module (and why)

* **Template engine**: EJS instead of Thymeleaf. Markup and CSS classes are unchanged — the
  stylesheet ([`public/css/app.css`](public/css/app.css)) is copied byte-for-byte from the Java
  module's `static/css/app.css`.
* **Persistence**: `better-sqlite3` (synchronous, in-process) instead of H2-over-JDBC. Schema in
  [`src/db/schema.sql`](src/db/schema.sql) mirrors every `@Entity` table.
* **Sessions/auth**: `express-session` + `bcryptjs` instead of Spring Security, hand-rolled to
  the same rules: one login form for both account kinds, opaque failure messages, a
  synchronizer-token CSRF guard on every state-changing request (Spring Security's default,
  since the Java module never disables it), and the same security response headers
  (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, a script-src-`none` CSP).
* **QR rendering**: the `qrcode` npm package instead of ZXing, still rendered as inline SVG with
  no external service and no image files.
* **Verification ID minting**: `crypto.randomBytes` instead of `SecureRandom`, same 12-symbol,
  32-character alphabet (I/L/O/U omitted) for the same 60 bits of entropy — the JS equivalent of
  the F1 fix documented in the Java module's `docs/SECURITY.md`.

## Security notes carried over from the Java module

* The `/sim`-style demo hook does not exist here either — every staff decision goes through
  `/faculty/requests/:id/act`, which derives the actor from the session, never from the request
  body.
* Document downloads, faculty rosters, and academic writes are scoped exactly as in the Java
  module: a wrong tenant or wrong student returns "not available", never a 404 that would
  confirm existence, and never the engine's internal exception text.
* See [`../student-experience/docs/SECURITY.md`](../student-experience/docs/SECURITY.md) for the
  full audit — the same findings and the same residual-risk list apply here, since the
  behaviour is unchanged.

## Not built (same declared non-goals as the Java module)

Real payments, real file storage (a certificate is a filename reference), external notification
delivery, and a second identity provider. `/verify/{verifyId}` is a public page, not one of the
seven student views — see the parent [`STATE_CONTRACT.md`](../student-experience/docs/STATE_CONTRACT.md).
