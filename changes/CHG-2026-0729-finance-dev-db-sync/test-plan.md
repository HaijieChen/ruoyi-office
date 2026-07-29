# Test plan — CHG-2026-0729-finance-dev-db-sync

## Direct acceptance tests

- 检查角色 SQL 不含 5225、5228、5229、5245。
- 检查根目录、页面组件和权限码均按稳定键匹配。

## Unit tests

新增 `FinanceRoleMigrationContractTest`，读取真实 SQL 文件验证迁移契约。

## Integration and contract tests

运行财务模块迁移契约测试；迁移后通过 JDBC 查询真实共享库。

## Adjacent regression

运行财务模块完整测试集；检查 Git diff 和 SQL 文件集合。

## Core smoke paths

重新登录后校验财务菜单；访问财务列表 API，确认不出现表不存在或权限缺失错误。

## Security and permission isolation

- `finance_admin` 包含全部 `finance:%` 权限。
- `business_staff` 不包含 review/confirm/reject/revoke 及到款写权限。

## Migration, rollback, idempotency, concurrency, and recovery

- 迁移前复制 `system_menu`、`system_role`、`system_role_menu` 为带时间戳备份表。
- 任一 SQL 失败立即停止。
- 全量脚本重复执行一次，比较表/菜单/角色/映射计数及稳定键集合。
- 不并发运行迁移。

## Evidence matrix

| Requirement or risk | Test level | Command or case | Result |
|---|---|---|---|
| 无硬编码菜单 ID | 单元/契约 | `FinanceRoleMigrationContractTest` | RED 2 fail → GREEN pass |
| 兼容真实 system_menu 结构 | 单元/契约 + 只读探针 | 禁止 `m/p.tenant_id` | RED 1 fail → GREEN pass |
| 兼容受限迁移账号 | 单元/契约 + 真实库 | phase1e 不依赖存储过程权限 | RED 1 fail → GREEN 4/4 pass |
| 财务模块回归 | 模块测试 | Maven finance server tests | 105 pass；1 个基线遗留失败 |
| 数据库可恢复 | 真实库 | 备份表行数与源表一致 | pending |
| 迁移幂等 | 真实库 | 全量 SQL 连续执行两次并比较指纹 | pending |
| 角色隔离 | 真实库/API | 角色菜单与权限白名单/黑名单查询 | pending |

基线遗留失败：`FinanceReceiptClaimControllerContractTest.revokeAuditRespVOShouldExposeOnlyImmutableAuditFields` 仍期望 5 个字段，当前基线 VO 已有 11 个字段。已在未修改的 `main@9139607e` 独立复现，不属于本变更范围。
