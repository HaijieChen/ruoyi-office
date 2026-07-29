# Architecture — CHG-2026-0729-finance-dev-db-sync

## Existing constraints

- `system_menu.id` 为各环境自增值，不能作为可移植迁移契约。
- 稳定业务键为财务根路径 `/finance`、页面 `component` 和按钮 `permission`。
- dev/test 共用同一数据库，变更会同时影响两个环境。

## Decision

角色授权 SQL 改为动态定位：根目录使用 `parent_id=0 AND path='/finance'`，页面使用明确的 `component`，按钮使用明确的 `finance:*` 权限集合。财务管理员覆盖财务目录、页面和全部财务按钮；商务人员只覆盖目录、三个业务页面及白名单按钮。

## Alternatives considered

1. 执行旧 SQL 后人工补权限：不可重复、容易漏权，拒绝。
2. 只同步表和菜单、不建角色：交付不完整，拒绝。
3. 使用固定 ID：无法跨库移植，拒绝。

## Consequences and risks

优点是跨环境可移植、可重复收敛。风险是稳定业务键若被重复或改名会导致授权异常，因此迁移后必须校验唯一性和权限差集。

## ADR requirement

不单独新增 ADR；这是迁移可移植性修复，决策记录在本变更包和 Traycer 决策制品中。

## Integration order

1. 契约测试（RED）
2. 角色 SQL 修复（GREEN）
3. 代码验证、提交并推送 dev
4. 共享库备份
5. 财务 SQL 依赖顺序执行
6. 幂等、权限和运行态验收
