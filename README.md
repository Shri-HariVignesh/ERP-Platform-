# ERP Platform

A modular campus ERP. Each concern is a **module** under `modules/` with its own runtime, its
own persistence and its own documents; the platform is the set of contracts modules agree on,
not a single deployable.

## Layout

```
ERP-Platform-/
├── docs/                       platform-level contracts
│   ├── ARCHITECTURE.md         module boundaries, tenancy, the shared rules
│   └── MODULES.md              the module map — what exists, what is planned
└── modules/
    └── student-experience/     first module (built) — Node.js / Express
        ├── README.md
        ├── package.json
        ├── docs/
        │   ├── STATE_CONTRACT.md
        │   ├── REPOSITORY_SCOPE_RULES.md
        │   ├── SECURITY.md
        │   └── THREAT_MODEL.md
        ├── src/
        │   ├── engine/     the guarded state machine + transition matrix
        │   ├── domain/     the Request shape, typed enums
        │   ├── payload/    per-type payload classes + codec
        │   ├── repo/       scoped query modules (no unscoped finder exists)
        │   ├── service/    scope resolution, academic writes, QR
        │   ├── view/       the normalized card / timeline read model
        │   ├── web/        routes incl. the faculty portal and /verify
        │   ├── config/     demo seeder
        │   └── db/         schema + connection
        ├── views/          EJS templates + static css
        └── test/           engine, scoping, and view-helper tests
```

## Build

Each module builds independently; there is no aggregator build yet.

```bash
cd modules/student-experience && npm install
cd modules/student-experience && npm start   # http://localhost:8080
cd modules/student-experience && npm test
```

## Modules

| Module | Status | What it owns |
|---|---|---|
| `student-experience` | built | The seven student-facing views: Home, My Requests, Leave, Internship, Documents & Certificates, Academic, Grievance — plus an eight-view faculty portal — all on one polymorphic request engine. |

Everything else — admissions, finance, HR, hostel, library, placements — is unclaimed. See
[`docs/MODULES.md`](docs/MODULES.md) before starting one.

## Non-negotiables

Two rules hold across every module, not just the first:

1. **Tenancy is not optional.** No query compiles without `tenantId`. See
   [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
2. **One guarded transition per state change.** State moves through a declared matrix and nowhere
   else; side effects fire inside the transition's transaction.

`student-experience` is the reference implementation of both.
