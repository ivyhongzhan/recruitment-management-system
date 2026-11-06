> Historical FastAPI/SQLite baseline document. For the current Java/MySQL architecture see [ARCHITECTURE.md](ARCHITECTURE.md); for the current unexecuted evaluation plan see [FORMATIVE_EVALUATION.md](FORMATIVE_EVALUATION.md). Historical audit terminology does not imply actor attribution or tamper evidence.

# Recruitment Management System — UX / product review

**Review date:** 5 November 2026  
**Scope:** 2026 demonstration implementation and its PhD portfolio presentation, not the unavailable original 2021 thesis source.  
**Verdict:** The original MVP demonstrated state management and persistence, but the interface and case-study entry point underexplained the system's value. The revised interface adds a guided tour, a stage board, and application history. Its effect on user performance has not been evaluated.

## Evidence-based review and implemented responses

| Prior limitation | Priority | Change implemented | Verification/evidence boundary |
|---|---|---|---|
| Hard to evaluate quickly without knowing where to begin | High | Overview now provides a 90-second, three-task product tour; case study presents the research problem, evidence and limitations first | Navigation and primary actions exercised in offline Chromium; no external user study |
| Candidate management was table-only | High | Six-stage board with explicit horizontal-scroll guidance and board/table switch; consistent server-backed actions | Board rendered from seeded API records; list/board controls exercised; not compared with real recruiter performance |
| No application-specific inspectable history | High | Candidate details dialog with a per-record REST endpoint and stage event history | New API integration test passes; dialog opened successfully during Chromium DOM exercise |
| Compact / low-contrast secondary text | Medium | Increased key text sizes and improved selected small-text colors; semantic labels, native dialogs and keyboard focus cues retained | Visual desktop/mobile preview and sample CSS contrast spot-check; **not** WCAG conformance certification |
| Portfolio described features but not design reasoning | High | Reworked systems case study as: problem → 90-second tour → design decisions → architecture → research perspectives → verification and limitations | Static HTML routes and internal references checked; no independent professor review |
| Snapshot charts might be confused for a hiring conversion funnel | High | Stage chart explicitly states current-state denominator; no percentage conversions, rankings or fairness claims | Metric definitions trace back to SQL counts from fictional records |
| Demo could be misread as a secure hiring product | High | Persistent synthetic-data labels and explicit absence of authentication and real hiring suitability | Disclosure verified in UI/README; actual security hardening intentionally out of scope |

## Context and standards used

1. [Greenhouse — What is an applicant tracking system?](https://www.greenhouse.com/resources/glossary/what-is-an-applicant-tracking-system-ats) describes job creation, applicant stages, interview review, teamwork and reporting as common ATS workflows. This **informs** product coverage; this prototype does not claim Greenhouse feature parity.
2. [Greenhouse — Visual Candidate Pipeline (updated March 2, 2026)](https://support.greenhouse.io/hc/en-us/articles/4874727408795-Visual-Candidate-Pipeline) documents per-stage visual layouts. This **motivates** a board alternative to a table, not a claim of copying or empirical superiority.
3. [Nielsen Norman Group — Ten Usability Heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) informs visible system status, error prevention, recognition over recall, and clear recovery.
4. [W3C — WCAG 2.2](https://www.w3.org/TR/wcag/) provides the accessibility benchmark for focus visibility, target sizing, input semantics and status messaging. This site has **not** undergone formal WCAG 2.2 AA audit.

## What is implemented and verifiable

- FastAPI + SQLite with jobs, candidates, applications and audit event tables.
- Stage state machine, validation, duplicate safeguards and optimistic version checks.
- Search/filter, responsive overview, board/table switch, candidate details, job status and history views.
- A standalone, offline-readable research portfolio with case study explaining three research perspectives.
- No real applicant data, automated candidate ranking or real user-study outcomes.

## Testing actually performed

1. `python -m pytest -q` — 13 passing API / route / persistence tests as of this review.
2. `node --check app/static/app.js` — passed syntax check.
3. Chromium was able to render the HTML/CSS via `page.set_content`, with JavaScript `fetch` bridged to FastAPI `TestClient` (in-memory request adapter; not HTTP browsing). Tested: initial metrics, board rendering, candidate details dialog, table switch, creating and searching an applicant, advancing to Screening, recording a history event, creating a job, closing a job, and rendering the activity log.
4. Desktop width 1440px, mobile width 390px: document-level horizontal overflow was 0px for the overview/case study in the preview setup. The board intentionally scrolls horizontally within its container.
5. Real browser visits to `http://127.0.0.1` were restricted by the environment (`ERR_BLOCKED_BY_ADMINISTRATOR`). Thus the Chromium exercise verifies DOM/display and API calls through the test adapter, **not** complete browser-to-server networking or independent deployment.
6. Preview images under `docs/screenshots/` are captures from the Chromium offline HTML rendering using synthetic API data, **not** screenshots from an externally deployed site. They can be used in README with this caveat.

## Limitations worth resolving before public release

- Real on-device Chrome/Safari browser-to-server end-to-end test, including multiple browser sizes and keyboard-only flows.
- Formal accessibility review including screen reader, zoom, contrast, dialogs, and meaningful error announcements.
- Authentication/roles, rate limits, privacy safeguards, CSRF and operational security if expanding beyond local synthetic demo.
- An HCI task study is needed before claiming effectiveness or design implications. Candidate artifacts without empirically derived outcomes are **research prototypes**, not research findings.

## Proposed HCI evaluation (not conducted)

**Research question:** Does displaying valid next actions and individual transition history improve task clarity and reduce mistakes?  
**Possible participants:** Adults with or without prior recruiting experience, stratified or analyzed by expertise; actual sampling would require a justified target population and protocol.  
**Tasks:** Locate a candidate by role; change from Applied to Screening; diagnose an invalid stage jump; explain why the stage is currently shown.  
**Outcomes:** Per-task completion, elapsed time, errors and recovery, post-task confidence, comprehension score, qualitative feedback; clearly define units and missingness before collection.  
**Design:** Counterbalanced within-subject experiment or carefully planned formative comparison between workflow controls with and without explicit constraints.  
**Inference:** Do not claim the visual board improves performance from seeing it alone. Do not extrapolate synthetic applicant counts into hiring equity or real-world outcomes. Design implications must follow observed evidence.
