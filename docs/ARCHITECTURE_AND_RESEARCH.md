> Historical FastAPI/SQLite baseline document. For the current Java/MySQL architecture see [ARCHITECTURE.md](ARCHITECTURE.md); for the current unexecuted evaluation plan see [FORMATIVE_EVALUATION.md](FORMATIVE_EVALUATION.md). Historical audit terminology does not imply actor attribution or tamper evidence.

# Architecture and research rationale

## Scope

This is a **2026 implementation** of a recruitment-workflow system based on a topic explored in a **2021 undergraduate thesis**. It is a portfolio system with code developed in 2026. Synthetic examples are used for demonstration, not as evidence of research findings.

## System boundary

```text
Browser (HTML/CSS/vanilla JavaScript)
          |
          | REST/JSON over localhost
          v
FastAPI server (request validation, workflow policies)
          |
          | parameterized SQL / version checks
          v
SQLite database (jobs, candidates, applications, audit_events)
```

- **Client:** Semantic HTML, native dialogs, keyboard-accessible forms and tables, responsive layout. Output from user fields is escaped before use in HTML templates.
- **Service:** FastAPI + Pydantic; HTTP 422 for invalid input, 404 for absent records, 409 for workflow conflict and duplicate applications.
- **Storage:** SQLite with foreign keys, a uniqueness constraint on (candidate, job), and versioned application transitions.
- **Auditability:** A history row is written in the same database transaction as accepted state changes. This is **not** a tamper-proof audit system.

### Entity relationships

```text
jobs [1] ---------- [many] applications [many] ---------- [1] candidates
                               |
                               +-- changes noted in audit_events
```

Tables: `jobs(id, title, team, location, status, created_at)`, `candidates(id, full_name, email, created_at)`, `applications(id, candidate_id, job_id, stage, version, created_at, updated_at)`, and `audit_events(id, event_type, entity_type, entity_id, from_stage, to_stage, details, created_at)`.

### Stage policy

```text
applied --> screening --> interview --> offer --> hired
   |           |            |           |
   +-----------+------------+-----------+--> rejected
```

- Stage skips are rejected. Rejection is possible from active stages.
- `hired` and `rejected` are terminal in this version.
- Closing a job blocks **new applications** but does not delete existing ones.
- Conflicts from stale UI state return 409 with a refresh prompt via optimistic concurrency control (`expected_version`).
- A candidate can apply to multiple jobs, but cannot apply twice to the same job, even if their email casing differs.

## Research relevance and evidence boundaries

| Field | Implemented design artifact | What still requires evidence |
|---|---|---|
| Human–Computer Interaction | Clear feedback for stage updates, visible workflow constraints, responsive presentation, native-dialog form tasks | A defined user population, usability study, objective task-success/time/errors, and qualitative feedback. No user study has been run. |
| Computational Social Science | Transparently computed *descriptive snapshot* counts with explicit denominators; traceable record changes | Real, ethically collected data and preregistered or specified research questions for substantive conclusions. There is no inference, hypothesis test or fairness audit here. |
| Information Science / Information Systems | Relational workflow model, persistence, stage rules, history, API contracts, integrity constraints | Real-world requirements study, multi-user authentication, deployment security, observability, operational evaluation and privacy governance. |

**Metric definitions** (all computed from synthetic records):

- `Applications`: count of application records, **not** distinct people.
- `Unique candidates`: count of candidate records (normalized by case-insensitive email in this demo), **not** verified individuals.
- `In progress`: applications in `applied`, `screening`, `interview` or `offer`; excludes terminal `hired` and `rejected`.
- `Current pipeline distribution`: count of applications whose **current** stage is each category; each application counted exactly once. This is **not** a longitudinal funnel, transition probability or conversion rate.
- `Applications per job`: number of application records associated with each job, whether open or closed.

**No causal, fairness or performance results** can be inferred from the fictional dataset. In particular, the system does not profile applicants on protected attributes, rank candidates, or make automated hiring recommendations.

## Possible HCI study protocol (planned, not conducted)

- **Question:** Does an explicit, auditable next-step control reduce errors and improve task comprehension compared with a less constrained workflow interface?
- **Design:** Within-subject comparison of two counterbalanced prototypes using representative tasks (add applicant, find candidate, progress stage, explain rejected transition). Participants should reflect a defined recruiting-user population; do not recruit before protocol and ethics review as applicable.
- **Measures:** Task success, completion time, recoverable errors, workflow comprehension questions, and post-task subjective usability; report distributions and uncertainty, not just averages.
- **Analysis:** Predefine unit of analysis, inclusion criteria, missing data handling and effects of learning/order. Assess whether results support design implications, and avoid generalizing beyond the sample.
- **Status:** **Not conducted. No participants, results or effect sizes are claimed.**

## Known limitations

Local demo only. No authentication, role authorization, rate limiting, CSRF protections, production deployment configuration, secure secrets management, backup/restore policy, privacy notices, or retention policy. It must **not** be exposed publicly with real candidate information or used to make hiring decisions. No claim of WCAG conformance or empirically validated usability is made; formal evaluation remains future work.
