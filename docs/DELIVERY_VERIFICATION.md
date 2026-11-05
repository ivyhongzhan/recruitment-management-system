# Delivery verification log

## FastAPI migration baseline

- ZIP: `Recruitment_Management_System_UX_Reviewed_2026 (1).zip`
- Confirmed required files: `app/main.py`, `app/db.py`, `app/static/{index.html,app.js,styles.css,polish.css}`, `tests/test_api.py`, `README.md`.
- Python 3.9 collection attempt: failed because source uses Python 3.10 union type syntax.
- Python 3.14 isolated environment: `15 passed`.

## Java/MySQL migration

- Source implementation, Docker Compose, Flyway migration, and MySQL Testcontainers integration tests added.
- Full supplied FastAPI application, tests, launcher, and requirements retained under `legacy-fastapi/`.
- Original static UI copied to Spring Boot's `src/main/resources/static/` and served same-origin.
- CI initially found and drove fixes for Java compilation errors. The runner's Docker socket uses unsupported API 1.32, so CI now supplies a MySQL 8.4 service container and the same integration test class switches to it when configured; local execution can use Testcontainers.
- Java build/MySQL/API verification is pending the current GitHub Actions run. No passing result is claimed until that run completes successfully.
- GitHub publication and personal-site/CV updates are pending successful local/CI verification and connected GitHub access.

## Synthetic-data check

Seed records use invented names and reserved `.example.test` email addresses. No historic thesis data or real applicant records are imported.
