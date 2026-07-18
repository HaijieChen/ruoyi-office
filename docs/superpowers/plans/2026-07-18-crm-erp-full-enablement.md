# CRM、ERP Full Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the complete native CRM and ERP modules in the local platform, expose their existing menus without recreating data, and leave a repeatable JDK 17 build/start procedure.

**Architecture:** Add the existing CRM and ERP server modules to the monolithic `yudao-server` dependency graph, build the fat JAR in a pinned JDK 17 Maven container, then restart the bind-mounted backend with a safely rotated and injected read-only form-data-source credential. Enable only the two root menus after the backend is healthy; keep CRM, ERP, and WMS data models independent.

**Tech Stack:** Java 17, Maven 3.9, Spring Boot, MySQL 8, Docker Desktop, Vue 3/Vite, pnpm, shell scripts.

## Global Constraints

- Use Docker image `maven:3.9-eclipse-temurin-17`; the host currently has only JDK 21 and JDK 8.
- Do not initialize, truncate, migrate, or overwrite the existing 19 CRM tables or 33 ERP tables.
- Do not merge CRM customers, ERP customers, ERP suppliers, or WMS supplier fields.
- Enable only root menu IDs `2397` (CRM) and `2563` (ERP); preserve all child menu and role grants.
- Do not grant CRM or ERP permissions to ordinary roles automatically.
- Never print the `ruoyi_form_reader` password; rotate and pass it as opaque temporary data.
- Preserve the unrelated modification in `ruoyi-office-vben/apps/web-antd/.env.development` and never include it in CRM/ERP commits.
- If the backend fails to start, keep both root menus disabled and restore the prior JAR.

---

## File Structure

- Modify `yudao-server/pom.xml`: include the existing CRM and ERP server modules in the monolith.
- Create `scripts/local/build-backend-jdk17.sh`: reproducibly build and verify the fat JAR with JDK 17.
- Create `scripts/local/restart-backend-with-reader-secret.sh`: rotate the dedicated read-only account, restart the backend, inject the credential, and wait for health.
- Create `docs/deployment/crm-erp-local-runbook.md`: record module JARs, JDK choice, build/start commands, menu enablement, verification, and rollback.

### Task 1: Enable CRM and ERP server dependencies

**Files:**
- Modify: `yudao-server/pom.xml:114-128`

**Interfaces:**
- Consumes: existing Maven modules `yudao-module-crm-server` and `yudao-module-erp-server`.
- Produces: both modules as runtime dependencies of `yudao-server`.

- [ ] **Step 1: Run the failing dependency assertion**

```bash
docker run --rm \
  -v /Users/chenhaijie/workspace/3dm/royi-oa:/workspace \
  -v /Users/chenhaijie/.m2:/root/.m2 \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 \
  mvn -pl yudao-server dependency:tree \
    -Dincludes=cn.iocoder.cloud:yudao-module-crm-server,cn.iocoder.cloud:yudao-module-erp-server \
  | tee /private/tmp/ruoyi-crm-erp-dependencies-before.txt

test "$(grep -Ec 'yudao-module-(crm|erp)-server:jar' /private/tmp/ruoyi-crm-erp-dependencies-before.txt)" -eq 2
```

Expected: the final assertion fails because neither server module is currently a `yudao-server` dependency.

- [ ] **Step 2: Add the two existing dependencies**

Replace the commented CRM and ERP dependency blocks with:

```xml
        <!-- CRM 相关模块 -->
        <dependency>
            <groupId>cn.iocoder.cloud</groupId>
            <artifactId>yudao-module-crm-server</artifactId>
            <version>${revision}</version>
        </dependency>

        <!-- ERP 相关模块 -->
        <dependency>
            <groupId>cn.iocoder.cloud</groupId>
            <artifactId>yudao-module-erp-server</artifactId>
            <version>${revision}</version>
        </dependency>
```

- [ ] **Step 3: Run the dependency assertion again**

Run the Step 1 commands again, writing to `/private/tmp/ruoyi-crm-erp-dependencies-after.txt`.

Expected: the assertion passes and the dependency tree contains exactly one CRM and one ERP server dependency.

- [ ] **Step 4: Check the POM change**

```bash
git diff --check -- yudao-server/pom.xml
git diff -- yudao-server/pom.xml
```

Expected: only the CRM and ERP dependency blocks change.

- [ ] **Step 5: Commit the dependency change**

```bash
git add yudao-server/pom.xml
git commit -m "feat(server): enable CRM and ERP modules"
```

Expected: the unrelated frontend environment file remains unstaged.

### Task 2: Add a pinned JDK 17 backend build script

**Files:**
- Create: `scripts/local/build-backend-jdk17.sh`

**Interfaces:**
- Consumes: repository root, host Maven cache, Docker image `maven:3.9-eclipse-temurin-17`.
- Produces: `yudao-server/target/yudao-server.jar` containing both module JARs.

- [ ] **Step 1: Run the failing script-existence check**

```bash
test -x scripts/local/build-backend-jdk17.sh
```

Expected: FAIL because the script does not exist.

- [ ] **Step 2: Create the build script**

```sh
#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "${script_dir}/../.." && pwd)
maven_image='maven:3.9-eclipse-temurin-17'
maven_cache='/Users/chenhaijie/.m2'
jar_path="${repo_dir}/yudao-server/target/yudao-server.jar"

docker image inspect "${maven_image}" >/dev/null

docker run --rm \
  -v "${repo_dir}:/workspace" \
  -v "${maven_cache}:/root/.m2" \
  -w /workspace \
  "${maven_image}" \
  mvn -pl yudao-server -am clean package -DskipTests

test -s "${jar_path}"
jar tf "${jar_path}" | grep -q 'BOOT-INF/lib/yudao-module-crm-server-.*\.jar'
jar tf "${jar_path}" | grep -q 'BOOT-INF/lib/yudao-module-erp-server-.*\.jar'

stat -f 'built jar: %z bytes %N' "${jar_path}"
```

- [ ] **Step 3: Validate the script before execution**

```bash
chmod +x scripts/local/build-backend-jdk17.sh
sh -n scripts/local/build-backend-jdk17.sh
test -x scripts/local/build-backend-jdk17.sh
grep -q 'maven:3.9-eclipse-temurin-17' scripts/local/build-backend-jdk17.sh
```

Expected: all checks pass.

### Task 3: Add a repeatable backend restart script

**Files:**
- Create: `scripts/local/restart-backend-with-reader-secret.sh`

**Interfaces:**
- Consumes: Docker containers `ruoyi-office-mysql`, `ruoyi-office-redis`, `ruoyi-office-backend` and MySQL root password held inside the MySQL container environment.
- Produces: a healthy backend at `http://127.0.0.1:48080`, with a freshly rotated read-only credential injected at `/run/secrets/bpm.form-data-source.jdbc.password`.

- [ ] **Step 1: Run the failing script-existence check**

```bash
test -x scripts/local/restart-backend-with-reader-secret.sh
```

Expected: FAIL because the script does not exist.

- [ ] **Step 2: Create the restart script**

```sh
#!/bin/sh
set -eu

mysql_container='ruoyi-office-mysql'
redis_container='ruoyi-office-redis'
backend_container='ruoyi-office-backend'
secret_path='/run/secrets/bpm.form-data-source.jdbc.password'
temporary_dir=$(mktemp -d /private/tmp/ruoyi-office-secret.XXXXXX)
temporary_secret="${temporary_dir}/bpm.form-data-source.jdbc.password"

cleanup() {
  if [ -f "${temporary_secret}" ]; then
    chmod 600 "${temporary_secret}" || true
    rm -f "${temporary_secret}"
  fi
  rmdir "${temporary_dir}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

chmod 700 "${temporary_dir}"
umask 077
openssl rand -hex 32 >"${temporary_secret}"

docker start "${mysql_container}" "${redis_container}" >/dev/null

new_password=$(tr -d '\n' <"${temporary_secret}")
printf "ALTER USER 'ruoyi_form_reader'@'%%' IDENTIFIED BY '%s'; FLUSH PRIVILEGES;\n" "${new_password}" \
  | docker exec -i "${mysql_container}" sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' >/dev/null
unset new_password

docker restart "${backend_container}" >/dev/null
docker exec -i "${backend_container}" sh -lc \
  'umask 077; cat > /run/secrets/bpm.form-data-source.jdbc.password' \
  <"${temporary_secret}"
docker exec "${backend_container}" chmod 400 "${secret_path}"

attempt=0
while [ "${attempt}" -lt 90 ]; do
  if curl -fsS http://127.0.0.1:48080/actuator/health >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 1
done

if [ "${attempt}" -ge 90 ]; then
  echo 'backend health check timed out' >&2
  exit 1
fi

docker exec "${backend_container}" stat -c 'runtime jar: %s bytes %n' /app.jar
docker exec "${backend_container}" sh -lc \
  'if ls -l /proc/1/fd 2>/dev/null | grep -q "app.jar (deleted)"; then exit 1; fi'
curl -fsS http://127.0.0.1:48080/actuator/health
```

- [ ] **Step 3: Validate the script before execution**

```bash
chmod +x scripts/local/restart-backend-with-reader-secret.sh
sh -n scripts/local/restart-backend-with-reader-secret.sh
test -x scripts/local/restart-backend-with-reader-secret.sh
if grep -Eq 'echo .*password|cat .*secret_path' scripts/local/restart-backend-with-reader-secret.sh; then exit 1; fi
```

Expected: syntax and safety checks pass.

- [ ] **Step 4: Commit both operational scripts**

```bash
git add scripts/local/build-backend-jdk17.sh scripts/local/restart-backend-with-reader-secret.sh
git commit -m "chore(local): add reproducible backend lifecycle scripts"
```

Expected: only the two scripts are committed.

### Task 4: Build and deploy the complete backend

**Files:**
- Runtime artifact: `yudao-server/target/yudao-server.jar`
- Temporary rollback artifact: `/private/tmp/ruoyi-office-backend-before-crm-erp.jar`

**Interfaces:**
- Consumes: the scripts produced in Tasks 2 and 3.
- Produces: a healthy backend running the new CRM/ERP fat JAR.

- [ ] **Step 1: Capture the rollback JAR and baseline**

```bash
cp yudao-server/target/yudao-server.jar /private/tmp/ruoyi-office-backend-before-crm-erp.jar
docker exec ruoyi-office-backend stat -c 'before: %y %s %n' /app.jar
curl -fsS http://127.0.0.1:48080/actuator/health
```

Expected: the backup exists and baseline health is `UP`.

- [ ] **Step 2: Run module tests in JDK 17**

```bash
docker run --rm \
  -v /Users/chenhaijie/workspace/3dm/royi-oa:/workspace \
  -v /Users/chenhaijie/.m2:/root/.m2 \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 \
  mvn -pl yudao-module-crm/yudao-module-crm-server,yudao-module-erp/yudao-module-erp-server -am test
```

Expected: Maven exits 0. If a test is blocked by infrastructure before its test body, record it separately and do not describe it as a feature failure.

- [ ] **Step 3: Build and verify the fat JAR**

```bash
scripts/local/build-backend-jdk17.sh
```

Expected: exit 0, non-empty JAR, and both module JAR assertions pass.

- [ ] **Step 4: Restart and verify the backend**

```bash
scripts/local/restart-backend-with-reader-secret.sh
```

Expected: health is `UP`, the runtime JAR matches the newly built file size, and no `/proc/1/fd` entry references `app.jar (deleted)`.

- [ ] **Step 5: Verify module registration**

```bash
jar tf yudao-server/target/yudao-server.jar \
  | grep -E 'BOOT-INF/lib/yudao-module-(crm|erp)-server-.*\.jar'
docker logs --since 5m ruoyi-office-backend 2>&1 \
  | grep 'Started YudaoServerApplication'
if docker logs --since 5m ruoyi-office-backend 2>&1 \
  | grep -E 'ERROR|ClassNotFoundException|NoClassDefFoundError|TypeNotPresentException'; then
  echo 'new backend error found' >&2
  exit 1
fi
```

Expected: both module JAR names are printed, the application start marker exists, and there are no new unhandled startup errors.

### Task 5: Enable the two root menus after backend health

**Files:**
- Runtime data only: `system_menu` rows `2397` and `2563`.

**Interfaces:**
- Consumes: healthy backend from Task 4.
- Produces: CRM and ERP dynamic routes for the super administrator.

- [ ] **Step 1: Verify the precondition**

```bash
curl -fsS http://127.0.0.1:48080/actuator/health
docker exec ruoyi-office-mysql sh -lc \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-office -Nse "SELECT id,name,status FROM system_menu WHERE id IN (2397,2563) ORDER BY id;"'
```

Expected: health is `UP` and both menu statuses are `1` before enablement.

- [ ] **Step 2: Enable the root menus in one transaction**

```bash
docker exec ruoyi-office-mysql sh -lc \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-office -e "START TRANSACTION; UPDATE system_menu SET status=0, updater=\"1\", update_time=NOW() WHERE id IN (2397,2563) AND deleted=0; SELECT id,name,status FROM system_menu WHERE id IN (2397,2563) ORDER BY id; COMMIT;"'
```

Expected: exactly two rows show status `0`.

- [ ] **Step 3: Refresh the administrator session**

Reload the local frontend and sign in again as `admin` if the existing permission cache does not refresh automatically. Do not inspect or print browser tokens, cookies, or local storage.

- [ ] **Step 4: Verify menu visibility**

Expected: top-level `CRM 系统` and `ERP 系统` menus appear for `admin`; ordinary role grants remain unchanged.

### Task 6: Run full CRM/ERP and regression acceptance

**Files:**
- No source changes.

**Interfaces:**
- Consumes: visible dynamic routes from Task 5.
- Produces: evidence that all 42 CRM/ERP pages and representative existing modules are usable.

- [ ] **Step 1: Record the acceptance start time**

```bash
date -u '+%Y-%m-%dT%H:%M:%SZ' \
  | tee /private/tmp/ruoyi-crm-erp-acceptance-start.txt
```

- [ ] **Step 2: Derive the authoritative route set from the database**

```bash
docker exec ruoyi-office-mysql sh -lc \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-office -Nse "WITH RECURSIVE mt AS (SELECT id,parent_id,path,component,type,status,path AS full_path FROM system_menu WHERE id IN (2397,2563) UNION ALL SELECT m.id,m.parent_id,m.path,m.component,m.type,m.status,CONCAT(TRIM(TRAILING \"/\" FROM mt.full_path),\"/\",TRIM(LEADING \"/\" FROM m.path)) FROM system_menu m JOIN mt ON m.parent_id=mt.id) SELECT full_path FROM mt WHERE type=2 AND status=0 ORDER BY full_path;"'
```

Expected: 42 page routes.

- [ ] **Step 3: Visit all 42 routes in the authenticated local browser**

For each database-derived route, navigate under `http://127.0.0.1:5666`, wait for the page to load, and assert that the page does not contain `系统异常`, `系统错误`, `加载失败`, `Internal Server Error`, or `404 Not Found`.

Expected: 42 checked, 0 failed.

- [ ] **Step 4: Regress representative existing pages**

Visit:

```text
/workspace/home
/bpm/start-process
/infra/codegen
/asset/category
/wms/warehousing
```

Expected: each page loads without the failure strings from Step 3.

- [ ] **Step 5: Check backend logs from the acceptance start time**

```bash
if docker logs --since "$(cat /private/tmp/ruoyi-crm-erp-acceptance-start.txt)" ruoyi-office-backend 2>&1 \
  | grep -E 'ERROR|ClassNotFoundException|NoClassDefFoundError|TypeNotPresentException|Table .* doesn.t exist|No static resource|NoResourceFoundException'; then
  echo 'acceptance log contains an error' >&2
  exit 1
fi
```

Expected: no matching lines. The exact timestamp captured in Step 1 is used; no approximate time window is allowed.

### Task 7: Record the reusable local operating procedure

**Files:**
- Create: `docs/deployment/crm-erp-local-runbook.md`

**Interfaces:**
- Consumes: actual successful commands and observations from Tasks 1-6.
- Produces: a copy-pasteable runbook for future builds and starts.

- [ ] **Step 1: Write the runbook with verified facts**

The document must include:

```markdown
# CRM、ERP 本地构建与启动手册

## 固定模块依赖
- yudao-module-crm-server
- yudao-module-erp-server

## JDK 与 Maven
- 宿主机 Maven 路径和当前 Java 版本
- 为什么不能使用 `/usr/libexec/java_home -v 17`
- 固定镜像 `maven:3.9-eclipse-temurin-17`
- `scripts/local/build-backend-jdk17.sh` 使用方式

## 后端启动
- MySQL、Redis、后端容器名称
- 只读账号的权限边界
- `scripts/local/restart-backend-with-reader-secret.sh` 使用方式
- 健康检查、JAR 大小和 deleted-inode 检查

## 前端启动
- 工作目录 `ruoyi-office-vben`
- pnpm/Vite 完整启动命令
- 端口 `5666`

## 菜单
- CRM ID 2397
- ERP ID 2563
- 查询、启用和停用 SQL

## 访问与验收
- 前端、后端、健康检查地址
- 登录账号
- 42 页面回归与日志检查方法

## 回滚
- 停用两个菜单
- 恢复备份 JAR
- 重新执行安全重启脚本
```

Use the actual JAR size, startup command, successful timestamps, and test results from this implementation. Do not include any secret values.

- [ ] **Step 2: Self-review the runbook**

```bash
if grep -En 'password=|acceptance-start-time' docs/deployment/crm-erp-local-runbook.md; then exit 1; fi
grep -q 'yudao-module-crm-server' docs/deployment/crm-erp-local-runbook.md
grep -q 'yudao-module-erp-server' docs/deployment/crm-erp-local-runbook.md
grep -q 'maven:3.9-eclipse-temurin-17' docs/deployment/crm-erp-local-runbook.md
grep -q 'restart-backend-with-reader-secret.sh' docs/deployment/crm-erp-local-runbook.md
git diff --check -- docs/deployment/crm-erp-local-runbook.md
```

Expected: all checks pass and no secrets or placeholders are present.

- [ ] **Step 3: Commit the runbook**

```bash
git add docs/deployment/crm-erp-local-runbook.md
git commit -m "docs: record CRM and ERP local operations"
```

Expected: only the runbook is committed.

### Task 8: Final verification and cleanup

**Files:**
- Verify all committed and runtime artifacts.

**Interfaces:**
- Consumes: every previous task.
- Produces: final evidence and a clean handoff.

- [ ] **Step 1: Run final service checks**

```bash
curl -fsS -o /dev/null -w 'frontend HTTP %{http_code}\n' http://127.0.0.1:5666/
curl -fsS http://127.0.0.1:48080/actuator/health
jar tf yudao-server/target/yudao-server.jar \
  | grep -E 'BOOT-INF/lib/yudao-module-(crm|erp)-server-.*\.jar'
```

Expected: frontend 200, backend `UP`, and two module JAR entries.

- [ ] **Step 2: Confirm menu state and data preservation**

```bash
docker exec ruoyi-office-mysql sh -lc \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-office -Nse "SELECT id,name,status FROM system_menu WHERE id IN (2397,2563) ORDER BY id; SELECT SUBSTRING_INDEX(table_name,\"_\",1),COUNT(*) FROM information_schema.tables WHERE table_schema=\"ruoyi-office\" AND (table_name LIKE \"crm\\_%\" OR table_name LIKE \"erp\\_%\") GROUP BY SUBSTRING_INDEX(table_name,\"_\",1);"'
```

Expected: both menus status `0`; CRM has 19 tables and ERP has 33 tables.

- [ ] **Step 3: Confirm repository state**

```bash
git status --short --branch
git log --oneline -5
```

Expected: only the previously existing `ruoyi-office-vben/apps/web-antd/.env.development` modification remains uncommitted.

- [ ] **Step 4: Remove temporary rollback material after acceptance**

Delete `/private/tmp/ruoyi-office-backend-before-crm-erp.jar` only after all acceptance checks pass. The file is temporary and is not part of the repository.
