# Agent Roles And Workflow

## Purpose

This file defines how the agentic development team should work inside TestStream. The goal is not just to ship code. The goal is to ship well-scoped, reviewable work that improves the project without increasing architectural debt.

Planning and review are mandatory parts of the workflow here.

## Core Operating Rules

- Read `.context/PROJECT.md`, `.context/ARCHITECTURE.md`, and `.context/STATE.md` before starting substantial work.
- Start with review before implementation.
- For non-trivial work, present a short plan to the user before coding.
- Keep tasks scoped to one feature or one clearly defined refactor target whenever possible.
- Prefer one ticket, one branch, one coherent PR.
- Add or update tests with the feature.
- Update `.context/STATE.md` when a meaningful feature lands or the project state materially changes.
- When using sub-agents, give them disjoint ownership and a narrow goal.

## Mandatory Review-First Process

Every non-trivial task should follow this sequence:

1. Review the relevant feature files and existing tests.
2. Identify the affected layers: controller, service, repository, template, JS, deployment, or docs.
3. Check the change against `ARCHITECTURE.md`.
4. Summarize the intended plan to the user.
5. Only then begin implementation.

For this project, "non-trivial" includes:

- changes touching more than one feature area
- package moves or architectural refactors
- new API endpoints
- database or schema changes
- Docker, CI, or Render changes
- large frontend work
- anything affecting security, auth, or team scoping

Small, contained fixes can move faster, but they still require local review first.

## When The AI Must Pause And Realign With The User

Pause and get alignment before proceeding when a task would:

- change package structure
- introduce or remove shared abstractions
- alter database shape or migration behavior
- change deployment assumptions for Docker or Render
- affect auth, permissions, or team isolation
- require touching many files across the repo
- create a tradeoff between short-term delivery and long-term maintainability

## Role Definitions

### Architect / Planner

Responsibilities:

- read the memory bank first
- inspect the current code path before proposing changes
- identify risks, dependencies, and file ownership
- produce a short implementation plan
- protect MVC boundaries and package discipline

This role leads first on any major task.

### Reviewer

Responsibilities:

- independently challenge the proposed plan or patch
- look for controller bloat, service bloat, and misuse of `shared`
- verify tests, edge cases, and regression risk
- call out missing user-visible behavior or missing acceptance coverage

This role should be involved before or during implementation, not only after.

### Backend Implementer

Responsibilities:

- implement service, controller, repository, DTO, and model changes inside the owning feature package
- keep controllers thin
- preserve transactions and team scoping
- avoid leaking persistence entities across boundaries

Restrictions:

- do not add feature logic to `shared` unless the reuse rule is met
- do not bypass service orchestration from controllers

### Frontend Implementer

Responsibilities:

- implement Thymeleaf and JavaScript work in feature-based folders
- extract behavior out of inline template scripts when pages begin to grow
- keep one small page boot file per page
- split large page templates into feature-aligned fragments once the JS seams are established
- prefer reusable components over more code in giant page scripts

Restrictions:

- do not add major new logic into `workspace/page.js` if it can be isolated
- do not keep growing flat template structure without first creating folders
- do not create vague shared frontend helpers for feature-specific behavior

### QA / Test Agent

Responsibilities:

- add or update integration and service tests with each feature
- verify happy paths and failure cases
- confirm transaction rollback expectations for bulk or ingestion features
- verify that team scoping and auth rules remain intact

Restrictions:

- do not sign off on a feature that lacks meaningful regression coverage

### DevOps / Release Agent

Responsibilities:

- maintain Docker, CI, environment configuration, and Render readiness
- protect stateless deployment assumptions
- verify environment variable usage and proxy-related behavior
- flag production-sensitive persistence or schema changes early

Restrictions:

- any deployment-sensitive change requires explicit review with the user first

### Memory Keeper

Responsibilities:

- keep `.context` current
- update `STATE.md` after meaningful changes
- record architectural decisions that future agents will need
- remove stale assumptions when the codebase changes

## Delegation Rules

- Use sub-agents for bounded research, isolated implementation, or parallel review.
- Do not give multiple agents overlapping write ownership.
- Do not skip the planner/reviewer phase just because work is delegated.
- Delegation is for focus and speed, not for bypassing discipline.

## Definition Of Ready

Work is ready to implement when:

- the user goal is clear
- affected feature areas are known
- likely files are identified
- architecture constraints are understood
- test expectations are defined

## Definition Of Done

Work is done when:

- the implementation matches the agreed plan
- tests are added or updated as needed
- the architecture rules were followed
- no unnecessary shared abstractions were added
- docs and state are updated if the project context changed

