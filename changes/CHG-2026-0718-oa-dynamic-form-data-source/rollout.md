# Rollout — CHG-2026-0718-oa-dynamic-form-data-source

## Deployment order

1. Build/test code and back up affected tables.
2. Create SELECT-only account and inject environment-only credentials.
3. Apply idempotent schema/menu migration.
4. Restart backend, then frontend; verify health and security probes.
5. Publish reusable sources, configure forms, then save workflow drafts.

## Feature flags and compatibility window

New sources/components are additive and unused until a form references a published code. Keep old entries and forms visible through acceptance; disable new sources for immediate runtime containment.

## Observability

Monitor health, datasource pool acquisition, query duration/timeouts/row counts, normalized failure codes, API error rate, tenant/source/version audit dimensions, and browser component retry failures.

## Rollback triggers

Any write capability, cross-tenant result, SQL/secret exposure, migration inconsistency, sustained datasource errors, timeout/limit bypass, or historical rendering regression.

## Rollback and recovery procedure

Disable published sources, restore prior application artifact, restore backed-up tables if schema/data rollback is required, remove the read-only credential from runtime, and verify old forms/processes before reopening traffic. Never drop or rewrite historical form/process data during ordinary rollback.
