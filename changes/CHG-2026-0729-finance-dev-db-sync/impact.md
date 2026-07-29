# Impact analysis — CHG-2026-0729-finance-dev-db-sync

## Affected capabilities and users

财务模块菜单发现、`finance_admin` 与 `business_staff` 的页面/按钮权限，以及 dev/test 共享库的财务数据结构。

## Modules and expected file sets

- `sql/mysql/finance_roles_finance_admin_and_business_staff.sql`
- `yudao-module-finance/.../FinanceRoleMigrationContractTest.java`
- 本变更包文档

## APIs and downstream consumers

不改变 API 契约；登录后的 `get-permission-info` 返回值会新增对应财务菜单与权限。

## Data and migrations

目标库当前无财务表与财务菜单。迁移包含 DDL/DML，DDL 自动提交，因此必须先备份系统菜单、角色、角色菜单映射。

## Security, privacy, and permissions

财务管理员拥有财务模块全量权限；商务人员只有到款查询、商务单维护和本人侧认领权限，不得获得复核、确认、驳回、撤销权限。

## Operations and observability

记录每个脚本的开始、成功或失败；任一脚本失败立即停止。迁移后对对象计数、稳定键、角色授权和重复执行结果做校验。

## Compatibility and documentation

动态匹配兼容菜单自增 ID 不同的数据库；保留 tenant_id=1 的既有约定。

## Parallelization candidates

| Stream | Deliverable | Owned paths | Dependencies | Independent tests |
|---|---|---|---|---|
| 单一串行流 | SQL 修复、推送、备份、迁移、验收 | 上述文件与目标库 | 前一步成功后才能进入下一步 | 契约测试 + 数据库验收 |

不并行：代码提交、数据库备份与迁移有严格依赖，且共享同一迁移状态。
