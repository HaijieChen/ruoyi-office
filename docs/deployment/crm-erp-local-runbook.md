# CRM / ERP 本地构建、启动与验收手册

## 1. 适用范围

本手册用于当前仓库和当前本机的开发、联调与上线前验收：

- 仓库：`/Users/chenhaijie/workspace/3dm/royi-oa`
- 分支：`codex/oa-platform-production`
- 前端：`http://127.0.0.1:5666/`
- 后端：`http://127.0.0.1:48080/`
- 健康检查：`http://127.0.0.1:48080/actuator/health`
- 管理员用户名：`admin`；密码不写入代码、脚本或本文档

本文档针对已经存在的 `ruoyi-office-mysql`、`ruoyi-office-redis`、`ruoyi-office-backend` 三个本地容器。若 MySQL 容器或数据卷丢失，应先从备份恢复当前平台数据库，不要自动创建空数据库替代。

## 2. 下次启动的最短流程

后端代码发生变化时，依次执行：

```bash
cd /Users/chenhaijie/workspace/3dm/royi-oa
git checkout codex/oa-platform-production

./scripts/local/build-backend-jdk17.sh
./scripts/local/restart-backend-with-reader-secret.sh
```

另开一个终端启动前端：

```bash
cd /Users/chenhaijie/workspace/3dm/royi-oa
./scripts/local/start-frontend.sh
```

验证：

```bash
curl -fsS http://127.0.0.1:48080/actuator/health
curl -fsSI http://127.0.0.1:5666/
```

预期后端返回 `{"status":"UP"}`，前端返回 HTTP 200。前端脚本以前台方式运行，按 `Ctrl+C` 停止。

仅重启现有部署、后端代码未变化时，可以跳过构建，直接执行：

```bash
cd /Users/chenhaijie/workspace/3dm/royi-oa
./scripts/local/restart-backend-with-reader-secret.sh
./scripts/local/start-frontend.sh
```

## 3. JDK、Maven、Node 与 pnpm 选择

### 3.1 后端统一选择 JDK 17

项目的编译目标是 Java 17。构建和自动化测试统一使用：

- Docker 镜像：`maven:3.9-eclipse-temurin-17`
- Maven profile：`boot`
- 构建脚本：`scripts/local/build-backend-jdk17.sh`

本机 Maven 位于 `/Users/chenhaijie/.local/bin/mvn`，版本为 3.9.16，但它当前由 JDK 21.0.11 驱动。本机只注册了 JDK 21 和 JDK 8，没有 JDK 17。因此不要直接用本机 Maven 构建本项目。

特别注意：在这台机器上，`/usr/libexec/java_home -v 17` 找不到 JDK 17 时可能回退到 JDK 21，不能只根据命令是否退出成功判断版本。构建脚本会在容器内执行 `java -version` 并明确断言版本为 17。

当前本地后端容器仍使用历史镜像 `maven:3.9.9-eclipse-temurin-21`，运行时 Java 为 21.0.7；本次 JDK 17 编译产物已在该运行时通过验收。生产发布应使用精简、不可变的 JRE 17 镜像，不应使用 Maven 开发镜像作为运行镜像。

### 3.2 前端固定 pnpm 10.28.2

- 当前 Node：24.18.0
- 仓库 `packageManager`：`pnpm@10.28.2`
- Vite：7.3.1
- 固定版本启动脚本：`scripts/local/start-frontend.sh`

系统路径中还可能出现 pnpm 11，不能用它替代项目声明的 pnpm 10.28.2。启动脚本通过 npm 固定调用指定版本，不修改全局 Node/npm 配置。

## 4. CRM / ERP 需要启用的 JAR

`yudao-server/pom.xml` 必须包含以下两个单体后端依赖：

- `cn.iocoder.cloud:yudao-module-crm-server`
- `cn.iocoder.cloud:yudao-module-erp-server`

最终 `yudao-server.jar` 中必须同时存在：

- `BOOT-INF/lib/yudao-module-crm-server-2026.01-SNAPSHOT.jar`
- `BOOT-INF/lib/yudao-module-crm-api-2026.01-SNAPSHOT.jar`
- `BOOT-INF/lib/yudao-module-erp-server-2026.01-SNAPSHOT.jar`
- `BOOT-INF/lib/yudao-module-erp-api-2026.01-SNAPSHOT.jar`

验证命令：

```bash
jar tf yudao-server/target/yudao-server.jar \
  | grep -E 'BOOT-INF/lib/yudao-module-(crm|erp)-(api|server)-.*\.jar'
```

必须使用 `-Pboot` 构建单体包。若遗漏该 profile，默认 `cloud` profile 会先把各业务模块打成可执行 Boot JAR，再嵌入主 JAR，包体会异常增大，Spring 也无法扫描嵌套模块中的 Bean。

本次错误示例是包体从约 200 MB 增长到约 1.48 GiB，并在启动时找不到 `PermissionCommonApi`。正确单体包约 201 MB。

## 5. 后端构建与测试

### 5.1 CRM / ERP 及依赖测试

```bash
docker run --rm \
  -v /Users/chenhaijie/workspace/3dm/royi-oa:/workspace \
  -v /Users/chenhaijie/.m2:/root/.m2 \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 \
  mvn -Pboot \
  -pl yudao-module-crm/yudao-module-crm-server,yudao-module-erp/yudao-module-erp-server \
  -am test
```

不要用受限环境中的宿主 JDK 21 结果替代此测试。若 Mockito / Byte Buddy 在测试体执行前提示无法附加到当前 JVM，这是运行环境的代理附加限制，不是业务测试失败；直接切换到上述可信 JDK 17 容器执行。

### 5.2 完整单体包

```bash
./scripts/local/build-backend-jdk17.sh
```

脚本会：

1. 断言构建镜像确实是 Java 17；
2. 使用 `mvn -Pboot -pl yudao-server -am clean package -DskipTests`；
3. 断言最终包包含 CRM、ERP server JAR；
4. 输出最终文件大小。

构建会替换当前绑定到容器的 JAR 文件。构建成功后应立即执行安全重启脚本，避免旧 JVM 长时间继续持有 `app.jar (deleted)` 文件句柄。

## 6. 基础设施和后端启动

当前本地容器与端口：

| 容器 | 宿主端口 | 容器端口 | 用途 |
| --- | ---: | ---: | --- |
| `ruoyi-office-mysql` | 33061 | 3306 | 当前平台数据库 |
| `ruoyi-office-redis` | 6380 | 6379 | 当前平台缓存 |
| `ruoyi-office-backend` | 48080 | 48080 | 单体后端 |

不要把它们和本机另外存在的 `oa-mysql:3306`、`oa-redis:6379` 混用。

统一执行：

```bash
./scripts/local/restart-backend-with-reader-secret.sh
```

脚本会完成：

1. 启动 `ruoyi-office-mysql` 和 `ruoyi-office-redis`；
2. 创建或更新 `ruoyi_form_reader@%`；
3. 撤销该账号的其它权限，只授予 `ruoyi-office.*` 的 `SELECT`；
4. 随机生成 64 位十六进制密码，过程中不打印密码；
5. 重启 `ruoyi-office-backend`；
6. 把密码仅写入容器 tmpfs 的 `/run/secrets/bpm.form-data-source.jdbc.password`，权限设为 `0400`；
7. 等待健康检查；
8. 校验运行中 JAR 大小，并拒绝 `app.jar (deleted)` 状态。

只读权限验证：

```bash
docker exec ruoyi-office-mysql sh -lc \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW GRANTS FOR '\''ruoyi_form_reader'\''@'\''%'\'';"'
```

应只有 `USAGE` 和 `GRANT SELECT ON ruoyi-office.*`。

查看状态和日志：

```bash
docker ps --filter name=ruoyi-office
docker logs --tail 200 ruoyi-office-backend
curl -fsS http://127.0.0.1:48080/actuator/health
```

## 7. 前端启动

前端开发配置位于 `ruoyi-office-vben/apps/web-antd/.env.development`：

- 端口：5666
- 后端基础地址：`http://127.0.0.1:48080`
- API 前缀：`/admin-api`

启动：

```bash
./scripts/local/start-frontend.sh
```

脚本等价于：

```bash
cd /Users/chenhaijie/workspace/3dm/royi-oa/ruoyi-office-vben
npm exec --yes --package=pnpm@10.28.2 -- pnpm \
  --filter @vben/web-antd exec vite \
  --mode development \
  --host 127.0.0.1 \
  --port 5666 \
  --strictPort
```

`--strictPort` 用于防止 5666 被占用时静默改用其它端口。检查占用：

```bash
lsof -nP -iTCP:5666 -sTCP:LISTEN
```

## 8. CRM / ERP 菜单开关

CRM 顶级菜单 ID 为 2397，ERP 顶级菜单 ID 为 2563。后端健康后才能启用菜单。

启用：

```bash
docker exec ruoyi-office-mysql sh -lc \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-office -e "START TRANSACTION; UPDATE system_menu SET status=0, updater=\"1\", update_time=NOW() WHERE id IN (2397,2563) AND deleted=0; SELECT id,name,path,status FROM system_menu WHERE id IN (2397,2563) ORDER BY id; COMMIT;"'
```

停用或紧急回滚菜单：

```bash
docker exec ruoyi-office-mysql sh -lc \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-office -e "START TRANSACTION; UPDATE system_menu SET status=1, updater=\"1\", update_time=NOW() WHERE id IN (2397,2563) AND deleted=0; SELECT id,name,path,status FROM system_menu WHERE id IN (2397,2563) ORDER BY id; COMMIT;"'
```

当前数据库基线：CRM 19 张表、ERP 33 张表。启用模块和菜单不会初始化、清理或合并 CRM、ERP、WMS 的业务数据。

## 9. 验收清单

### 9.1 自动化与运行态

```bash
curl -fsS http://127.0.0.1:48080/actuator/health

stat -f '%z %N' yudao-server/target/yudao-server.jar
docker exec ruoyi-office-backend stat -c '%s %n' /app.jar

docker exec ruoyi-office-backend sh -lc \
  'if ls -l /proc/1/fd | grep -q "app.jar (deleted)"; then exit 1; fi'
```

主机 JAR 与容器 `/app.jar` 大小必须一致，健康状态必须为 `UP`。

### 9.2 页面回归

至少覆盖：

- CRM 20 个页面；
- ERP 22 个页面；
- `/workspace/home`；
- `/bpm/start-process`；
- `/infra/codegen`；
- `/asset/category`；
- `/wms/warehousing`。

页面不能出现“系统异常”“系统错误”“加载失败”“Internal Server Error”“404 Not Found”，并应同步检查回归时间段的后端 `ERROR`、类缺失、表缺失和 SQL 语法异常。

## 10. 已处理的兼容问题

1. CRM 合同、回款 BPM 状态监听器改为从 `event.getProcessInstanceInfo().getStatus()` 读取状态，兼容当前 BPM 事件模型。
2. ERP 的 `ErpPurchaseOrderMapper` 注入字段使用 `erpPurchaseOrderMapper`，避免和 WMS 的 `purchaseOrderMapper` Bean 按名称冲突。
3. 单体构建固定启用 Maven `boot` profile，避免业务模块被重复打成嵌套 Boot JAR。

合并上游分支时要保留这三项，否则可能出现编译失败、后端启动失败或包体异常增大。

## 11. JAR 回滚

每次构建前先保存当前已验证包：

```bash
cp -p yudao-server/target/yudao-server.jar /private/tmp/ruoyi-office-backend-before-change.jar
```

若新包不能启动：

```bash
cp -p /private/tmp/ruoyi-office-backend-before-change.jar \
  yudao-server/target/yudao-server.jar
./scripts/local/restart-backend-with-reader-secret.sh
```

恢复健康后再停用 CRM / ERP 菜单。不要在后端不健康时只开放菜单。

## 12. 生产发布边界

本地验收通过不等于生产发布完成。生产环境还必须执行：

1. 使用 JRE 17 精简运行镜像，并把 JAR 固化到带版本号的不可变镜像；
2. 使用生产 profile 和外部配置，不使用 `application-local`；
3. 数据库、Redis 和动态表单只读账号密码全部通过密钥管理系统注入；
4. 先备份数据库，再执行菜单变更；
5. 前端执行生产构建并由 Web 服务器托管，不能运行 Vite 开发服务器；
6. 配置 HTTPS、反向代理、日志、监控、备份和回滚镜像；
7. 先在预发布环境重复 42 个 CRM / ERP 页面及核心原有模块验收，再放量上线。

## 13. 2026-07-18 本机验收记录

- 分支：`codex/oa-platform-production`
- 后端 JDK 17 模块测试：28 个 reactor 模块 `BUILD SUCCESS`
- 完整单体构建：48 个 reactor 模块 `BUILD SUCCESS`
- 最终 JAR：201,258,118 字节
- SHA-256：`3dcedc74dbd3cd5967e4f561908c0e2b8af53536183b02284a43f528f027a101`
- 构建完成：2026-07-18 06:00:07 UTC
- 运行态检查：2026-07-18 06:00:38 UTC，健康状态 `UP`
- 最终浏览器回归开始：2026-07-18 06:00:38 UTC
- 浏览器结果：CRM / ERP 42 个页面 + 原有核心 5 个页面，共 47/47 通过
- 回归期间后端异常特征日志：0 条
- CRM 菜单 2397：已启用
- ERP 菜单 2563：已启用
- 动态表单账号：仅 `ruoyi-office.*` 的 `SELECT`
- 前端固定版本脚本：临时 5667 端口启动成功并返回 HTTP 200；当前 5666 服务保持运行
- 当前 Maven 构建会把归档时间戳写入 JAR，因此同一源码重复构建的 SHA-256 可能变化；每次发布应重新记录实际发布包哈希，不能把本条哈希当成长期固定值
