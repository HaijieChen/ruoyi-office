# Status — CHG-2026-0729-finance-dev-db-sync

- Baseline commit: `afb61e6a5bd735d493aaa4f3e14ba45bc7d86034`
- Integration branch: `fix/finance-role-menu-dynamic` → `codeup/dev`
- Overall status: implementation in progress

## Workstreams

| Stream | Branch | Worktree | Owner | Status | Evidence |
|---|---|---|---|---|---|
| 动态授权与共享库迁移 | `fix/finance-role-menu-dynamic` | 当前 Traycer 隔离工作区 | Codex | in progress | 用户确认方案 1 |

## Integration log

- 2026-07-29：确认外网数据库与内网运行库 UUID/菜单指纹一致，目标 schema 为 `ruoyi_office`。
- 2026-07-29：用户明确确认方案 1；排除测试用户 SQL 和现有用户角色分配。
- 2026-07-29：新增角色迁移契约测试，RED 阶段 2/2 按预期失败，动态匹配修复后 GREEN 阶段 2/2 通过。

## Regression results

- `FinanceRoleMigrationContractTest`：2/2 通过。
- 财务模块：105 个测试通过；1 个基线遗留的 VO 字段契约测试失败，已在未修改的 `main@9139607e` 复现。
- `git diff --check`：通过。

## Remaining risks

迁移尚未执行；DDL 自动提交，必须先完成备份验证。基线遗留 VO 契约测试不在本次迁移修复范围。

## Memory and documentation updates

变更包与 Traycer 决策记录同步维护；不写入凭据。
