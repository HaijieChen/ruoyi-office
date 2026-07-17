# Impact analysis — CHG-2026-0718-oa-dynamic-form-data-source

## Affected capabilities and users

Workflow administrators gain source management and designer components. Form users consume linked choices. Operations manage a read-only database account and environment-only credentials. Existing process users retain compatible history.

## Modules and expected file sets

- Backend: BPM API/server definition, datasource framework, controllers, services, mappers, tests, and local configuration.
- Frontend: `ruoyi-office-vben/apps/web-antd` BPM API/views and form-create components/plugins/tests.
- Data/docs: MySQL migration, OA configuration records, workbook-derived form and workflow configuration.

## APIs and downstream consumers

New management endpoints cover create/update/page/get/simple-list/draft/trial/publish/disable. Runtime executes a published source by code. The generic form selector is the only browser consumer and never receives SQL or credentials.

## Data and migrations

Add three tenant-aware BPM tables and idempotent menu permissions. Before applying, back up BPM, Flowable, form, and menu tables. Create a SELECT-only MySQL account whose password is not committed.

## Security, privacy, and permissions

Strict risk: administrator SQL, authorization, tenant isolation, sensitive result masking, SSRF/path traversal, resource limits, audit redaction, and runtime form access must be tested explicitly.

## Operations and observability

Log source/version, context identifiers, digest, count, duration, success, and normalized error code without raw parameter values. Roll back on migration failure, cross-tenant results, write capability, timeout/limit bypass, or elevated error rate.

## Compatibility and documentation

Published versions are immutable and form history retains label/value snapshots. Old forms and process instances remain readable. The acceptance record captures IDs, versions, screenshots, checksums, limitations, and rollback.

## Parallelization candidates

| Stream | Deliverable | Owned paths | Dependencies | Independent tests |
|---|---|---|---|---|
| backend-persistence | Task 1 schema/DO/mappers | `sql/mysql/bpm_form_data_source.sql`, BPM definition DO/mapper and mapper tests | baseline only | `BpmFormDataSourceMapperTest` |
| backend-sql-safety | Task 2 SQL validator/context | BPM datasource framework, BPM error constants and focused tests | baseline only | SQL validator/context resolver tests |
| frontend-linkage | Task 7 safe expression/DAG | `ruoyi-office-vben/.../form-create/data-source` | baseline only | expression/linkage Vitest |
| backend-domain | Tasks 3-5 execution/API/form validation | BPM services/controllers/config | Task 1-2 integrated | focused BPM tests |
| frontend-ui | Tasks 6 and 8 management/selector | BPM views/API and form-create component/plugin | backend API contract and Task 7 | focused Vitest + typecheck |
