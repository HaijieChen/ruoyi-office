---
title: Expense invoice OCR and no-invoice process
type: feat
date: 2026-08-19
origin: docs/superpowers/specs/2026-08-19-expense-invoice-ocr-and-no-invoice-design.md
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: origin-spec
execution: code
---

# Expense invoice OCR and no-invoice process

## Goal Capsule

- Objective: 有票报销每行一张发票并可 OCR 预填；差旅/交通强制挂出差或外出；新增仅部分人可发的无票报销。
- Means: 一张台账两条 processKey；OCR 在 10.20.32.1；OA 只读 yaml `yudao.finance.invoice-ocr.base-url`。
- Authority: origin spec. Keys: `oa_expense_reimbursement`, `oa_expense_no_invoice`.
- Stop: 不验真、不查重、不改审批链、不在 OA 进程嵌 Paddle、不改模型页「按角色发起」。

## Product Contract

- R1. Header 增加 `invoice_mode` (WITH_INVOICE|NO_INVOICE)、`process_key`。有票 create 强制 WITH_INVOICE；无票 create 强制 NO_INVOICE。请求体 mode 与入口不一致则 400。
- R2. Line 增加 `invoice_file_url`、`predoc_type` (TRIP|OUTING|null)、`predoc_process_instance_id`。有票非代票每行必须一张可识别 URL（沿用付款 `isAcceptableFileUrl`）。无票/代票发票必须空。
- R3. 分类 `travel`：必须 TRIP + 本人发起且 `status=BpmTaskStatusEnum.APPROVE` 的 `oa_business_trip`。`transport`：TRIP 或 OUTING，本人已通过出差或外出。其它分类禁止前置。无票同样执行。
- R4. `POST /finance/expense-reimbursement/ocr-invoice` 登录即可。后端读 yaml `yudao.finance.invoice-ocr.base-url` + `timeout-ms`，转发 10.20.32.1 `POST /ocr/invoice`。失败/超时/未配置返回空字段不抛业务失败。浏览器不直连 10.20.32.1。
- R5. 无票 processKey `oa_expense_no_invoice`，审批 BPMN 与有票同构。发起：模型指定用户；可选权限 `finance:expense-no-invoice:create` 与名单并集。不进 BpmEmbedProcessStartPermissionRegistry（与现有报销一致，靠模型 startUserIds）。
- R6. 列表/详情同一套；`invoiceMode` 展示。余额表仍只认 PAID，不区分有票无票。
- R7. PaddleOCR CPU 容器部署在 10.20.32.1:8099。test yaml 写 `http://10.20.32.1:8099`。

## File map

- SQL: `sql/mysql/finance_expense_invoice_ocr.sql`（ALTER 列）；`sql/mysql/bpmn/oa_expense_no_invoice.bpmn20.xml`；`sql/mysql/finance_expense_no_invoice_model.sql`
- Config: `yudao-server/src/main/resources/application-dev.yaml`、`application-test.yaml` → `yudao.finance.invoice-ocr`
- Java: `FinanceInvoiceOcrProperties`；`FinanceInvoiceOcrClient`；expense create 分入口或 `processKey` 参数；line VO 新字段；`FinanceExpensePredocService`（查出差/外出已通过）
- FE: 有票 form-body 加上传+OCR+前置选择；`no-invoice-form-body.vue`；embed-registry
- Ops: `deploy/ocr/paddleocr/docker-compose.yml` + README（10.20.32.1）

## Implementation Units

### U1. Schema + error codes

- Parallel with: U4, U7.
- Files: `sql/mysql/finance_expense_invoice_ocr.sql`; ErrorCodeConstants 新码（发票必填/禁止、前置无效、mode 不一致）。
- Test: 无（纯 DDL）。Test expectation: none -- DDL.
- Execution note: 幂等 ALTER（信息_schema 判断列是否存在）。

### U2. OCR yaml + backend proxy

- Depends on: 无代码依赖 U1。
- Files: Properties + Client + `POST /finance/expense-reimbursement/ocr-invoice`；application-dev.yaml / application-test.yaml `base-url: http://10.20.32.1:8099` `timeout-ms: 8000`。
- Test:
  - Happy: mock sidecar 返回 date=2026-08-01 amount=12.50 → API 原样。
  - Edge: base-url 空 → 200 且 date/amount 空。
  - Error: sidecar 超时 → 200 空字段，不 500。

### U3. Create 校验（mode / 发票 / 前置）

- Depends on: U1.
- Files: `FinanceExpenseReimbursementServiceImpl#create` 按入口写 mode/processKey；`FinanceExpensePredocService` 查 trip/outing mapper（本人 + status=2）。
- Test:
  - Happy: 有票非代票一行一票 + 差旅挂已通过出差 → insert。
  - Edge: 交通挂已通过外出 → 通过。
  - Error: 缺票 / 无票带票 / 差旅无前置 / 交通无前置 / 招待带前置 / mode 与入口不一致 → 对应错误码。

### U4. 无票 BPMN + model SQL

- Parallel with: U1.
- Files: 复制有票 BPMN，process id/name 改为 `oa_expense_no_invoice` / 无票费用报销；create/view path 仍 `/finance/expense-reimbursement/create|detail` 或 `/no-invoice/create`（推荐独立 path 以免表单混用）：`/finance/expense-reimbursement/no-invoice-create`。
- Test: 无。Test expectation: none -- 设计师导入。

### U5. 有票前端：发票 + OCR + 前置

- Depends on: U2, U3.
- Files: `form-body.vue`；API ocr-invoice；差旅/交通选择器调 trip/outing 已通过列表。
- Test: 手工。Test expectation: none -- Vue 无现成单测栈。
- 行为：上传后调 OCR 预填；人可改；提交走现有 create。

### U6. 无票前端 + 注册

- Depends on: U3, U4.
- Files: `no-invoice-form-body.vue`；embed-registry `oa_expense_no_invoice`；无票 create 页。
- Test: 无。Test expectation: none -- Vue。

### U7. 10.20.32.1 部署 PaddleOCR

- Parallel with: U1–U6.
- Files: `deploy/ocr/paddleocr/docker-compose.yml`（CPU 镜像，8099）；README 启动命令。
- 执行：SSH 到 10.20.32.1 拉起容器；curl `/ocr/invoice` 探活。
- Test: 部署后对一张样票返回非空 rawText 或结构化字段。

## Parallelism

U1 ∥ U4 ∥ U7。U2 ∥ U3 after U1 列存在（U2 可不改表）。U5 after U2+U3。U6 after U3+U4。ce-work 可按 ticket 领取。

## Review (inline, not independent)

- Completeness: R1–R7 均有 unit。
- Consistency: 无票独立 path，避免有票 form 被无票 key 打开。
- Scope: 无验真；模型页不加角色。
- Test gaps: U5/U6 无自动测，靠 U3 守门。
- Ops risk: 10.20.32.1 网络与镜像需执行时验证，失败则 OCR 降级手填，不挡发布。
