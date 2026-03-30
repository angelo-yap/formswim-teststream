# Architecture Guide

## Purpose

This project is a Java Spring Boot, Thymeleaf, and PostgreSQL application deployed through Docker and intended for Render. This document defines the architectural boundaries future work must follow so the codebase stays modular as Iteration 3 refactors land.

## Current Technical Snapshot

- Java 17
- Spring Boot with Spring MVC, Spring Security, Spring Data JPA, and Thymeleaf
- PostgreSQL in runtime environments
- H2/Postgres-backed test profile support
- server-rendered pages with JavaScript-enhanced interactions
- Docker multi-stage build
- GitHub Actions CI with PostgreSQL service container

The current backend is already mostly feature-packaged at the top level:

- `auth`
- `workspace`
- `bulk`
- `ingestion`
- `export`
- `shared`

That is the right direction. The next step is to make the internal structure of each feature consistent and keep `shared` from becoming a dumping ground.

## Architectural Principles

- Feature-first organization beats technical-layer sprawl.
- Controllers stay thin.
- Services own business logic, validation, and transactions.
- Repositories only handle persistence concerns.
- Shared code is allowed only for true cross-cutting concerns.
- Frontend pages should have small page boot files that compose modules, not giant page scripts.
- Reuse aggressively, but promote code into shared space only when it is genuinely cross-feature and stable.
- Design for stateless deployment on Render: no reliance on local disk persistence or instance-local memory for durable business state.

## Strict MVC Boundaries

### Controller Rules

Controllers are HTTP adapters only.

- Accept request data, auth context, and route parameters.
- Delegate work to services.
- Return view names, redirects, response payloads, and status codes.
- Build small view models only when necessary for templates.
- Never query repositories directly unless a temporary legacy seam already exists and the task is explicitly a refactor toward service ownership.
- Never contain cross-entity business rules, transaction coordination, or duplicate ownership logic.

### Service Rules

Services own application logic.

- Validate business rules.
- Enforce team-scoped ownership and authorization rules.
- Orchestrate transactions.
- Call repositories.
- Map domain data into DTOs or view models for controllers.
- Coordinate multi-step workflows such as upload review, bulk edit, bulk move, export selection, and future undo/redo.

### Repository Rules

Repositories own persistence only.

- Query and save data.
- Expose clearly named methods tied to domain access needs.
- Do not contain UI shaping logic.
- Do not contain redirect or response logic.
- Do not become cross-feature policy objects.

### DTO / ViewModel Rules

- Controllers should not expose JPA entities directly to JSON or templates by default.
- Request DTOs, response DTOs, and template view models should be feature-specific.
- If a page only needs a subset of `TestCase`, map that subset intentionally.

## Package Strategy

Use feature-based packages with a consistent internal structure. Standardize on singular package names going forward.

Target pattern:

```text
com.formswim.teststream
  auth/
    controller/
    service/
    repository/
    dto/
    model/
  workspace/
    controller/
    service/
    dto/
    viewmodel/
  bulk/
    controller/
    service/
    dto/
  ingestion/
    controller/
    service/
    repository/
    dto/
    model/
    viewmodel/
  export/
    controller/
    service/
    dto/
  history/         # future undo/redo module
    controller/
    service/
    repository/
    dto/
    model/
  admin/           # future admin module
    controller/
    service/
    dto/
    viewmodel/
  shared/
    config/
    security/
    web/
    util/
```

## Shared Code Rules

Reuse is encouraged, but `shared` must stay narrow.

Default placement rule:

- keep code in the feature that currently owns the behavior

Promote code to `shared` only when all of the following are true:

- it is used by more than one feature
- it is not specific to one product workflow
- the abstraction is already clear, not speculative

Do not put the following into `shared`:

- feature-specific services
- feature-specific DTOs
- workspace-only helpers
- ingestion-only parsing rules
- upload-review view logic
- bulk-edit field mutation logic

If several features depend on the same domain concept and that concept grows large, create a dedicated domain feature package instead of bloating `shared`. For this codebase, if `TestCase`, `TestStep`, and `TestCaseRepository` continue to expand across multiple workflows, prefer a dedicated `testcase` or `history` domain module over enlarging `shared.domain`.

## Auth And Team Scope Rules

The current application repeatedly checks session state and team membership inside multiple controllers. That duplication should be reduced.

Rules:

- `CurrentUserService` or a dedicated request-context abstraction should be the single place that resolves the current user and team context.
- Controllers should call that abstraction rather than repeat the same optional-user and missing-team branches.
- Team ownership rules belong in services, not scattered controller branches.

## Transaction Rules

The following workflows must remain transactional:

- bulk edit
- bulk move
- direct import save
- upload review apply/cancel
- future undo/redo operations

If an operation partially fails, the safe default is rollback.

## Frontend Architecture Rules

The frontend is currently the weakest architectural area and should be modularized before more major workspace features are added.

### Current Pain Points

- flat `templates/` directory
- inline scripts inside templates
- template-local styling and repeated head markup
- large workspace page script
- upload review behavior embedded in the template

### Required Folder Creation

Before expanding the UI surface further, organize templates and static assets by feature.

Recommended template structure:

```text
src/main/resources/templates/
  layout/
  auth/
  workspace/
  ingestion/
  testcases/
  admin/
```

Recommended static asset structure:

```text
src/main/resources/static/
  js/
    shared/
    auth/
    workspace/
      api/
      components/
      features/
      page/
      state/
    ingestion/
    testcases/
  css/
```

### Frontend Module Rules

- Each Thymeleaf page should have one small page boot module.
- Page boot modules compose smaller components and feature modules.
- DOM rendering should live in components, not in giant page entry files.
- Network calls should live in small API modules.
- CSRF, fetch wrappers, flash messages, clipboard helpers, and generic DOM utilities can live in `static/js/shared`.
- Do not create a catch-all shared frontend bucket for feature logic.
- New behavior should not be embedded inline in templates unless it is tiny and truly page-local.

### Immediate Frontend Refactor Targets

- split the workspace page script into page boot, folder tree, filters, pagination, selection, organize modal, and notices
- move upload review script out of the template and into `static/js/ingestion`
- introduce shared Thymeleaf layout fragments for head, flash messages, and common nav elements

## Deployment And Infrastructure Rules

- Assume the app runs behind a proxy on Render.
- Keep `server.forward-headers-strategy=framework`.
- Treat PostgreSQL as the system of record.
- Keep application behavior stateless across instances.
- Never rely on writable local disk for durable upload or history state.
- Docker must remain the supported build/runtime path.
- Environment-driven configuration should remain the deployment default.

## Persistence Guidance

Current repo state uses `spring.jpa.hibernate.ddl-auto=update` and application-driven schema adjustment. That is acceptable for rapid iteration, but it is not the long-term target.

Direction:

- move toward explicit migrations for production safety
- avoid hidden schema side effects at startup
- treat Render/Postgres changes as deployment-sensitive work that requires user review

## Architecture Smells To Reject

Reject changes that introduce any of the following:

- controllers that directly own business logic or repository orchestration
- new feature logic added to `shared` without clear cross-feature justification
- new inline scripts in already-large templates
- giant page entrypoints that combine fetch, state, rendering, and modal logic
- API endpoints returning raw entities when DTOs or view models are more appropriate
- refactors that improve reuse by centralizing unrelated behavior into one generic utility file

