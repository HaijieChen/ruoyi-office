# 加班 / 补卡 / 节假日日历 上线准备

日期：2026-09-07  
分支：`deploy/oa-attendance-20260907`（基线 `codeup/main` `ea7b434bf` + 加班补卡日历，不含 IM）  
王鹏：生产 username=0002 / id=705；BPMN `candidateParam=705`。不要改生产用户 ID。  
部门负责人节点：strategy 38 param=2 只向上找候选人，单人审批，与请假/外出相同；不是两级都要依次批。  
2026 清明法定日=04-05（国令第795号农历清明当日），不是连休起日 04-04。

## 范围

包含：加班、补卡、节假日日历（加班提交会按当年 ACTIVE 日历校验周末/法定假）。  
不含：IM、当前 `feature/财务` 未提交改动。

侧边栏：

- 工作流程 → OA 示例 → 加班查询 → 节假日日历（`/bpm/oa/overtime/calendar`）
- 同级：补卡查询

## SQL 顺序（均幂等）

应用切换前先备份数据库。只加表/列/菜单/任务，不 DROP。

1. `sql/mysql/bpm_oa_overtime.sql`
2. `sql/mysql/bpm_oa_punch_correction.sql`
3. `sql/mysql/bpm_oa_quota_lock.sql`
4. `sql/mysql/bpm_oa_overtime_punch_dict.sql`
5. `sql/mysql/bpm_oa_overtime_menu.sql`
6. `sql/mysql/bpm_oa_punch_correction_menu.sql`
7. `sql/mysql/bpm_oa_overtime_calendar.sql`（含 2026 ACTIVE 种子、站内信模板、周一 09:00 抓取任务）
8. `sql/mysql/bpm_oa_attendance_oaadmin_menu.sql`（oaadmin 当前角色 + `tenant_admin` 可见查询/核验）
9. `sql/mysql/bpm_oa_attendance_catalog.sql`（假勤分类补 overtime/punch）

## 流程导入

1. 设计器导入 `sql/mysql/bpmn/oa_overtime.bpmn20.xml` 并部署。
2. 设计器导入 `sql/mysql/bpmn/oa_punch_correction.bpmn20.xml` 并部署。
3. 跑 `sql/mysql/bpm_oa_overtime_model.sql`、`sql/mysql/bpm_oa_punch_correction_model.sql`（不 INSERT 流程定义）。
4. 核对加班模型抄送节点 candidateParam=705（生产王鹏）。若仍为 221，发布前改成 705。周末链无此节点。

## 上线窗口只读核对

- 生产能否访问 `www.gov.cn` / `sousuo.www.gov.cn`（日历抓取）。访问不了不影响已种子的 2026 日历，只影响以后自动更新。
- `infra_job.handler_name = oaOvertimeCalendarFetchJob` 与 test 不要抢同一套生产数据。
- 站内信模板 `bpm_oa_overtime_calendar`。
- oaadmin 重新登录后能打开节假日日历；人事 `hr_admin` 也能查加班/补卡。

## 回滚

- 应用：切回发布前镜像/前端包。已发起的加班/补卡实例留在库里，旧包没有对应页面/接口，不要用整库快照覆盖后续业务。
- 数据库：保留表 `bpm_oa_overtime`、`bpm_oa_punch_correction`、`bpm_oa_quota_lock`、`bpm_oa_overtime_calendar_version`。不 DROP。
- 流程：停用 `oa_overtime` / `oa_punch_correction` 模型，不删除在途实例。
- 菜单：可把加班/补卡/日历 `system_menu.status` 置停用，不删角色授权历史。

## 上线后冒烟（不要在生产试真实审批）

- 打开加班/补卡发起页、节假日日历 2026 有 ACTIVE 13 天。
- 用测试租户或预发走一笔周末加班、一笔补卡（若必须用生产，用可作废账号并立即撤回）。
- 日历页面能看法定日；核验按钮仅有 verify 权限的人可见。
