# Architecture — CHG-2026-0718-oa-dynamic-form-data-source

## Existing constraints

- Spring Boot/MyBatis-Plus multi-tenant backend and Vue 3/form-create frontend are in one Git repository.
- Current-platform MySQL and existing authorization/tenant context remain authoritative.
- Existing BPMN and simple-process designers are reused unchanged.

## Decision

Store data-source definitions, immutable versions, and execution logs in BPM. Validate SQL with an AST plus explicit lexical guards, resolve reserved context server-side, and execute SQL through a separately configured read-only pool. Dispatch DICT and PLATFORM_API sources through typed providers. Expose only source codes and mapped rows to forms. Implement linkage as a deterministic DAG and path-only binding resolver in the frontend.

## Alternatives considered

- Browser-executed URLs/SQL: rejected for credential, injection, and tenant-isolation risk.
- External database connections: rejected because scope is the current platform database only.
- Hard-coded form-specific components: rejected in favor of a reusable selector and linkage engine.
- BPMN engine changes: rejected because existing modelers already own process behavior.

## Consequences and risks

Positive: centralized security, reuse, immutable publication, auditable execution. Risks: schema migration, read-only credential lifecycle, query load, platform-API SSRF, stale UI responses, and historical compatibility. Controls are captured in the test and rollout plans.

## ADR requirement

This architecture file is the decision record for the change. A separate ADR is unnecessary unless implementation changes the single-database or server-only execution boundary.

## Integration order

1. Schema and persistence contracts.
2. SQL/context validation.
3. Read-only execution and lifecycle APIs.
4. Form-save validation.
5. Frontend management and linkage UI.
6. Migration, reusable sources, forms, and workflow drafts.
7. Integrated regression and handoff.
