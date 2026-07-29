# Status — CHG-2026-0729-finance-dev-db-sync

- Baseline commit: `afb61e6a5bd735d493aaa4f3e14ba45bc7d86034`
- Integration branch: `fix/finance-role-menu-dynamic` → `codeup/dev`
- Overall status: complete

## Workstreams

| Stream | Branch | Worktree | Owner | Status | Evidence |
|---|---|---|---|---|---|
| 动态授权与共享库迁移 | `fix/finance-role-menu-dynamic` | 当前 Traycer 隔离工作区 | Codex | complete | dev 推送、备份、迁移、幂等及运行态验收通过 |

## Integration log

- 2026-07-29：确认外网数据库与内网运行库 UUID/菜单指纹一致，目标 schema 为 `ruoyi_office`。
- 2026-07-29：用户明确确认方案 1；排除测试用户 SQL 和现有用户角色分配。
- 2026-07-29：新增角色迁移契约测试，RED 阶段 2/2 按预期失败，动态匹配修复后 GREEN 阶段 2/2 通过。
- 2026-07-29：真实库只读探针发现 `system_menu` 无 `tenant_id`；新增契约测试先失败，再移除无效菜单租户条件。
- 2026-07-29：真实迁移在 phase1e 第 18 条因账号无 `ALTER ROUTINE` 权限停止；删列尚未开始。以不需存储过程权限的动态 SQL 保护分支替代。
- 2026-07-29：迁移前备份 `bak_fin_20260729_0852_{menu,role,role_menu}` 创建成功，行数分别为 938、6、873。
- 2026-07-29：13 个财务脚本完整执行成功，并再次全量执行成功验证幂等。
- 2026-07-29：HTTPS 登录、财务分页 API 与权限菜单运行态验收通过。

## Regression results

- `FinanceRoleMigrationContractTest`：3/3 通过。
- 财务模块：105 个测试通过；1 个基线遗留的 VO 字段契约测试失败，已在未修改的 `main@9139607e` 复现。
- `git diff --check`：通过。
- 真实库最终指纹：6 张财务表、26 条财务菜单、2 个财务角色；根目录与稳定键重复数为 0。
- 授权：`finance_admin` 26 条、`business_staff` 14 条；管理员漏权 0、商务额外/禁用权限 0。
- 数据边界：测试用户 0、财务角色用户绑定 0；最终列缺失 0、遗留列 0。
- 运行态：登录业务码 0、财务分页业务码 0、权限接口财务根目录 1、页面 4。

## Remaining risks

基线遗留 VO 契约测试不在本次迁移修复范围。迁移备份按约定保留，尚未清理。

## Memory and documentation updates

变更包与 Traycer 决策记录同步维护；不写入凭据。
