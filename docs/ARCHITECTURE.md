# Architecture and migration notes

## Before migration

The FastAPI/SQLite baseline used FastAPI routes in `app/main.py`, direct SQLite sessions/schema in `app/db.py`, and same-origin HTML/CSS/JavaScript under `app/static/`. It modeled jobs, candidates, applications, and audit events. Applications moved through six controlled stages with a version check. Its test suite passed 15/15 on Python 3.14 before migration.

## Current implementation

Spring Boot 3.5 / Java 21 serves the unchanged static UI and the original JSON routes. Spring Data JPA persists four entities to MySQL. Flyway owns schema changes; Hibernate is set to validate, never mutate the production schema. The database stores stages/statuses with CHECK constraints and protects relationships/uniqueness with foreign keys and unique indexes. Updates run transactionally and audit events are inserted with the state change.

## Data flow

`Browser → REST Controller → JPA Repository → MySQL`.

Creation and transitions validate inputs and workflow rules before commit. The explicit `expected_version` API check remains for client compatibility, with JPA `@Version` as a second concurrency guard. Search treats input as data through parameterized JPQL. Metrics are descriptive counts only.

## UX preservation

The HTML, JavaScript, and styles are retained in Spring’s static resources. Existing screenshots document the earlier interface review.

## Limitations

No authentication, authorization, production privacy/security controls, public API deployment, or real-applicant support is provided. Synthetic seed data uses `.example.test` addresses. The hosted static portfolio Demo is independent and does not connect to the MySQL instance in this repository.

## Event history limits

See the README evaluation and event-history boundaries. The `audit_events` name denotes transactional operation events; no authenticated actor, tamper evidence or full security audit is provided.
