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
    └── student-experience/     first module (built) — Spring Boot, Maven root
        ├── README.md
        ├── pom.xml
        ├── docs/
        │   ├── STATE_CONTRACT.md
        │   └── REPOSITORY_SCOPE_RULES.md
        └── src/
            ├── main/java/com/campusos/portal/
            │   ├── engine/     the guarded state machine + transition matrix
            │   ├── domain/     one Request table, typed enums
            │   ├── payload/    per-type payload DTOs + codec
            │   ├── repo/       scoped repositories (no JpaRepository)
            │   ├── service/    scope resolution, academic writes, QR
            │   ├── view/       the normalized card / timeline read model
            │   ├── web/        controllers incl. the demo hook and /verify
            │   └── config/     demo seeder, security headers
            ├── main/resources/ Thymeleaf templates + css
            └── test/java/      74 tests: guards, side effects, scoping, security
```

## Build

Each module builds independently; there is no aggregator POM yet.

```bash
cd modules/student-experience && mvn spring-boot:run   # http://localhost:8080
cd modules/student-experience && mvn test              # 74 tests
```

## Modules

| Module | Status | What it owns |
|---|---|---|
| `student-experience` | built | The seven student-facing views: Home, My Requests, Leave, Internship, Documents & Certificates, Academic, Grievance — all on one polymorphic request engine. |

Everything else — staff dashboards, admissions, finance, HR, hostel, library, placements — is
unclaimed. See [`docs/MODULES.md`](docs/MODULES.md) before starting one.

## Non-negotiables

Two rules hold across every module, not just the first:

1. **Tenancy is not optional.** No query compiles without `tenant_id`. See
   [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
2. **One guarded transition per state change.** State moves through a declared matrix and nowhere
   else; side effects fire inside the transition's transaction.

`student-experience` is the reference implementation of both.
