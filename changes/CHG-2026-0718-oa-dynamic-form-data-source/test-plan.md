# Test plan — CHG-2026-0718-oa-dynamic-form-data-source

## Direct acceptance tests

Run every plan-specified backend and frontend focused test, then execute source lifecycle, runtime query, selector linkage, save/reopen, and workflow-model validation cases.

## Unit tests

- Mapper persistence and published-version selection.
- SQL AST/lexical rejection and reserved-context resolution.
- Parameter/schema/result validation, masking, cache isolation, limits, and failure audit.
- Lifecycle immutability and form linkage DAG validation.
- Safe bindings, dependency order/actions, payload normalization, and selector stale-response behavior.

## Integration and contract tests

Verify management/runtime API shapes, error codes, SQL omission, read-only account behavior, DICT provider, allow-listed platform API provider, and tenant/user-scoped cache keys.

## Adjacent regression

Existing BPM form CRUD, model designers, history rendering, old seal form, auth/tenant headers, dictionaries, and current OA pages.

## Core smoke paths

Login, open source management, create/trial/publish/execute a source, design/save/preview a linked form, reopen historical form data, and validate simple/BPMN drafts.

## Security and permission isolation

Injection corpus, comments/multi-statements, dangerous functions, reserved overrides, encoded traversal, redirects, cross-tenant/user cache isolation, missing form access, secret/SQL/stack-trace omission, timeout and row-limit enforcement.

## Migration, rollback, idempotency, concurrency, and recovery

Apply migration twice, verify backup checksum and restore procedure, publish concurrent-version protection, immutable published rows, disabled-source failure, failure audit without raw data, and service restart using environment-only credentials.

## Evidence matrix

| Requirement or risk | Test level | Command or case | Result |
|---|---|---|---|
| Current baseline | build/test | BPM compile/tests and frontend form-create Vitest/typecheck | pending |
| SQL safety | unit/security | `BpmFormDataSourceSqlValidatorTest` | pending |
| Tenant/read-only/limits | integration/security | execution service tests plus SELECT-only DB smoke | pending |
| Linkage determinism | unit/component | form-create data-source Vitest | pending |
| Historical compatibility | regression/E2E | reopen old process/form values | pending |
| Migration rollback | operations | backup, double-apply, restore rehearsal | pending |
