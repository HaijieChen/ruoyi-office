# Correct RuoYi Office Local Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mismatched generic frontend and partial backend deployment with the matching RuoYi Office open-source frontend, backend branch, database, and enabled menu set.

**Architecture:** Preserve the current checkout and deploy from isolated runtime directories. Build the `master-jdk17-开源` backend and the custom `ruoyi-office-vben` frontend, reinitialize only the dedicated local MySQL database, then expose the replacement services on the existing local ports.

**Tech Stack:** Git worktree, JDK 17, Maven 3.9, MySQL 8, Redis 7, Node.js, pnpm, Vue 3, Vben Admin, Docker.

## Global Constraints

- Preserve `/Users/chenhaijie/workspace/3dm/royi-oa` and its uncommitted changes.
- Only reset the dedicated local `ruoyi-office` database used by `ruoyi-office-mysql`.
- Keep the user-facing frontend URL at `http://127.0.0.1:5666/`.
- Keep the backend URL at `http://127.0.0.1:48080/`.
- Do not report completion until login, health, menus, and representative OA, HRM, and BPM routes are freshly verified.

---

### Task 1: Isolated matching source trees

**Files:**
- Create: `/private/tmp/ruoyi-office-master-jdk17-open`
- Create: `/private/tmp/ruoyi-office-vben-custom`

**Interfaces:**
- Consumes: public branches from `https://github.com/yuqing2026/ruoyi-office.git` and `https://github.com/yuqing2026/ruoyi-office-vben.git`
- Produces: isolated backend and frontend source trees at known commits

- [ ] **Step 1: Add the backend upstream remote without changing the current branch**

Run: `git remote add yuqing https://github.com/yuqing2026/ruoyi-office.git`

Expected: remote `yuqing` exists and the current branch remains `master-jdk17`.

- [ ] **Step 2: Fetch and create the isolated backend worktree**

Run: `git fetch yuqing master-jdk17-开源 && git worktree add /private/tmp/ruoyi-office-master-jdk17-open yuqing/master-jdk17-开源`

Expected: the worktree is detached at the fetched open-source branch commit.

- [ ] **Step 3: Clone the matching custom frontend**

Run: `git clone --depth 1 --branch master https://github.com/yuqing2026/ruoyi-office-vben.git /private/tmp/ruoyi-office-vben-custom`

Expected: the frontend origin is `yuqing2026/ruoyi-office-vben` on `master`.

### Task 2: Build matching backend and frontend

**Files:**
- Build: `/private/tmp/ruoyi-office-master-jdk17-open/yudao-server/target/yudao-server.jar`
- Install: `/private/tmp/ruoyi-office-vben-custom/node_modules`
- Modify only if required for local endpoints: `/private/tmp/ruoyi-office-vben-custom/apps/web-antd/.env.development`

**Interfaces:**
- Consumes: isolated source trees and local MySQL/Redis ports
- Produces: a runnable backend JAR and frontend development server dependencies

- [ ] **Step 1: Inspect branch-specific README, POM, SQL, and environment files**

Run: `rg -n "48080|3306|6379|VITE_BASE_URL|module-oa|module-hrm|module-bpm" README.md yudao-server/pom.xml yudao-server/src/main/resources apps/web-antd/.env*`

Expected: exact backend modules, database scripts, and local API URL are known before building.

- [ ] **Step 2: Build the backend server and its required modules**

Run: `mvn -pl yudao-server -am clean package -DskipTests`

Expected: Maven exits `0` and creates `yudao-server/target/yudao-server.jar`.

- [ ] **Step 3: Install frontend dependencies**

Run: `pnpm install --frozen-lockfile`

Expected: pnpm exits `0` without changing the lockfile.

### Task 3: Replace the dedicated local runtime

**Files:**
- Import: `/private/tmp/ruoyi-office-master-jdk17-open/sql/mysql/ruoyi-vue-pro.sql`
- Runtime: Docker containers `ruoyi-office-mysql`, `ruoyi-office-redis`, and `ruoyi-office-backend`

**Interfaces:**
- Consumes: matching SQL and built JAR
- Produces: a fresh local database and backend on port `48080`

- [ ] **Step 1: Stop only the old frontend and backend processes**

Expected: MySQL and Redis stay available until the database import begins.

- [ ] **Step 2: Recreate only the `ruoyi-office` schema**

Run: `DROP DATABASE IF EXISTS \`ruoyi-office\`; CREATE DATABASE \`ruoyi-office\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` followed by importing the branch SQL.

Expected: the import exits `0`, and all required tables and columns exist.

- [ ] **Step 3: Recreate the backend container with the matching JAR**

Expected: `/actuator/health` returns `{"status":"UP"}` on port `48080`.

- [ ] **Step 4: Start the custom frontend on port 5666**

Run: `pnpm dev:antd --host 127.0.0.1 --port 5666`

Expected: the custom RuoYi Office login page responds at `http://127.0.0.1:5666/`.

### Task 4: End-to-end verification

**Files:**
- Verify only; no planned source modifications

**Interfaces:**
- Consumes: running matching frontend, backend, MySQL, and Redis
- Produces: evidence that the corrected deployment matches the available open-source modules

- [ ] **Step 1: Verify login with the documented administrator account**

Expected: `admin` / `admin123` reaches the customized home page without a system error.

- [ ] **Step 2: Verify menus map to frontend components**

Expected: every visible component route has a corresponding file in the custom frontend; disabled backend modules are not exposed as usable menus.

- [ ] **Step 3: Verify representative business pages**

Expected: one OA page, one HRM page, and one BPM page render and their initial API calls return successful responses.

- [ ] **Step 4: Verify fresh logs**

Expected: no new `ERROR`, `Unknown column`, missing-table, or missing-endpoint messages appear during the verification flow.
