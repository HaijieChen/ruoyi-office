---
title: OA 加班与补卡申请流程
date: 2026-09-05
type: feat
topic: overtime-punch
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-plan-bootstrap
execution: code
---

# OA 加班与补卡申请流程

## Goal Capsule

Objective: 员工可在 OA 假勤目录发起加班申请与补卡申请；系统按原文额度与审批链强制校验，申请时可见补卡剩余次数。
Means: 复用 BPM OA 外出申请形态（业务表 + 嵌入式 Vue 表单 + BPMN 种子），加班一个 process key 用网关分周末/节假日链 (KTD1)。
Authority: `OA-人事需求沟通-20260904.docx` 13-1/13-2 定审批与额度；用户提供的企微截图定表单字段；会话已确认补卡按补卡日期所在月计次、撤回/驳回释放额度。
Stop: 不实现第 12 条假期余额、不写考勤机同步、不改用户已有未提交文件、不远程部署、不对任何既有库导入/部署/写业务数据。隔离目标唯一：新建可丢弃 `mysql:8.0` 实例（空闲口 13306 或 33062，不对外 bind、不用生产凭证）。禁写 `33061`、既有 `48080`、Redis `6379`，含这些端口上的任何 schema。U9/U10 无该实例则只读 blocked。U12 不改全 RPC 鉴权、不给业务 create 加 query 墙、不把加班/补卡改走 Finance HMAC/`createProcessInstanceByBusiness`。HTTP 通用直启尚未实测利用，不得写成已利用。

## Product Contract

### Summary

新增两个假勤流程：加班（周末链 / 法定节假日链）和补卡。表单对齐企微截图；审批对齐需求原文，不以企微截图里的行政/抄送名单替代原文审批链。

### Problem Frame

假勤目录描述已含「加班补卡」，但没有业务表、BPMN、发起卡片。人事目前只能走企微。额度若只做前端校验会被并发突破。

### Actors

- A1. 员工：发起加班/补卡，查看本人单据与剩余补卡次数。
- A2. 部门负责人：两流程第一级审批。
- A3. 人事（hr_admin 或签，企微示例为刘鹤/李霞）：第二级审批。
- A4. 分管总（复用已有 `gm` 角色）：仅法定节假日加班第三级。
- A5. 抄送人王鹏：仅法定节假日加班通过后抄送，不是详情读者。

### Requirements

- R1. 加班表单字段与顺序对齐企微：加班事由（必填）、开始时间（日期+时刻，必填）、结束时间（日期+时刻，必填）、加班时长（小时，只读自动计算）、是否法定节假日（必填下拉）、附件（选填）。OA 惯例可另显只读申请人/部门及任职公司。
- R2. 加班时长由服务端根据开始/结束计算；忽略客户端提交的时长。时钟时长不足 2 小时拒绝（含 10:00–11:00）。时钟时长按分钟/60 保留 1 位小数后与 8 小时取小，作为本单时长。10:00–23:00 计 8 小时。
- R3. 开始与结束必须同一自然日（Asia/Shanghai）。跨日拒绝。
- R4. 同一申请人同一自然日，审批中+已通过的加班时长合计加本单不得超过 8 小时。时间重叠的审批中/已通过单额外拒绝。
- R5. `是否法定节假日=否` 走周末链：部门负责人 → 人事或签。`=是` 走节假日链：部门负责人 → 人事或签 → 分管总 → 抄送王鹏。由申请人选择，不查系统日历。
- R6. 补卡表单对齐企微：补卡日期（必填）、补卡时间（日期+时刻，必填）、补卡事由（必填）、说明附件（选填）。申请时按所选补卡日期显示本月剩余次数。
- R7. 每人每月最多 2 次补卡。月份按补卡日期所在月，不按提交日。
- R8. 加班日额度与补卡月次数只统计审批中与已通过。撤回、驳回、取消后释放。流程启动失败不占用。
- R9. 额度校验必须在服务端同一事务内原子完成，并发双提交不得突破 8 小时或 2 次。
- R10. 发起入口在假勤目录；列表/详情/重提复用外出形态。详情读者=发起人、query 权限、当前办理人；抄送人不是读者。
- R11. 登录员工即可发起（create 不套请假那种 query 权限墙）。人事/超管可查全部。
- R12. 通过后本期不写考勤机、不增加调休余额。

### Flows

- F1. 员工填加班 → 前端展示计算时长 → 提交 → 后端重算并锁日额度 → 启流程。节假日走分管总并抄送。
- F2. 员工选补卡日期 → 显示剩余次数 → 提交 → 后端锁月次数 → 启流程：部门负责人 → 人事或签。
- F3. 撤回/驳回后额度立即按 R8 可再申请。

### Acceptance Examples

- AE1. 10:00–11:00 加班无法提交。
- AE2. 10:00–23:00 时长为 8。
- AE3. 同日已有 6 小时审批中，再申请 3 小时失败；撤回后再申请 3 小时成功。
- AE4. 同日两笔不重叠各 4 小时可过；并发两笔各 5 小时只有一笔成功。
- AE5. 9 月提交补 8 月 31 日的卡，扣 8 月次数。
- AE6. 8 月已有 2 笔审批中/已通过补卡，第 3 笔失败并提示剩余 0。
- AE7. 法定节假日=是 出现分管总节点且抄送王鹏；=否 无分管总、无抄送。
- AE8. 补卡发起页在选择补卡日期后显示剩余次数，提交时次数不足被后端拒绝。

### Success Criteria

员工能从假勤目录走通两类申请；额度与审批链与 AE 一致；单测覆盖时长/额度/并发；浏览器能打开发起页并看到补卡剩余次数。

### Scope Boundaries

In: 13-1 加班、13-2 补卡、假勤目录卡片、列表详情、BPMN 种子与幂等 SQL。U12 仅 `oa_overtime`/`oa_punch_correction`：服务器业务启动上下文 + 回调 `processInstanceId` 绑定。
Out: 第 12 条年假/调休；13-3 及之后人事流程；工作日加班专项类型；考勤机写入；按企微截图增加行政节点或钟伟抄送。全 RPC `permitAll` 改造；业务 create 加 `bpm:oa-*:query`/`create` 墙（R11）；复用 Finance HMAC；改 EligibilityImpl 或恢复全局 trusted deny；**把全局 `notification.async` 改 false**；外出/请假/薪税通道化。KTD13 不在本写者会话改 U9 IT。
Deferred: 用系统节假日日历自动判断类型。

### Key Decisions

- KD1. 表单跟企微截图，审批跟需求原文。`(session-settled: user-directed — 用户上传截图作为表单依据，原文 13-1/13-2 已写审批链)`
- KD2. 补卡按补卡日期所在月计次。`(session-settled: user-directed — chosen over 按提交月)`
- KD3. 撤回/驳回释放额度。`(session-settled: user-directed — chosen over 提交后不返还)`
- KD4. 是否法定节假日由申请人选择，不查日历。
- KD5. 分管总复用请假 `gm` 角色，不新建「分管总」角色。
- KD6. 抄送王鹏用指定用户（部署 SQL 按 nickname=`王鹏` 解析 userId；找不到则种子失败并记发布单）。
- KD7. 一个加班 process key `oa_overtime`，网关变量 `holiday`。补卡 `oa_punch_correction`。

### Sources

- 需求原文 13-1/13-2。
- 企微加班表单截图、企微补卡表单截图。
- 外出实现：`BpmOAOutingServiceImpl`、`sql/mysql/bpmn/oa_outing.bpmn20.xml`、`views/bpm/oa/outing/`。

## Planning Contract

### Key Technical Decisions

- KTD1. 落在 `yudao-module-bpm` OA 包，不新建 Maven 模块。形态复制外出：插单 → `createProcessInstance` → 回写 processInstanceId，同事务。
- KTD2. 加班时长计算器独立于 `OaDurationHours`，以免改坏外出。
- KTD3. 日/月额度在 insert 前先按 KTD13 对 `bpm_oa_quota_lock` ODKU 拿申请人维度排他行锁，再跑原计数算法。空集合业务行 `FOR UPDATE` 已否决。流程启动失败回滚锁与占用。
- KTD4. 错误码：加班 `1_009_001_012`–`016`，补卡 `017`–`019`。U1 先写入 `ErrorCodeConstants.java`，避免并行抢文件。
- KTD5. BPMN：部门负责人 `strategy=38 param=2`；人事 `strategy=10 hr_admin approveMethod=3`；分管总 `strategy=10 gm`；抄送 ServiceTask `bpmCopyTaskDelegate` + 用户策略。种子不 INSERT 流程定义，部署后跑 model SQL，与请假/外出相同。
- KTD6. 共享文件只许 U8 改：`embed-registry.ts`、`bpm_process_start_catalog.sql` CASE、`BpmOATripOutingMenuContractTest.java`。菜单 SQL 可按流程拆文件但挂同一请假父级。
- KTD7. 不修改当前工作区已脏文件：`file-preview-list.vue`、`guard.ts`、`core.ts`、`scripts/local/hrm-role-permission.test.mjs`。
- KTD8. 额度并发：先在 KTD9 可丢弃实例上用 InnoDB 取证，再决定是否改锁。无该实测报告时，不得宣称已证实突破或已证实安全（含 mock `ReentrantLock`）。有隔离级别下的空集合双插入突破证据时必须报告，并按证据做最小锁修复。`(session-settled: user-directed — chosen over 先改锁设计: 优先验证)`
- KTD9. 禁止向任何既有 DB 导入/部署/写业务数据。禁写 `33061`、既有 `48080`、Redis `6379`。009f 预检：已有 `mysql:8.0` arm64，空闲 `13306`/`33062`/`48081`，宿主 JDK 17.0.19+10 与 Maven 3.9.16。U9/U10 用这些空闲口起不对外 bind、不用生产凭证的可丢弃实例；不得把 33061 当目标。`(session-settled: user-directed — chosen over 把 localhost:33061/48080/6379 当私有验收库)`
- KTD10. 王鹏种子必须失败关闭：执行前将 `@oa_overtime_wangpeng_id` 置 NULL；缺失/非唯一/id≠221/陈旧会话变量均不得让脚本成功。注释不得把 MySQL `SELECT INTO` 无行写成必定 ERROR 1329。修复范围仅 `sql/mysql/bpm_oa_overtime_model.sql`。不得先断言已在数据库复现。
- KTD11. 加班/补卡只能由本 JVM 业务 Service 在同一额度事务内、insert 业务行之后置位的启动上下文启流（业务行+额度先行），HTTP/RPC 参数不能伪造。独立 Holder（勿复用 `BpmBusinessStartChannelHolder`）。守卫只认这两个 key，写在 `createProcessInstance0` 的 key 分支，**不改** `BpmProcessStartEligibilityServiceImpl`（L10–12、L28–30、L34–35、L44–45 为当前显式「目录可见即可通用直启，薪税与其它一样」，不是可恢复的旧 Finance deny）。evaluate / 目录可见 / 非本两 key 原行为保持。无 Holder 时拒绝这两 key **不是** `bpm:oa-*:create` 权限墙（R11 登录仍走业务 create）。禁止恢复全局 `trustedBusinessStart` deny。若扩展守卫必须仍限这两 key，并附无关 key 回归（至少薪税或付款或外出之一无 Holder 仍可通用 create）。`(session-settled: user-directed — chosen over 恢复 Eligibility 旧 deny / 全 RPC 鉴权 / Finance HMAC / create 加 query 墙)`
- KTD12. 状态回调按单据已回写的 `processInstanceId` 精确匹配才改 status/额度。不得用事件反写 piId。错实例/缺 businessKey/单据不存在：不改库。`processCreated` 不发状态通知。`processProcessInstanceCompleted`（约 L1396–1447，RUNNING 升 APPROVE）对 **仅** `oa_overtime`/`oa_punch_correction`：在 Flowable 线程上快照 `processInstanceId/businessKey/key/status/tenantId`（禁止 lazy `ProcessInstance`），`afterCommit` 内 **`PROPAGATION_REQUIRES_NEW`（或 `TransactionTemplate`）新开写事务** 再同步 Local handler（`TenantUtils.execute`+快照 tenant）。原审批 TX 已 commit，同步≠已持久化；测试须在内层 TX commit 后读库行。`STATUS_ROLLED_BACK` 不派发。禁止 afterCommit 再进 `sendProcessStatusNotification`/`runAsync`。其它 key 仍走现 manager async。不得把全局 `yudao.bmp.notification.async` 改 false 当闭合。create 回写后 reconcile 只覆盖启流即时终态，**不能**替代审批事务 afterCommit。`(session-settled: user-directed — chosen over 全局关 async / 事件反写 piId / 仅同步不新开事务)`
- KTD13. 空集合 `FOR UPDATE` 已否决：RC 双空读 20/20 突破>8h；RR 20/20 死锁≠额度安全。锁定：新表 `bpm_oa_quota_lock`，唯一/主键 `(tenant_id, user_id, quota_type, period)`。`quota_type`+`period`：`OVERTIME_DAY`+`YYYY-MM-DD`；`PUNCH_MONTH`+`YYYY-MM`。两 Service 在同一 Spring TX、**原额度查询之前** `INSERT ... ON DUPLICATE KEY UPDATE` 拿稳定排他行锁，随后保留原额度算法；提交/回滚自然释放。不用 `GET_LOCK`、不用系统用户行锁。纠正「ODKU 必须改列才锁」：no-op UPDATE 也应拿 X 锁；`touch_seq` 可留作实现细节但**不宣称业务必要**。`tenant_id` 必须与真实 `TenantContext` 严格一致，生产禁止静默 `0` fallback；IT 显式租户上下文或与生产相同 tenant 配置。DDL 与 IT schema 同步；DO/Mapper 最小。绿：RR/RC 各 20 次加班恰好 1 成功 + 1 额度错误（非 deadlock）；补卡空集合 2 都成功、3 最多 2；跨用户/跨租户隔离；回滚释放。红 probe 保留 baseline，绿用例禁止 SELECT 后再 barrier。发布 SQL 由父记真实 UUID，本写者不猜环境名。Main 仅在本 KTD 已 settled 后由 009f 写。`(session-settled: user-directed — chosen over GET_LOCK / 系统用户行锁 / 静默 tenant 0)`

### High-Level Technical Design

```text
Employee UI (embed form-body + list/detail)
    -> Controller create/page/get/remaining  (login userId, R11 无 query 墙)
    -> Service (ODKU bpm_oa_quota_lock 同行 X 锁 → 原额度算法 → insert RUNNING, Holder.callBusiness)
         -> ProcessInstanceApi.createProcessInstance
         -> createProcessInstance0: oa_overtime / oa_punch_correction 无 Holder => deny（须在 deleteHistoric 之前）
         -> 回写 processInstanceId 后清 Holder
    -> processCompleted: 两 key 快照 + afterCommit 派发；其它 key 仍 async manager
    -> Listener: event.piId == bill.piId => status; mismatch/rollback 未派发 => 不改占用
HTTP /bpm/process-instance/create 与 RPC /rpc-api/bpm/process-instance/create
    永不置 Holder => oa_overtime / oa_punch_correction 拒绝
```

Quota state: RUNNING + APPROVE occupy; REJECT/CANCEL/withdraw release. U12: 他实例事件不得改变占用。

### Assumptions

- 任职公司选择复用外出 `startCompanyDeptId`。
- 补卡时间的日期可与补卡日期不同；额度只看补卡日期。
- 王鹏在租户 1 有唯一 nickname；实施时查库写入 BPMN param。
- 本期不自动判断周末；`holiday=否` 即周末链，即使落在工作日。
- Product Contract preservation: unchanged（增量 U9–U12 不改 R/AE/KD1–KD7；U12 加 KTD11–KTD12，U9 锁升级加 KTD13）。
- 本地库隔离级别以运行时 `@@transaction_isolation` 为准，YAML 未设置 isolation。

### Sequencing

U1 共享基础（串行）→ U2 加班后端与 U3 补卡后端并行 → U4/U5 BPMN 与 U6/U7 前端分别并行 → U8 目录嵌入（串行收口）→ U9/U10 仅在隔离实例证明后写库；U11 tsc/视觉可与 U12 并行。U12 改 ServiceImpl/Listener/`createProcessInstance0`/`processProcessInstanceCompleted`（两 key afterCommit 分支），必须等 U9 锁行落地与 U10 结束以免抢文件。KTD13 已 settled：009f 写 Main（锁表+两 Service ODKU）与 IT；本写者不抢 test、不猜 release UUID。U10 补验 4/0，原 XML+MI 保留。Maven 空闲。U11 视觉 AE 运行时探测 5666 与 browser provider，不可用则该项 blocked，不得写死永久不可用。共文件单写者。

U1–U8 已落地。增量验收 U9–U11 不改 R/AE。U12 闭合 F1/F2：仅合法业务事务能创建/驱动加班补卡流程。实施交 Grok 4.6。无 U9 隔离实测报告时不得宣称突破或安全；有实测突破则必须报告并最小修复。U11 先修 9 个 tsc；视觉须运行时取证，两轮仍差再升 Astra。HTTP 通用直启未实测利用。

## Implementation Units

### U1. Shared errors, dict, overtime hour helper

Goal: 后续票不再抢 ErrorCodeConstants，时长规则可单测。
Requirements: R2 R3
Files: `yudao-module-bpm-api/.../ErrorCodeConstants.java`；新 helper 与测试在 `yudao-module-bpm-server`；字典 SQL `sql/mysql/bpm_oa_overtime_punch_dict.sql`（是否法定节假日 是/否）。
Approach: 写入加班/补卡错误码 012–019。Helper 输入起止 Instant，输出 hours 或 empty；跨日 empty；<2h empty；否则 min(clock, 8) 一位小数。
Dependencies: none
Test scenarios:
- 10:00–11:00 empty
- 10:00–12:00 = 2.0
- 10:00–23:00 = 8.0
- 10:00–次日 10:00 empty
- 10:00–10:00 empty

### U2. Overtime backend

Goal: 可 API 创建加班单并启动 `oa_overtime`。
Requirements: R1–R5 R8–R12
Files: `bpm_oa_overtime` 表 SQL；DO/Mapper/Service/Controller/Listener；`BpmOAOvertimeServiceTest`。
Approach: 复制外出。变量 `hours`、`holiday`（boolean）、可选 `startCompanyDeptId`。create 校验事由、起止、holiday 必填。KTD13 ODKU 锁行后再按原算法校验 R4。
Dependencies: U1
Test scenarios:
- 合法 2h 插入 RUNNING 并启流，变量 holiday=false
- 日合计将超 8 抛额度错误
- 重叠时间抛错
- 已驳回时长不计入
- 无 query 权限不能看他人单；发起人能看
- 并发双提交用同日 5+5，仅一笔成功（线程或事务测试）

### U3. Punch-correction backend

Goal: 可 API 创建补卡并查询剩余次数。
Requirements: R6–R12
Files: `bpm_oa_punch_correction` 表 SQL；DO/Mapper/Service/Controller/Listener；剩余次数接口；`BpmOAPunchCorrectionServiceTest`。
Approach: 字段 punchDate、punchTime、reason、attachmentUrls。剩余 = 2 - count(RUNNING+APPROVE where punchDate 在该月)。KTD13 ODKU 锁行后再插入。
Dependencies: U1
Test scenarios:
- 剩余 2 时创建成功变 1
- 已 2 次拒绝
- 9 月提交 8 月日期扣 8 月
- 驳回后剩余恢复
- 并发两笔第 2、第 3 次，只有额内成功

### U4. Overtime BPMN and model SQL

Goal: 可导入部署加班流程定义。
Requirements: R5 R10
Files: `sql/mysql/bpmn/oa_overtime.bpmn20.xml`；`sql/mysql/bpm_oa_overtime_model.sql`；加班菜单 SQL（页面+query/create，hr_admin 仅 query）。
Approach: 网关 `${holiday}` true → gm → copy。false → end。抄送用户 ID 用 nickname 王鹏查询写入注释与 param。
Dependencies: U2（流程 key 与变量名对齐）
Test scenarios: XML 含 strategy 38/10/gm 与 holiday 条件；model SQL 幂等且 form_type=20。可用字符串契约测试，不必真部署。

### U5. Punch BPMN and model SQL

Goal: 可导入部署补卡流程。
Requirements: R10
Files: `sql/mysql/bpmn/oa_punch_correction.bpmn20.xml`；model/menu SQL。
Approach: 与外出同构，无网关无抄送。
Dependencies: U3
Test scenarios: 两节点 38 然后 hr 或签；form_type=20。

### U6. Overtime frontend

Goal: 发起/列表/详情可用，字段顺序对齐企微。
Requirements: R1 R2 R5 R10 R11
Files: `ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/overtime/**`；`src/api/bpm/oa/overtime.ts`。不要改 `embed-registry.ts`（U8）。
Approach: 复制 outing 的 create/detail/index + form-body。时长 disabled。holiday 下拉。开始/结束用分开的日期+时刻控件以贴近截图，提交转为 timestamp。
Dependencies: U2
Test scenarios: 手工/浏览器：缺事由不能提交；时长随时间变化；holiday 变更预测节点。单元可测纯计算函数。

### U7. Punch frontend

Goal: 发起页显示剩余次数，字段对齐企微。
Requirements: R6 R7 R8 R10
Files: `views/bpm/oa/punch/**`；`api/bpm/oa/punch.ts`。不改 embed-registry。
Approach: 选补卡日期即请求 remaining。剩余 0 时仍展示但提交由后端拒绝并提示。
Dependencies: U3
Test scenarios: 日期变更刷新剩余；无日期不显示假剩余。

### U8. Catalog, embed registry, contract test

Goal: 假勤目录出现加班/补卡卡片，嵌入表单能打开。
Requirements: R10
Files: `embed-registry.ts`；`sql/mysql/bpm_process_start_catalog.sql`；`BpmOATripOutingMenuContractTest.java`；vben 镜像 SQL 若仓库有同步要求。
Approach: CASE 增加 oa_overtime/oa_punch_correction → attendance。registry 增加两 key。更新契约测试断言。
Dependencies: U4 U5 U6 U7
Test scenarios: 契约测试通过；键名与外出一致的懒加载写法。

### U9. Real InnoDB quota atomicity and rollback evidence

Goal: 在 KTD9 可丢弃实例上证明 R9；RC 空范围已突破后落地 KTD13 锁行。无该实例则只读 blocked。
Requirements: R8 R9 AE3 AE4 AE6
Files（009f 写 Main，本写者不抢 IT）：
- 窄 DDL `sql/mysql/bpm_oa_quota_lock.sql`（及 IT schema 同步）：表 `bpm_oa_quota_lock`，唯一/主键 `(tenant_id, user_id, quota_type, period)`。
- 最小 DO/Mapper。
- `BpmOAOvertimeServiceImpl` / `BpmOAPunchCorrectionServiceImpl`：同 TX、额度查询前 ODKU 锁行，然后原算法。
- `BpmOAQuotaInnoDbIT` 仅 009f 改；红 probe 保留作 baseline。
Approach:
1. 取证（已完成）：RC 双空读 20/20 突破>8h；RR 20/20 deadlock。红 probe 可留，绿用例**不得** SELECT 后再 barrier。
2. 锁行：`OVERTIME_DAY`+`YYYY-MM-DD`；`PUNCH_MONTH`+`YYYY-MM`。`INSERT ... ON DUPLICATE KEY UPDATE`（no-op 也应拿 X 锁；勿写「必须改列才锁」）。`touch_seq` 可选、非业务必要。
3. `tenant_id` = 当前 `TenantContext`，生产禁止静默 0；IT 显式上下文或同 tenant 配置。DO 继承 `TenantBaseDO`（或等价让拦截器对 `bpm_oa_quota_lock` 生效），禁止无 TableInfo 裸 JDBC。
4. 不用 GET_LOCK / 系统用户行锁。提交/回滚释放。
5. 发布 SQL 由父记真实 UUID。Main 仅本 KTD settled 后由 009f 写。
Dependencies: U2 U3
Test scenarios（009f IT，绿）：
- RR 与 RC 各 20 次加班空日 5h+5h：恰好 1 成功 + 1 额度错误，**非 deadlock**。
- 补卡空月：2 并发都成功；3 并发最多 2 成功。
- 跨用户、跨租户不互锁/不串数据。
- 回滚释放后可再申请。
- AE6 顺序第 3 失败 remaining=0；REJECT/CANCEL/WITHDRAW 释放。
- 启流抛错同事务业务行与锁行回滚。

### U10. Approval-chain local integration and Wang Peng seed fail-closed

Goal: 本机验证 13-1/13-2 审批链；王鹏种子缺失/非唯一/id 不符/陈旧会话变量失败关闭。
Requirements: R5 R10 AE7
Files: `sql/mysql/bpm_oa_overtime_model.sql`（种子失败关闭的唯一授权修复）；只读两 BPMN XML；允许新增嵌入式 Flowable IT（不必全量 app）。字符串契约不能替代 AE7 链实际运行。
Approach:
1. 禁写既有 48080，禁 `application-local.yaml`（其 JDBC 为 33061、Redis 6379）。优先测试内嵌 Flowable + U9 可丢弃 MySQL（13306/33062）。仅当进程数据源指向该可丢弃库且 Redis 不是 6379 时才允许空闲口 48081。否则只读 XML 并 blocked。目标是节点链实际跑起来，不是只 assert 字符串。王鹏失败关闭仍只改该 model SQL。
2. `holiday=true` 出现分管总与抄送王鹏；`false` 无这两节点。补卡仅部门负责人→人事或签。
3. 王鹏：先 `SET @oa_overtime_wangpeng_id = NULL`。无行不得沿用连接内旧 221；多行失败；id≠221 失败。不得把无行 `SELECT INTO` 注释成必定 ERROR 1329。修复只改该 SQL。勿先断言已在库中复现。
Dependencies: U4 U5
Test scenarios:
- Covers AE7. 本机 holiday 真/假路径节点与抄送符合 R5。
- 补卡 BPMN 无网关无抄送。
- 王鹏缺失：变量先 NULL，脚本失败关闭，不成功更新 form_type。
- 王鹏 nickname 非唯一：失败关闭。
- 王鹏 id≠221：失败关闭。
- 陈旧会话：先把变量设为 221 且查不到用户，脚本仍失败。

### U11. Latest local UI browser acceptance

Goal: 在最新本地页验证假勤卡片与加班/补卡发起（含补卡剩余次数）；清掉本功能 vue-tsc 错误；有可用浏览器才签 AE 视觉。
Requirements: R1 R6 R10 AE1 AE2 AE5 AE8
Files: `ruoyi-office-vben/apps/web-antd/src/views/bpm/oa/overtime/index.vue`；`overtime/modules/form-body.vue`；`punch/index.vue`（单写者）。不修 trip/outing/WMS 等既有 tsc。
Approach:
1. `/tmp/u8-vue-tsc.txt` 973 错中本功能 9 错不得归入既有：`overtime/index.vue:161/167/175/182` TS2345 `Record<string,any>` 缺 `reason/startTime/endTime`；`form-body.vue:269` TS2322 字典选项与 Select 不兼容；`punch/index.vue:161/167/175/182` TS2345 缺 `punchDate/punchTime/reason`。
2. 9 个本功能 tsc 可先修。视觉 AE 运行时探测 5666 与 browser provider；父 native 曾成功登录不代替本次执行时取证。当时不可用则该项 blocked，恢复后重测。HTTP 200 不能替代 AE。不得向 33061/既有 48080 写成功单据；可用前端时长计算、GET remaining、被拒绝提交覆盖所列 AE。
3. 剩余 0 仍展示、提交由后端拒绝。不远程部署。不覆盖用户脏文件。
Execution note: 先修这 9 个 tsc；视觉门控依赖可用浏览器 provider。HTTP 200 不能替代 AE 点击验收。
Dependencies: U6 U7 U8
Test scenarios:
- 本功能 9 个 vue-tsc 清零；全仓 973 不要求清零。
- Covers AE1 AE2. 加班 10:00–11:00 不能提交；10:00–23:00 展示 8（须浏览器可用）。
- Covers AE5 AE8. 选 8 月日期显示该月剩余；无日期不显示假剩余。
- 假勤目录可打开加班/补卡嵌入表单。

### U12. OA attendance start context and callback instance bind

Goal: 仅合法业务事务能创建/驱动加班与补卡流程。闭合 F1（通用 create 绕过额度）与 F2（回调只信 businessKey）。HTTP 未实测利用，测试用直接调用/单测证明拒绝，不得写成已利用。

Requirements: R8 R9 R11（保留登录即可走业务 create；额度只被匹配实例的合法状态改变）

Files:
- 新 Holder（建议 `yudao-module-bpm-server/.../framework/security/OaAttendanceBusinessStartHolder.java`），禁止改 `BpmBusinessStartChannelHolder` / `BpmBusinessStartIdentityFilter` / `createProcessInstanceByBusiness`。
- `BpmProcessInstanceServiceImpl.createProcessInstance0`（key 守卫，约 L1088–1147）。
- `BpmProcessInstanceServiceImpl.processProcessInstanceCompleted`（约 L1396–1447）：**仅两 key** 快照 + `afterCommit` 派发；其它 key 仍 `sendProcessStatusNotification`。
- `BpmOAOvertimeServiceImpl` / `BpmOAPunchCorrectionServiceImpl`：Holder 包住 `createProcessInstance` + 回写 `processInstanceId`；`update*Status` 按实例绑定（等 U9 锁行与 U10 完成后再改这两文件）。
- `BpmOAOvertimeStatusListener` / `BpmOAPunchCorrectionStatusListener`：传入 event processInstanceId。
- 测试：`BpmOAOvertimeServiceTest` / `BpmOAPunchCorrectionServiceTest` 增回调用例；新 `OaAttendanceBusinessStartGuardTest`（或等价）覆盖通用 VO/DTO 路径。不改 U11 前端。

Approach:
1. Holder：ThreadLocal + `callBusiness(Callable)` finally 清理；无线路字段、无 HMAC。仅加班/补卡 `create*` 在 `@Transactional` 内 **先额度锁+insert 业务行**，再包住 `processInstanceApi.createProcessInstance`，回写 piId 后结束。语义是「业务行+额度事务先行」，不是业务 create 权限点。
2. `createProcessInstance0`：definition/挂起校验之后、**`deleteHistoricalProcessInstancesByBusinessKey` 与 `processInstanceBuilder.start()` 之前**，若 key 为 `oa_overtime`/`oa_punch_correction` 且 Holder 未置位 → 拒绝。复用已有 `PROCESS_INSTANCE_START_USER_CAN_START` 或 `PROCESS_INSTANCE_START_PERMISSION_DENIED`，不新增 012–019 段码。其它 key 不进该分支。**禁止修改** `BpmProcessStartEligibilityServiceImpl`。禁止恢复全局 old `trustedBusinessStart`。Finance/`createProcessInstanceByBusiness` 不置本 Holder，两 key 同样拒绝，其它 key 现行为不变。
3. 回调：不得用事件反写 piId。单据不存在 / businessKey 非法 / 已绑定但 piId 不同 → no-op。已绑定且相等 → 更新 status。
4. **审批事务 P1：** bind 之后若仍用 manager 默认 `runAsync`，APPROVE 可在审批 TX commit 前改占用，随后 rollback 造成单据 APPROVE、引擎仍 RUNNING。create 回写后 reconcile **只**补启流即时终态，不能修后续审批回滚。闭合方式：`processProcessInstanceCompleted` 对两 key 先构建脱离 Flowable 的消息快照（id/key/status/businessKey/tenantId），`afterCommit` 内 **`PROPAGATION_REQUIRES_NEW`（或 `TransactionTemplate`）新开写事务**，再同步 Local handler（`TenantUtils.execute`+快照 tenant）。原 TX 已 commit，同步≠已持久化。禁止 afterCommit 再进 `sendProcessStatusNotification`/`runAsync`。rollback 不派发。其它 key 原路径。禁止用全局关 async 当闭合。
5. 范围锁：不碰 `/rpc-api/**` permitAll；不给业务 create 加 `@PreAuthorize`；不改 catalog；不改全局 `yudao.bmp.notification.async`。

Dependencies: U9 U10 完成（ServiceImpl 单写者）。U11 并行。U2 U3 已落地。

Test scenarios:
- 业务 `createOvertime`/`createPunchCorrection` 仍成功启流并回写 piId（mock Api 即可）。
- `createProcessInstance(userId, CreateReqVO)` 对加班/补卡 definition **无** Holder → 拒绝；不 insert 业务行。
- `createProcessInstance(userId, CreateReqDTO)` key=`oa_overtime`|`oa_punch_correction` 无 Holder → 拒绝（含自选 userId+businessKey），**且不删除**该 businessKey 的历史流程实例。
- `createProcessInstanceByBusiness` 对这两 key 仍拒绝（不得借 Finance 信任放行加班补卡）。
- **无关 key 回归（必做）：** 无本 Holder 时至少 1 个非本两 key（`finance_salary_payment_apply` 或 `finance_payment_apply` 或 `oa_outing`）走通用 create **不被** 该分支拒绝；`evaluate`/`shouldHideFromStartList` 对加班补卡仍为可见/可展示（不测成隐藏）。
- **(a) 合法终态不丢：** 审批 TX **commit 后** afterCommit + **REQUIRES_NEW 内层写事务提交后**，再断言库行占用已更新（不得只断言内存/同步返回）。
- **(a2) 审批 rollback 不传播终态：** 模拟 `processProcessInstanceCompleted` 所在 TX rollback，占用保持 RUNNING。
- **(b) 错实例不篡改：** 已绑定 piId=A，事件 piId=B，status/额度不变。
- **(c) 启流同事务回滚：** start 失败后无业务行，监听不改额度。
- 其它 key 仍可走原 async manager（回归：至少 1 个非两 key 不注册 attendance afterCommit）。
- 不覆盖/不改 U9 IT；不要求浏览器。

## Verification Contract

- JDK17：`scripts/local/build-backend-jdk17.sh` 或 `mvn -pl yudao-module-bpm/yudao-module-bpm-server -am -Dtest=OaOvertimeHoursTest,BpmOAOvertimeServiceTest,BpmOAPunchCorrectionServiceTest,BpmOATripOutingMenuContractTest`。U12 补上守卫测试类名。缺 `-am` 的 api 符号失败不得记为既有 lombok。
- 前端 pnpm 10.28.2。`5666`/`48080` 为已有服务口，不等于隔离验收环境。5666 与 browser provider 以执行时探测为准。
- 浏览器 AE：运行时不可用则该项 blocked，恢复后重测；9 个本功能 tsc 可先修。
- U9/U10 只允许在 13306/33062 可丢弃实例（及符合条件的内嵌 Flowable）写库；禁止写任何既有库含 33061/48080/6379。Mock `ReentrantLock` 不得当作完成证据。无实测报告不得宣称突破或安全。
- 不覆盖用户脏文件。不直接本机 JDK21 `mvn`。不远程部署。

## Definition of Done

- U1–U8 完成且对应测试绿。
- U9–U11：无 13306/33062 可丢弃实例则 U9/U10 只读 blocked 不算完成；U11 tsc 9 错可完成；视觉 AE 以执行时探测为准。无 U9 隔离实测报告不得宣称突破或安全；有突破则必须报告并最小修锁。
- U12：**计划实施 ready**（KTD11–K12 含 afterCommit+REQUIRES_NEW 库行断言）。实现等 U9 独占两 Service 完成后交独立实现者。通用 VO/DTO 与 ByBusiness 对两 key 拒绝；业务 create 仍可启流；两 key 终态仅 afterCommit 内 REQUIRES_NEW 提交后改占用；审批 rollback 不传播终态。未跑 HTTP 利用不得声称已利用。不改 Main。
- 发布单记录：表 SQL、字典 SQL、菜单 SQL、**KTD13 额度锁行窄 DDL**、BPMN 需设计器导入部署后再跑 model SQL、王鹏 userId 解析。本机验收不替代共享库执行。
- 未做考勤同步与调休汇总。
- 无遗弃调试代码。
- 无 U9 隔离实测报告时不得宣称已证实突破或已证实安全；有该报告的突破则可报告。
