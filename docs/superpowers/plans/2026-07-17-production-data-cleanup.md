# Production Data Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the locally deployed demo database into a clean production initialization baseline while preserving required platform metadata and a usable administrator account.

**Architecture:** Take and verify a full logical backup first. Apply an auditable SQL cleanup transaction boundary with foreign-key checks disabled only for controlled truncation, preserve system menus/dictionaries/platform configuration/dashboard definitions/workflow definitions, reduce identity data to the default tenant plus administrator, and clear business, runtime, logging, token, notification, file, demo-module, and integration credential data. Restart Redis/backend to remove cached sessions and verify a fresh login plus representative pages.

**Tech Stack:** MySQL 8, Redis, Spring Boot, Docker, Vue/Vite.

## Global Constraints

- Never drop tables or databases.
- Preserve `system_menu`, dictionaries, core `infra_config`, dashboard definitions, and Flowable/BPM definition tables.
- Preserve only tenant `1`, user `1` (`admin`), role `1` (`super_admin`), and one root department.
- Remove all access tokens, refresh tokens, login sessions, business records, logs, notices, schedules, uploaded files, and demo integration credentials; preserve the platform's required `default` OAuth2 login client.
- Produce a restorable pre-cleanup backup and SHA-256 checksum before mutation.
- Do not modify the user's existing main-branch source changes.

---

### Task 1: Backup and Inventory

**Files:**
- Create: `/private/tmp/ruoyi-office-pre-production-cleanup-20260717.sql.gz`
- Create: `/private/tmp/ruoyi-office-pre-production-cleanup-20260717.sql.gz.sha256`

- [ ] Export the complete `ruoyi-office` database with routines, triggers, and events.
- [ ] Verify gzip integrity and record a SHA-256 checksum.
- [ ] Capture pre-cleanup counts for users, tenants, business data, tokens, logs, and files.

### Task 2: Reproducible Cleanup SQL

**Files:**
- Create: `sql/mysql/production-data-cleanup.sql`

- [ ] Add guarded cleanup SQL that verifies the target database and required administrator.
- [ ] Truncate runtime/history/log/token/message/file/business/demo-module tables without dropping schema.
- [ ] Reduce tenant, user, role, department, post, and relationship data to the production baseline.
- [ ] Neutralize demo names for the retained administrator, tenant, and root organization.
- [ ] Add postconditions that fail when non-baseline identity or runtime data remains.

### Task 3: Apply Cleanup and Clear Runtime Cache

**Files:**
- Use: `sql/mysql/production-data-cleanup.sql`

- [ ] Copy and execute the SQL against the dedicated `ruoyi-office` database.
- [ ] Flush the dedicated Redis database and restart the backend to invalidate cached menus and sessions.
- [ ] Confirm all three containers remain running and the backend health endpoint is `UP`.

### Task 4: Production Baseline Verification

**Files:**
- Verify: `sql/mysql/production-data-cleanup.sql`

- [ ] Verify exactly one tenant, user, role, department, and user-role relation remain.
- [ ] Verify transactional, runtime, token, log, notification, schedule, file, and disabled-demo module data counts are zero.
- [ ] Verify menus, dictionaries, configuration, dashboard definitions, and workflow definitions remain populated.
- [ ] Verify a fresh `admin` login and representative system/OA/BPM/HRM/asset/WMS pages render without backend errors.
- [ ] Report the backup path and mandatory remaining production action: change the default administrator password.
