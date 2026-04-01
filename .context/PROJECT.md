# TestStream

## Purpose

TestStream is an editor-first test case management web application for QA teams. Its job is not to replace enterprise repositories like QMetry, TestRail, or Zephyr as systems of record. Its job is to remove the editing friction those tools create when teams need to reorganize, clean up, bulk edit, and review large volumes of test cases.

This file gives future agents and teammates the product context behind the codebase so implementation decisions stay tied to the real problem being solved.

## Product Identity

- TestStream is a web workspace for fast test case maintenance.
- The product centers on bulk editing, folder organization, search, and safe data transformation.
- Import/export matters because teams already live in QMetry and spreadsheets, but ETL is a means to support the editor workflow, not the product's identity.
- The core promise is speed, lower friction, and safer large-scale edits.

## Stakeholders And Users

- Primary client stakeholders: Form Swim.
- Primary end users: QA Engineers managing large numbers of test cases and steps.
- Secondary users: test leads or managers who need structure, visibility, and safe batch operations.
- Future broad audience: QA teams across the software industry that are frustrated by slow enterprise editing workflows.

## Problem Statement

Legacy test management tools are strong at storage, auditability, and formal tracking, but they are poor active editing environments. QA teams run into the same bottlenecks repeatedly:

- folder navigation and search are slow or too limited
- bulk operations are tedious or artificially capped
- restructuring large repositories is cumbersome
- spreadsheet-based workarounds are fast but unsafe and disconnected from the source of truth
- duplicate imports and conflicting updates create review overhead

TestStream exists to bridge that gap: spreadsheet-like editing speed with database-backed safety and team-scoped access.

## Competitive Positioning

- Primary competitors: Jira/QMetry, TestRail, and Zephyr.
- Competitive weakness in the market: these tools are effective repositories but poor editors for mass maintenance.
- TestStream differentiator: an intentionally narrow, editor-first workflow with faster organization, bulk mutation, and import-review-export loops.

## Product Principles

- Editor first: optimize for speed of maintenance, not generic enterprise breadth.
- Safe bulk operations: large edits must be transactional, reviewable, and team-scoped.
- Interoperability over lock-in: import from and export to existing enterprise workflows cleanly.
- Low-friction organization: foldering and workspace structure should feel lightweight, not bureaucratic.
- Reversibility: users must be able to safely recover from mistakes; undo/redo is strategic, not optional polish.
- Maintainability matters: the product will keep growing, so architecture must support iteration without controller or frontend sprawl.

## Scope Snapshot

### Iteration 1

Iteration 1 established secure access and the initial data pipeline.

- account registration with secure password hashing
- login/logout with protected workspace access
- brute-force protection for login
- team-scoped access for standard users
- role model foundation for admin support
- Excel/CSV ingestion into the database
- export of selected test cases to QMetry-shaped Excel output

### Iteration 2

Iteration 2 focused on core editor-first workflow improvements and stronger team process.

- folder organization and bulk move flows
- join-existing-team registration using a 16-character team code
- throttling against team code brute force attempts
- upload conflict review flow for changed duplicate test cases
- dedicated test case details page replacing the drawer-only preview direction
- bulk edit backend foundation with transactional safety
- stronger GitHub Projects, issue scoping, PR review, and CI-backed test habits

### Iteration 3

Iteration 3 is underway and is focused on finishing the workflow foundation cleanly rather than only adding headline features.

- refactor workspace and ETL architecture for maintainability
- reduce controller and service bloat
- improve frontend modularity and file structure
- finish undo/redo support
- continue deployment hardening for Docker and Render

## Current Feature Status

- Authentication and registration: implemented.
- Team-scoped workspace access: implemented.
- Import/export pipeline: implemented.
- Upload duplicate review flow: implemented.
- Workspace filtering and dedicated details page: implemented.
- Bulk move: implemented.
- Bulk edit foundation: implemented, still being refined.
- Admin landing/dashboard flow: not yet implemented end to end.
- Undo/redo: planned for Iteration 3, not yet delivered.
- Visualization/dashboard features: later-cycle work.

## Workflow Expectations

- Every feature should be traceable to a user-facing editing or safety improvement.
- Ticket scope should be small enough to review quickly.
- One ticket should map to one branch and one coherent PR.
- Tests should ship with the feature, not afterward.
- Main should stay production-ready whenever possible.

## What Good Decisions Look Like

When choosing between implementation options, prefer the option that:

- reduces editing friction for QA users
- keeps large data operations safe and transactional
- preserves team scoping and security guarantees
- improves maintainability for future iterations
- avoids generic platform bloat that does not serve the editing workflow

## Non-Goals Right Now

- becoming a full enterprise test management replacement
- adding broad administrative surface area before the core editor workflow is stable
- growing the feature set faster than the architecture can safely support

