# Formative usability evaluation protocol — not conducted

Status: planning material created in 2026. No participants, observations, timings or usability outcomes are reported. Automated integration checks are engineering evidence, not user-study evidence.

## Purpose and setup

Explore whether people who manage recruitment can understand stage constraints, find an application, make a permitted change and explain its history. This is a formative problem-finding exercise, not a comparison establishing improved efficiency.

Use the local Spring Boot/MySQL application with fictional seed data in an isolated environment. Record the commit, browser, device, viewport and reset procedure. The public localStorage preview is a different implementation: if tested, record it as a separate variant and do not pool its observations with the Java application.

## Participants and consent

A proposed first round is 4–6 adults with recruitment coordination experience, recruited voluntarily without managerial pressure. This is a planning target, not a saturation or statistical-power claim. Record only a participant code and broad relevant experience. Before recruitment, determine the institutional ethics and consent requirements for the intended use and obtain any required approval. Do not claim approval from this protocol.

Explain the purpose, voluntary participation, ability to stop or skip a question, intended reporting, data collected and a practical withdrawal deadline. Obtain consent before recording observations. Screen/audio recording requires separate explicit consent; written notes are sufficient. Use fictional applicants only. Avoid capturing personal browser tabs or employer records. Specify access, retention and deletion arrangements before collecting data; keep consent/contact records separately from observations.

## Moderator instructions

Use the same neutral prompts, allow think-aloud without coaching, and record assistance separately. There is no scored speed target. For each task record independent completion, assisted completion, non-completion or skipped, observable errors, recovery and the participant's explanation. If timing is used, define start at the end of the prompt and end at completion or abandonment; report it only descriptively and disclose assistance and think-aloud effects.

| Task | Neutral prompt | Observable completion criterion |
| --- | --- | --- |
| T1 Find a record | Find Alex Rivera's application and tell me its current stage and position. | Correct record, stage and position identified. |
| T2 Change stage | Move that application to the next appropriate stage and check that the change was saved. | Screening → Interview succeeds and the new state is visible. |
| T3 Explain history | What does the history tell you, and what does it not tell you about who made the change? | Identifies recorded change/time and recognizes absent verified actor identity; record misconceptions verbatim without treating this as a standardized test. |
| T4 Recover from an error | Try adding the same fictional applicant to the same position again. What happened and what would you do next? | Duplicate is rejected and participant can explain feedback; observe recovery rather than prescribing it. |
| T5 Understand limits | Can this application be marked withdrawn or moved back? What would you expect? | Captures expectations about unsupported outcomes and any confusion with Rejected. |

After each task ask what was confusing and what feedback was expected. End by asking which workflow exceptions matter in the participant's work. Stop if the participant wishes to stop or real applicant data is entered; follow the agreed data-handling plan.

## Analysis and reporting

Use the blank observation CSV and issue log. Describe observed problems with task context, evidence, consequences and proposed changes. Distinguish moderator observation from participant interpretation. Count task outcomes with their denominators, retaining assisted and skipped outcomes; do not infer population rates from this small purposive sample. Include contrary experiences and limitations. Prioritize issues by observed consequence, recurrence and recoverability, documenting the rationale rather than assigning an unsupported universal severity score.

Do not fill templates with invented sessions. A later report must state actual recruitment, sample, consent arrangements, variant/commit, deviations, outcomes and unresolved issues. No efficiency, accessibility-conformance or comparative usability claim follows from this unexecuted plan.
