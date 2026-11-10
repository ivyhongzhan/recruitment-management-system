# Recruitment Lens — descriptive analytics extension (2026)

**Live static demo:** https://ivyhongzhan.github.io/Ivyhongzhan/assets/recruitment-lens.html  
**Source:** [index.html](index.html) (self-contained HTML/CSS/JavaScript and embedded synthetic records)

This is a separate, **read-only research-oriented prototype** developed in 2026. It extends the recruitment workflow *topic* of the original BEng graduation design and complements, but is **not the frontend or database of**, the Spring Boot/MySQL Recruitment Management System in this repository. This file does not call the Java API. No 2021 original source code was recovered for this analytics extension.

## What the demo does

- Explore a five-stage **ever-reached** funnel and its explicit denominator.
- Filter synthetic application records by department, source channel and cohort.
- Compare interview/offer stage reach within source channels.
- Drill from group summaries into generated application-event timelines.
- Search, page through records and export the filtered set to CSV.

All **360 records are synthetic**; the standalone page embeds them and works without a server. No real applicants, contact information or protected-group labels are used. It does not score candidates or recommend hiring decisions.

## Measurement boundaries

- *Reach to stage S* = records whose furthest stage is S or later, divided by all records in the selected filter. This is not the transition probability from the preceding stage.
- Each channel-level rate uses that channel's own filtered count as the denominator.
- Filtered cohorts have different compositions. Apparent channel differences are manufactured properties of the simulation—not empirical hiring evidence, causal effects or demographic fairness metrics.
- No usability study, accessibility certification, inferential analysis or predictive validation was performed for this 2026 prototype.

## Three complementary application directions

| Research direction | Evidence shown by this prototype |
| --- | --- |
| HCI / Human-centered systems | Transparent metric labeling, exploratory interaction, drill-down from aggregates to individual synthetic events |
| Computational social science | Auditable descriptive subgroup measurements with explicit unit and denominator definitions |
| Information science / systems | Self-contained web interface, event representation, deterministic data design, export and independently testable logic |

**Important:** this analytics extension is distinct from the operational ATS demo and from the Java/MySQL integration tests elsewhere in the repository. It is also distinct from the **2021 capstone**, whose original code and evaluation materials were not independently inspected when developing this extension.

**Project author:** Hong Zhan.
