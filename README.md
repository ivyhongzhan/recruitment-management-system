# Recruitment Management System

**2026 implementation: Java 21 + Spring Boot + Spring Data JPA + MySQL 8.4.** The browser UI and interaction design are carried forward from the supplied FastAPI project; the REST backend has been migrated and keeps the existing `/api/*` contract. The uploaded Python project remains available in the source archive and is recorded as the migration baseline.

This project is a demonstration, not a production ATS. All seeded names, emails, positions, counts, and activity are fictional synthetic records. Do not enter real applicant information: authentication and production privacy controls are not included.

![Synthetic recruitment overview](docs/screenshots/overview.webp)

## Run the full app

Requires Docker Desktop with Compose. From this directory:

```sh
docker compose up --build
```

Open <http://localhost:8000>. MySQL data is stored in a named volume and survives app restarts. To reset the local synthetic database, run `docker compose down -v` and start it again. To start empty, run `RMS_SEED_DEMO=false docker compose up --build`.

The default Compose credentials are for a local synthetic demo only. Override `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` for a non-demo deployment. The service has no authentication and should not be exposed publicly with demo credentials.

## API and behavior

The static HTML/CSS/JavaScript frontend is served by Spring Boot. API paths and JSON fields remain compatible with the FastAPI version:

| Area | Routes |
|---|---|
| Health and descriptive metrics | `GET /api/health`, `GET /api/metrics` |
| Jobs | `GET/POST /api/jobs`, `PATCH /api/jobs/{id}/status` |
| Applications | `GET/POST /api/applications`, `GET /api/applications/{id}`, `PATCH /api/applications/{id}/stage` |
| Audit | `GET /api/audit?limit=40` |

Filtering accepts `search`, `job_id`, and `stage`. The workflow has six stages: `applied → screening → interview → offer → hired`, with `rejected` allowed from every active stage. Stage updates require `expected_version`; stale requests and invalid transitions return HTTP 409. Email is normalized case-insensitively; a candidate can apply to multiple jobs, but duplicate applications to one job are rejected. A closed job accepts no new applications. Accepted job/application changes create operation-event records in the same transaction.

## Workflow rules and scope

The following reasons explain the 2026 demonstration design; they are not evidence of universal recruiting practice or a validated user requirement. Five ordered progress states plus one rejection outcome make six displayed stages.

| Implemented rule | Design reason | Unsupported exception |
| --- | --- | --- |
| Applied → Screening → Interview → Offer → Hired; no skipping or backward moves | Makes each change explicit and the next action inspectable | Fast-track hiring, correction/backtracking and parallel stages |
| Rejection from any active stage; Hired and Rejected are terminal | Preserves a simple final outcome without silently reopening it | Candidate withdrawal, offer declined and reopening a rejected application; these must not be relabelled as employer rejection |
| One application per candidate email and job | Prevents duplicate records within this demo | Reapplication to the same job or separate recruitment cycles |
| Interview is one stage | Keeps the overview compact | Multiple interview rounds and interview scheduling |
| Closed jobs reject new applications; existing applications remain | Separates intake availability from existing workflow | Automatically cancelling active applications when a job closes |
| Application updates require the current version | Detects stale edits | Automatic conflict merging or a multi-user approval workflow |

A different operational setting may require additional states and explicit event types. The current model should be evaluated with recruiters before extending it; rejected must not stand in for every reason an application ends.

## Architecture

```text
Existing browser UI (HTML / CSS / JS)
               │ same-origin REST
               ▼
Spring Boot REST controllers ── services/repositories (Spring Data JPA)
               │
               ├── Flyway versioned schema migration
               └── MySQL 8.4 (jobs, candidates, applications, audit_events)
```

Database constraints protect stage/status domains, normalized unique email, foreign keys, and one application per candidate/job. JPA optimistic locking backs the existing explicit application version. Migration files are in `src/main/resources/db/migration/`.

## Test and build

Requires Java 21, Maven 3.9+, and Docker for Testcontainers:

```sh
mvn verify
```

`RecruitmentApiIntegrationTest` exercises seeded synthetic data, job/application creation, closed-job behavior, search, valid/invalid transitions, optimistic version conflicts, scoped histories, duplicate prevention, and audit persistence against real MySQL 8.4. Locally the test uses Testcontainers; GitHub Actions runs the same test against its MySQL service container.

**Verified on GitHub Actions:** [run 42068376340](https://github.com/ivyhongzhan/recruitment-management-system/actions/runs/42068376340) — `mvn verify` passed with **5 tests, 0 failures, 0 errors, 0 skipped** on MySQL 8.4. The tests exercise all six stages, rejection, duplicate/case-insensitive candidate handling, literal wildcard search, closed/reopened jobs, validation, audit history and HTTP frontend/API serving. A CI smoke test wrote a job, restarted the Spring Boot process, and read the same MySQL record back successfully. JavaScript syntax also passed.

## Migration baseline and verification

The supplied FastAPI/SQLite source is retained under `legacy-fastapi/` and `README_FASTAPI_BASELINE.md`, separate from the runnable Java module. Before migration, its integration suite passed **15/15** under Python 3.14. The original UI files are copied unchanged to `src/main/resources/static/`; the UX screenshots and portfolio pages are retained.

The online portfolio Demo is a separate static GitHub Pages artifact. Its interactions persist in browser localStorage until reset or cleared; it is **not connected to this MySQL database** and is not a cloud-backed deployment. The public Demo is a UI preview only until it is deliberately connected to a deployed API.

## Academic and project context

The undergraduate project topic was a 2021 BEng graduation design. The current Java/Spring Boot/MySQL implementation is a new 2026 reconstruction and must not be described as recovered historical source. The supplied portfolio HTML is preserved in `portfolio/`; no user study or usability outcome is claimed.

## Repository contents

- `src/main/java`: API, persistence entities, repositories, frontend routes, synthetic seed
- `src/main/resources/db/migration`: Flyway schema
- `src/main/resources/static`: carried-forward recruitment UI
- `src/test/java`: MySQL Testcontainers integration tests
- `legacy-fastapi/`: retained pre-migration application and tests
- `portfolio/`: supplied three-direction research portfolio pages
- `docs/screenshots/`: supplied UX-reviewed UI screenshots

## Evaluation and event-history boundaries

The [formative evaluation protocol](docs/FORMATIVE_EVALUATION.md) provides task prompts, observation criteria, consent/data-handling preparation and blank recording templates. It has not been conducted; no usability or efficiency improvement is established.

The API path `/api/audit` and database table `audit_events` are retained for compatibility. They provide **operation-event history**, not a complete accountability audit. Records contain event/object identifiers, time and state changes, but no authenticated actor identity, role or reason for a decision. They are ordinary database records, not append-only or tamper-evident evidence. Transactional persistence establishes that an accepted change and its event are stored together; it does not establish who acted or whether they were authorized. Rejected requests are not a comprehensive security log.

Before a shared deployment, authentication, role authorization and server-derived actor identity are priorities, followed by explicit event semantics, access/retention controls and concurrency/failure tests. These capabilities are not implemented by the present documentation update.
