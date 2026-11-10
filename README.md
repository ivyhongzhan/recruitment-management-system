# Recruitment Management System

**2026 implementation: Java 21 + Spring Boot + Spring Data JPA + MySQL 8.4.** I retained the browser interface and interaction design from the FastAPI implementation while migrating the REST backend to Spring Boot and preserving the `/api/*` contract. The Python baseline is in `legacy-fastapi/`.

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

The FastAPI/SQLite baseline is retained under `legacy-fastapi/` and `README_FASTAPI_BASELINE.md`, separate from the runnable Java module. Before migration, its integration suite passed **15/15** under Python 3.14. The browser interface and interaction behavior are retained in `src/main/resources/static/`, alongside the earlier UX screenshots and portfolio pages.

The online portfolio Demo is a separate static GitHub Pages artifact. Its interactions persist in browser localStorage until reset or cleared; it is **not connected to this MySQL database** and is not a cloud-backed deployment.

## Original capstone and 2026 reconstruction

For my **2021 BEng capstone**, I designed and implemented job posting, application tracking, and candidate management with **Spring Boot and MySQL**. I conducted **think-aloud usability testing with 8 participants** and used the findings to revise the interface.

The code in this repository is a **2026 reconstruction**, not recovered thesis source. The current interface and backend have engineering tests, but no new participant study has been conducted for this version. The case studies are in `portfolio/`.

## Research analytics extension (Recruitment Lens, 2026)

A separate [read-only browser demo](https://ivyhongzhan.github.io/Ivyhongzhan/assets/recruitment-lens.html) explores how recruitment stage-reach fractions, subgroup denominators, and event histories can be presented transparently. Its [self-contained HTML/CSS/JavaScript source and methodological limits](analytics-lens/README.md) are in `analytics-lens/`. The 360 records are entirely synthetic.

This addition illustrates **HCI interface choices**, **computational descriptive analysis**, and **information-systems provenance**. It does **not** use this application's Java API or MySQL database, conduct hiring decisions, or establish an empirical fairness finding.

## Repository contents

- `src/main/java`: API, persistence entities, repositories, frontend routes, synthetic seed
- `src/main/resources/db/migration`: Flyway schema
- `src/main/resources/static`: carried-forward recruitment UI
- `src/test/java`: MySQL Testcontainers integration tests
- `legacy-fastapi/`: retained pre-migration application and tests
- `portfolio/`: research case studies
- `docs/screenshots/`: interface screenshots from the earlier UX review

## Evaluation and event-history boundaries

The [formative evaluation protocol](docs/FORMATIVE_EVALUATION.md) provides task prompts, observation criteria, consent/data-handling preparation and blank recording templates. It has not been conducted; no usability or efficiency improvement is established.

The API route `/api/audit` and table `audit_events` store **operation-event history**: event and object identifiers, timestamps, and state changes. Accepted updates and their events are stored in the same transaction. The log does not identify an authenticated operator, establish authorization, or provide tamper-evident records; rejected requests are not comprehensively logged.

Authentication, role permissions, actor identity, and data-retention controls would be needed before shared deployment. The current application is intended for isolated use with fictional data.
