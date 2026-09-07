# 审批业务表单（form_type=20）详情读权覆盖矩阵

Base: `78a0916dbc81acb1f133fb55042503ab22b833d8`（78a0916 → db472 → 065）  
Worktree: `.worktrees/codex-approver-form-read-access` (`codex/approver-form-read-access`)  
不发布。

规则：当前 assignee / owner / 引擎真实候选、历史已审批人（含流程结束）可读**该流程绑定**的表单与附件。不得因缺少模块 `*:query` 或 SELF 数据范围拒绝。不扩大列表、写权限、跨租户。关系只从业务行 `processInstanceId`/`businessKey` 证实，不信任客户端 `processId`。抄送既有规则保留（现实现：抄送人不是读者）。

共用能力（已有，请假/出差/外出/财务详情）：`OaBillAccessPermission` / `FinanceProcessParticipantSupport`（active `taskCandidateOrAssigned` ∪ historic `taskAssignee` ∪ 发起人）。OA/HRM 模块只依赖 `bpm-api`，本修复把同一规则暴露为 `BpmProcessParticipantApi`，避免 oa-server 依赖 bpm-server。

## 本工作树修改范围

OA / BPM-OA / HRM。合同不改。财务其他表单不抢（仅登记现状）。加班/补卡/IM/节假日不引入。

| 模块 | processKey | 详情 GET | 附件/子表 | 现状（942） | 本任务 |
|---|---|---|---|---|---|
| 用印 | `oa_seal_apply_bill` | `GET /oa/seal-apply-bill/get?id=` | 详情内嵌附件 | 代码已：登录 + 数据范围 query / 参与人 ignore。单测：方法安全、SELF 越权拒绝、H2+Flowable 当前/历史。**隔离浏览器未用本修复 jar（旧 iso 包仍 4b1/065）** | 代码已改；浏览器实测未过门禁 |
| 用车 | `oa_car_apply_bill` | `GET /oa/car-apply-bill/get` | 详情内嵌附件 | 代码已与用印同一 helper。无独立单测 | 代码已改；未隔离实测 |
| 还车 | `oa_car_return_bill` | `GET /oa/car-return-bill/get` | 同 | 同上 | 代码已改；未隔离实测 |
| 会议室预定 | `oa_meeting_room_booking` | `GET /oa/meeting-room-booking/get` | 详情内嵌附件 | 同上 | 代码已改；未隔离实测 |
| 请假 | `oa_leave` | `GET /bpm/oa/leave/get` | 无 | GET `isAuthenticated` + `canReadOaBill`（含 historic/task owner） | 静态符合；未本轮隔离 |
| 出差 | `oa_business_trip` | `GET /bpm/oa/trip/get` | 无 | 同上 | 静态符合 |
| 外出 | `oa_outing` | `GET /bpm/oa/outing/get` | 无 | 同上 | 静态符合 |
| 入职 | `hr_employee_entry_bill` | `GET /hrm/employee-entry-bill/get` | download 已改为登录+同一读权 | 代码已改 | 未隔离实测 |
| 转正 | `hr_employee_regular_bill` | `GET /hrm/employee-regular-bill/get` | 详情附件 | 代码已改 | 未隔离实测 |
| 调动 | `hr_employee_transfer_bill` | `GET /hrm/employee-transfer-bill/get` | 同 | 代码已改 | 未隔离实测 |
| 离职 | `hr_employee_resignation_bill` | `GET /hrm/employee-resignation-bill/get` | 同 | 代码已改 | 未隔离实测 |

印章主数据 `GET /oa/seal/get` 不是审批单据，保持 query。用印详情不要求会管权限。

## 不在本工作树改（登记）

| 模块 | processKey | 详情 GET | 现状 | 谁 |
|---|---|---|---|---|
| 合同 | `finance_contract_sign` | `GET /finance/contract-application/get` | `isAuthenticated` + `canReadBill` | 另一协调者 |
| 付款 | `finance_payment_apply` | `GET /finance/payment-application/get` | query **或** `canTaskContextOrOwnerRead`（service 含 historic） | 财务另协；保留 942 空值热修 |
| 薪资付款 | `finance_salary_payment_apply` | salary GET | 财务 | 另协 |
| 税金付款 | `finance_tax_payment_apply` | tax GET | 财务 | 另协 |
| 开票 | `finance_invoice_apply` | invoice GET | query **或** `financeInvoiceAccess.canTaskContextOrOwnerRead`（065 DP disable） | **静态符合 065**；未改 |
| 红冲 | `finance_invoice_redflush_apply` | `GET /finance/invoice-redflush/get` | 登录 + query 或 `canReadBill`（含 task owner）。单测：注解 + 原红冲服务 7 | 代码已改；隔离浏览器未跑本 jar |
| 报销 | `oa_expense_reimbursement` | 登录 + `assertCanRead` + 065 `canQueryAll` | **静态符合**；保留 manageAll 语义 | 未强改 |
| 无票报销 | `oa_expense_no_invoice` | 同上 | 同上 | 未强改 |
| 付款/薪/税 | payment/salary/tax GET | query **或** `financePaymentAccess.canAccessDetail`；manageAll=`*:update` | **静态符合 065/db472**；未改写入口 | 未强改 |

`CREATE_SHELL_EMBED_REGISTRY` 与上表 processKey 对齐（用印已注册 `oa_seal_apply_bill`）。

## form_type=10 流程表单（BPM 通用）

生产还有 `form_type=10`（`BpmModelFormTypeEnum.NORMAL`）：字段存在流程变量 / `bpm_form`，详情入口是 `GET /bpm/process-instance/get-approval-detail`。

- **有** `processInstanceId`：登录即可，服务端 `assertCanViewDetail`（发起人 / 有效分享 / 抄送 / 当前候选或办理 / 历史办理）。
- **无** `processInstanceId`（定义预览）：必须 `bpm:process-instance:query`。不得用参与关系放行空 pid。

通用 BPM 明确保留抄送可读流程详情；业务单据（用印等）抄送人仍不是单据读者，除非另有分享。

## 白盒用例（用印先写，同类复用）

正例

- S1 当前 assignee，无 `oa:seal-apply-bill:query`，SELF：`GET ?id=` 200，表单字段+附件列表非空策略与发起人一致。
- S2 当前 candidate（未签收），无 query：200。
- S3 历史 assignee（流程已结束，对应截图刘鹤），无 query，SELF：200。processInstanceId 只取库行。
- S4 发起人/creator，无 query：200。
- S5 持有 query 且数据范围能看到该行：200。

反例

- N1 无关用户，无 query：拒绝（非菜单 403 掩盖）。
- N2 无关用户猜 `processInstanceId`：GET 只收 `id`，服务不读客户端 processId。
- N3 跨租户：租户上下文隔离，不可读。
- N4 `GET /page` 仍要 query，且现实现强制 `creator=当前用户`，不因本修复变全量列表。
- N5 create/update/delete/export 权限不变。
- N6 抄送人（非办理人）保持不可读（与请假一致）。

列表不扩大：有 query 的人列表仍是自己的单；不能靠本修复扫到全部用印单。

## 用印缺口（白盒）

1. `SealApplyBillController.get` `@PreAuthorize oa:seal-apply-bill:query` → 已审批人先被 Spring Security 挡成「缺少 oa:seal-apply-bill:query」，表单空白。
2. `getSealApplyBillInfo` 无参与关系校验；若只摘 query 注解，任意登录者可按 id 读。
3. SELF 数据范围可能让 `selectById` 对非创建人返回空；参与者路径需 `executeIgnore` 后再用库内 `processInstanceId` 做资格判断。
4. 附件随详情返回；独立 `/common/attachment/*` 仍要 `common:attachment:query`（二次缺口，用印页若只走详情内嵌则本步可过）。

不采用：全局关 DataPermission、给角色加 `oa:seal-apply-bill:query`、信任前端 processId。
