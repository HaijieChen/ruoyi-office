# U11 隔离验收记录（TICKET-0446）

本文件记录**当前不可变 8def 包**的隔离验收结论。不是发布回执，也不是全仓绿灯。

## 真正路由 Pass（统一入口）

父 native 唯一 URL 窗口、`document.hidden=false`：

| 路由 | 结论 | 主证据 |
|---|---|---|
| `/bpm/start-process` 假勤→加班 | Pass：目录卡片可见；内嵌表单 10:00–23:00 只读 8.0；不足 2h 拒绝且行不增；8h 提交成功 overtime#5 | 截图 39、40；父 SQL `hours=8 status=1` |
| `/bpm/start-process` 假勤→补卡 | Pass：10/1 提交成功 punch#4；再看剩余 1；9 月第三次 toast「该月补卡次数已用完」、`hidden=false`、新增 reason 零行 | 截图 46、48；9 月拒绝由父 UI 复验 |
| `/bpm/task/my` 提交后列表 | Pass：补卡「审批中」 | 截图 47（父 `read_image`） |

**不得用独立 `/bpm/oa/overtime/create`、`/bpm/oa/punch/create` 或列表页替代上述统一入口。** 独立 create/list 与 HR 最小权限待办（截图 13–21，overtime#4 终态 2）仍作补充引用，只证明局部表单/权限 fixture，不算统一入口 Pass。

## Skip

- `/home`：隔离 fixture 导航 404，非本范围。

## 截图局限

- **主证据**：39（统一目录可见）、40（统一加班 8.0）、46（10/1 表单，提交前 remaining=2 / 09:00）、47（提交后我的流程）、48（统一入口 10/1 remaining=1）。
- **41**：统一入口 AE1 拒绝过程帧，不作最终目录/成功提交证明。
- **45**：文件名含 october，实际曾停在 9 月旧帧；**不得**当 10 月成功证据。以 46 + 父提交/SQL 为准。

监控：post-load `console.error` / `window.error` / `unhandledrejection` 期间切 9 月并第三次提交 = 0 errors。**不声称** bootstrap 或全历史 console clean。

## 包与测试分层（勿混）

| 层 | 是什么 | 不是什么 |
|---|---|---|
| 当前验收包 | SHA `8def53f0a335a454228c7f661c033a0d1a497cad58d28db87839bc06d61c4102`，只读 bind `/tmp/royi-oa-u11-artifacts/8def53f0….jar` | 已验 17:10 e6b 包 |
| 8def 定向 17 断言 | `U20_MONTH=2026-12 U20_RUN_ID=dec1` 脚本 17/17；JSON `u20-8def-directed-regression-2026-12-dec1.json` | 83/83 全量重跑 |
| 旧 e6b 83/83 | 换包前后端回归 | 不可外推到 8def |
| 前端 17 | 父本轮 21:46:49 重跑 vitest 3 files / 17 tests 通过（holiday-options / overtime-hours / punch-remaining），483ms，仅 Browserslist 过期提示 | 浏览器 E2E |
| 全仓 tsc | 本功能 9 条清；全仓约 973 为此前基线，非本轮重跑 | 不宣称全绿 |

版本精度：统一加班#5 的发起发生在固定 8def 之前；截图 40 是该次 UI 证据，本轮 SQL 再次核对其 8h。8def 当前加班后端由 12 月定向 HTTP 断言验证。统一补卡#4 与本轮余次/拒绝在固定 8def 下完成。开头「当前不可变 8def 包」不表示全部历史 UI 操作都在固定 8def 下重跑。

父独立 SQL（8def）：ot#5 hours=8 status=1；#11 5/status=1；#12 2/status=2 任务 0；#13 5/status=4；#14 5/status=1。punch 9 月 2 / 10 月 1 / 11 月 2 / 12 月 2。两定义 `initiator_withdraw_mode` 仍 NULL。jar 8def 与四个保护文件 SHA 由父确认未变。

## 首跑用例设计错误（已修正，JSON 保留）

1. 并发加班误用同一 11-10 00:00–05:00 窗口，失败码是重叠 400，不是 8h 额度。
2. 小时合计按相同 reason 跨日 LIKE，11-10#6 + 11-11#10 会被算成 10 而假失败。

已改为：显式月份 + run id、空白月 fail-closed、按本 run 的日/user/`status∈{1,2}` 汇总。12 月完整跑 17/17。  
**不删除** 11 月失败/补跑 JSON：`/tmp/royi-oa-u11-artifacts/u20-8def-directed-regression.json`。

旧行校验只比对所选业务列（加班：id/user_id/start_time/end_time/hours/holiday/status/process_instance_id/reason/deleted；补卡：id/user_id/punch_date/punch_time/status/process_instance_id/reason/deleted），**不是**表全列。

## 环境与发布

- 隔离服务保留：API `59283297bafa` internal-only 无 publish，IP `172.31.0.4`；proxy `1db0cb976259` `127.0.0.1:48081`；MySQL 33062；Redis 6381。
- **真实发布前置仍 open**：未部署目标环境。撤回策略迁移已在默认发布单（他会话事项），不归本 OA 票执行。
- 清理见 `CLEANUP.md`。本文档写入时**未执行**清理。
