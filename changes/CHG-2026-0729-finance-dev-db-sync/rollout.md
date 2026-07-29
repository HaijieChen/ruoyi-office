# Rollout — CHG-2026-0729-finance-dev-db-sync

## Deployment order

1. 推送动态角色授权修复到 `codeup/dev`。
2. 在共享库建立迁移前备份。
3. 按 phase1a → phase1b → phase1c → phase1d → 两个 phase1e → phase1f → CRUD/审计/菜单修复 → 角色授权执行。
4. 重跑同一序列验证幂等。

## Feature flags and compatibility window

无功能开关。数据库迁移完成后，当前 dev/test 应用在重新登录或权限缓存刷新后可见财务菜单。

## Observability

保存数据库 UUID、目标 schema、迁移脚本名、对象计数、角色授权计数和 API/健康检查结果；不记录密码。

## Rollback triggers

- 任一脚本报错。
- 财务根目录或页面稳定键出现重复。
- 必需表/列缺失。
- 商务人员获得财务复核或到款写权限。

## Rollback and recovery procedure

立即停止后续脚本并保留现场。系统菜单、角色及映射可从迁移前备份恢复；新建财务表先保留供核查，只有获得明确授权后才删除。备份表在验收通过后仍保留，等待用户决定清理。

## Execution result

- 备份：`bak_fin_20260729_0852_menu`、`bak_fin_20260729_0852_role`、`bak_fin_20260729_0852_role_menu`。
- 首次完整执行与第二次幂等执行均成功。
- 运行态 HTTPS、登录、财务分页 API 和权限菜单均通过。
