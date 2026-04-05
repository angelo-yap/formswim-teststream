# Current State

Last updated: 2026-04-05

## Summary

TestStream is a working Spring Boot and Thymeleaf application with core authentication, team-scoped workspace access, import/export, upload conflict review, workspace filtering, a dedicated details page, and bulk mutation workflows already in place. Iterations 1 and 2 are largely represented in the codebase. Iteration 3 is now focused on architectural cleanup, deployment hardening, and undo/redo, with the workspace frontend modularization now delivered.

## Delivered So Far

### Iteration 1 Foundations

- registration and login flow
- logout flow
- protected workspace access
- login throttling / brute-force protection
- team-scoped user model
- initial role model with `USER` and `ADMIN`
- Excel/CSV ingestion into the database
- export of selected test cases to Excel

### Iteration 2 Workflow Improvements

- team-code based registration into an existing workspace
- team-code throttling protection
- folder filtering and workspace metadata APIs
- dedicated test case details page
- duplicate upload conflict review flow with apply/cancel
- bulk move endpoint and workspace flow
- bulk edit backend and modal-driven workspace support with fixed status assignment, case-sensitive replacement, and Last updated refresh
- stronger test coverage across auth, workspace, ingestion, export, and bulk features
- GitHub Actions CI running tests against PostgreSQL

### Iteration 3 — Single Field Editing (branch 45-single-test-edit-adapt-width, delivered 2026-04-03)

- single-field inline editing on the test case details page using the existing bulk edit API (`PATCH /api/testcases/bulk-edit`)
- team-scoped and auth-enforced; consistent with bulk edit security model
- double-click to edit for content-heavy fields (summary, description, precondition, story linkages, step summary, test data, expected result)
- click to edit for metadata fields (status, priority, folder, components, test case type, labels, sprint, fix versions, version, estimated time)
- `updatedOn` auto-stamped in `TestCaseBulkEditService` on every successful case or step mutation, formatted `dd/MMM/yyyy HH:mm`
- keyboard shortcuts: Escape to cancel, Ctrl+Enter to save in textareas
- unsaved changes warning via `beforeunload`
- inline yellow flash on field display after successful save
- color-coded notice banner (yellow dot for success, red dot for error, dim dot for no-changes)
- no-changes short-circuit with neutral banner feedback
- auto-growing textareas (grows on input, capped at `max-h-96`, scrollable beyond)
- pencil icon discoverability on hover for all double-click fields
- trailing whitespace trimmed from textarea on open to avoid empty-space bloat from imported data
- adaptive page width (max-width cap removed)
- 4 integration tests covering single-field edit, cross-team rejection, auth guard, and step field edit; `updatedOn` format assertion added

## Iteration 3 Focus

Iteration 3 is in progress.

Recent delivery:

- folders are now first-class entities in a dedicated `folders` table with parent-child relationships (`parent_id`)
- startup backfill now hydrates folder rows from legacy slash-delimited `test_case.folder` values
- `GET /api/folders` now reads derived paths from the folder table while keeping the existing `List<String>` response shape
- folder APIs now support create, rename/reparent with cycle rejection, and explicit delete with conflict safeguards
- workspace sidebar now supports New Folder, folder context-menu actions, and folder drag/reparent behavior
- workspace theme now defaults to a softer dark gray and includes a header theme selector (`Dark Gray` / `High Contrast`) persisted in `localStorage`
- workspace header now uses a single animated settings menu (theme selector, team code, account, logout), with account controls removed from the main header row
- workspace notice popup can now be dismissed via both `OK` and `X`, and notice/bulk bars now visually follow the selected workspace theme
- folder delete confirmation is now a centered modal with blue-tinted backdrop, theme-aware styling, and keyboard focus trapping

Primary active goals:

- modularize ETL code and finish architectural cleanup around the workspace backend
- reduce controller and service duplication
- implement undo/redo safely
- harden Docker and Render deployment readiness

Completed in Iteration 3 so far:

- single-field editing on the test case details page (see above)

## Codebase Snapshot

### Backend

Current top-level backend features:

- `auth`
- `workspace`
- `bulk`
- `ingestion`
- `export`
- `shared`

Important state notes:

- the backend is already moving toward feature-based packaging
- controller logic is duplicated in places, especially around current-user and team checks
- service logic is doing most of the heavy lifting, which is good, but some services are growing large
- `shared.domain` currently holds `TestCase`, `TestStep`, and `TestCaseRepository`, which is useful today but a likely future bloat point

### Frontend

Current frontend shape:

- the workspace page now uses feature-scoped JS modules under `src/main/resources/static/js/workspace`
- the workspace template now composes fragments from `src/main/resources/templates/workspace`
- workspace bulk edit now supports explicit field selection, fixed status changes, case-sensitive matching, and drawer preview access from the modal
- workspace row preview now uses inline expandable rows in the grid (multi-expand), rather than opening from row actions into the right-side drawer
- workspace preview UX now includes in-card collapse controls, a global `Collapse all previews` control, and selected-row/preview linked highlighting
- workspace grid now includes truncation-aware title tooltips (hover/focus only when truncated)
- shared tooltip behavior was extracted into `src/main/resources/static/js/workspace/components/workspace-tooltip.js` and consumed by grid rendering
- other pages still use the older flat-template and inline-script patterns

Known frontend hotspots:

- `upload-review.html` still embeds significant review logic in the template
- shared layout fragments are not established yet across the rest of the app
- `test-case-details.html` and `test-case-details.js` are now feature-complete for single-field editing but are growing large; future work may want to split the JS into sub-modules

### Tests

There is meaningful test coverage under:

- `src/test/java/com/formswim/teststream/auth`
- `src/test/java/com/formswim/teststream/workspace`
- `src/test/java/com/formswim/teststream/ingestion`
- `src/test/java/com/formswim/teststream/export`
- `src/test/java/com/formswim/teststream/bulk`

This is a strength of the current project and should remain part of the definition of done.

## Known Gaps

- Undo/redo is not implemented yet.
- Admin flow is incomplete.
- Role support exists in the model, but there is no admin controller, admin template, or distinct admin dashboard in the current repo.
- Shared domain and repository code is growing faster than the module boundaries around it.
- Frontend organization is behind the pace of feature growth.
- Render-specific deployment configuration is not checked into the repo yet.
- Empty fields on the test case details page cannot be edited (the find/replace API requires existing text); users must set initial values via bulk edit in the workspace.
- The folder edit field has no click target when no folder is currently assigned to the test case.

## Known Debt And Risks

- repeated auth and team-resolution logic across controllers
- oversized feature hotspots such as bulk edit logic and upload review orchestration
- direct use of shared domain entities across multiple feature boundaries
- `spring.jpa.hibernate.ddl-auto=update` plus startup schema adjustment is fast for iteration, but risky as production deployment matures

## Deployment State

What is already present:

- Docker multi-stage build in `Dockerfile`
- PostgreSQL runtime configuration via `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
- `server.forward-headers-strategy=framework` for proxy-aware deployments
- CI pipeline that runs tests against PostgreSQL

What is still maturing:

- final Render deployment shape and project wiring
- explicit migration strategy for production-safe schema changes
- deployment documentation and checked-in platform config

## Immediate Architectural Priorities

- standardize feature package internals
- reduce repeated controller auth/team checks through a stronger request-context abstraction
- move inline template scripts into feature JS files
- keep new shared abstractions narrow and intentional

## Immediate Product Priorities

- finish Iteration 3 modularization work
- implement undo/redo in a transactional, auditable way
- preserve the editor-first experience while refactoring
- keep test coverage aligned with every new workflow

## Working Assumptions For Future Agents

- Iterations 1 and 2 are the baseline context for current work.
- Iteration 3 should prioritize maintainability as much as feature delivery.
- The product should remain editor-first, not drift into generic enterprise management scope.
- Any work that touches architecture, deployment, or cross-feature refactors should be reviewed with the user before implementation.

