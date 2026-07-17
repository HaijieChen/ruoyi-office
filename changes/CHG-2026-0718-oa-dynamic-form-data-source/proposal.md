# CHG-2026-0718-oa-dynamic-form-data-source — OA dynamic form data source and workflows

- Mode: `strict`
- Created: 2026-07-18
- Status: approved-for-implementation

## Current behavior

The dynamic-form designer can persist form-create schemas, but reusable server-managed data sources and deterministic component linkages are not available. OA forms therefore cannot safely query current-platform data through administrator-configured SQL. The required OA workflow set is not yet configured from the 318 workbook.

## Desired behavior

Administrators manage immutable, versioned SQL/dictionary/platform-API data sources. Runtime forms execute only published sources by code, bind safe server/form context, and link components without browser-side SQL or JavaScript evaluation. The platform contains the 14 entry forms, eight simple-process drafts, and seven BPMN drafts described by the approved plan.

## Goals

- Secure read-only queries against only the current platform database.
- Reusable source management, version publication, audit logs, and runtime execution.
- Safe form bindings, dependency ordering, mapped outputs, stale-request protection, and history snapshots.
- Configure the approved OA forms and workflow drafts while leaving approvers unset.

## Non-goals

- No external database connectivity or arbitrary URL execution.
- No BPMN/Flowable engine modification.
- No hard-coded approver identities.
- No replacement of existing historical form/process records.

## Acceptance criteria

- [ ] SQL accepts exactly one parameterized SELECT or WITH SELECT and rejects mutation, comments, multi-statements, dangerous functions, and reserved-parameter overrides.
- [ ] Published versions are immutable; disabled or unpublished sources cannot execute.
- [ ] Query execution is read-only, tenant isolated, capped at 200 rows by default, timed out after 3 seconds by default, audited, and does not expose SQL.
- [ ] Frontend bindings are path-only and never use eval, new Function, browser-provided URLs, or parser functions.
- [ ] Backend and frontend focused tests, builds, migration/rollback checks, and security cases pass.
- [ ] Fourteen forms, eight simple-process drafts, and seven BPMN drafts are recorded with identifiers and approval candidates left for the administrator.
- [ ] Existing running instances, historical values, and old seal form remain readable.

## Open questions

None blocking. The obsolete frontend path in the source plan is normalized to the unified `ruoyi-office-vben` directory in this repository and branch.
