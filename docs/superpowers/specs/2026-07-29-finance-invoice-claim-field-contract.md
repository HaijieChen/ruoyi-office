# 开票申请 / 认领改挂 — 字段契约（T1）

> 发布物。前后端、BPM 摘要变量与库列对照。购方/税项为**提交时快照**，不随客户档案回写。  
> DDL：`sql/mysql/finance_invoice_claim_phase2a.sql`  
> 决策：`invoice-claim-redesign-decisions` D2/D9；Tech `tech-plan-invoice-claim-r1` §4–5、D-T5/D-T6

---

## 1. XOR 与认领来源（应用层强制）

| 规则 | 说明 |
|------|------|
| 明细 XOR | `finance_receipt_claim_item` 上 **`invoice_application_id` XOR `business_order_id`**（恰有一个非空） |
| 新写 | `claim_source = INVOICE` 且仅写 `invoice_application_id` |
| 存量 | 迁移回填 `claim_source = LEGACY_BO`，`invoice_application_id = NULL`，保留 `business_order_id` |
| LEGACY 写 | 一切写（含 revoke）拒绝（T5；本契约只定字段） |
| DB CHECK | 一期**不**强制；可选 follow-up |

```text
claim_item
  INVOICE   : invoice_application_id 非空  AND  business_order_id IS NULL
  LEGACY_BO : business_order_id 非空       AND  invoice_application_id IS NULL
```

---

## 2. 开票申请主表 `finance_invoice_application`

| form schema key（建议） | 命令 DTO 字段（建议） | 列 | BPM 摘要变量（建议） | 备注 |
|-------------------------|----------------------|----|----------------------|------|
| _(system)_ | — | `id` | `businessKey` / `invoiceApplicationId` | 流程 businessKey = appId |
| _(system)_ | — | `application_no` | `applicationNo` | 单号 |
| _(system)_ | — | `process_instance_id` | — | 最新流程实例 |
| _(system)_ | — | `approval_status` | `approvalStatus` | PENDING/APPROVED/REJECTED/CANCELLED |
| _(system)_ | — | `issue_status` | `issueStatus` | 0/1/2；**不进 claimAllowed** |
| totalAmount / amountInclTax | `totalAmount` | `total_amount` | `totalAmount` | 价税合计 |
| _(system)_ | — | `confirmed_claimed_amount` | — | 认领确认累加 |
| _(system)_ | — | `pending_claimed_amount` | — | 待确认占用 |
| applicant / userId | `applicantUserId` | `applicant_user_id` | `applicantUserId` | 权限谓词 |
| expectedInvoiceDate | `expectedInvoiceDate` | `expected_invoice_date` | `expectedInvoiceDate` | 期望开票日 |
| invoiceCompany | `invoiceCompany` | `invoice_company` | `invoiceCompany` | 表头快照 |
| invoiceType | `invoiceType` | `invoice_type` | `invoiceType` | 普票/专票 |
| buyerName | `buyerName` | `buyer_name` | `buyerName` | **提交快照** |
| buyerTaxNo | `buyerTaxNo` | `buyer_tax_no` | `buyerTaxNo` | **提交快照** |
| buyerAddressPhone | `buyerAddressPhone` | `buyer_address_phone` | `buyerAddressPhone` | **提交快照** |
| buyerBankAccount | `buyerBankAccount` | `buyer_bank_account` | `buyerBankAccount` | **提交快照** |
| specialInvoiceRequirement | `specialInvoiceRequirement` | `special_invoice_requirement` | — | 可选 |
| taxContent | `taxContent` | `tax_content` | `taxContent` | **提交快照** |
| taxRate | `taxRate` | `tax_rate` | `taxRate` | **提交快照** |
| amountExcludingTax | `amountExcludingTax` | `amount_excluding_tax` | — | 可由价税反算 |
| taxAmount | `taxAmount` | `tax_amount` | — | 可由价税反算 |
| evidenceFileUrl | `evidenceFileUrl` | `evidence_file_url` | — | 开票依据 |
| remark / specialNote | `remark` | `remark` | — | 特殊情况说明 |
| _(system)_ | — | `voided` | `voided` | 作废 |

命令入口（T2，本契约仅命名）：

- `createAndStart(FinanceInvoiceApplicationCreateAndStartReqVO)`
- `resubmit(id, FinanceInvoiceApplicationResubmitReqVO)`
- `onApprovalOutcome(appId, outcome)` — 无 form key
- `updateIssueProgress` — 写 line 票号/附件与表头 `issue_status`

---

## 3. 开票申请明细 `finance_invoice_application_line`

| form schema key（建议） | 命令 DTO 字段 | 列 | BPM 摘要 | 备注 |
|-------------------------|---------------|----|----------|------|
| lines[].businessOrderId | `lines[].businessOrderId` | `business_order_id` | — | 必填；占 BO |
| lines[].amount | `lines[].amount` | `amount` | — | 本行金额 |
| lines[].invoiceCompany | `lines[].invoiceCompany` | `invoice_company` | — | 行快照 |
| lines[].invoiceType | `lines[].invoiceType` | `invoice_type` | — | 行快照 |
| lines[].billingPeriod | `lines[].billingPeriod` | `billing_period` | — | YYYY-MM |
| _(system)_ | — | `issue_status` | — | 0 未开 / 1 已开 |
| _(办票 API)_ | `invoiceNo` | `invoice_no` | — | **一行一票** |
| _(办票 API)_ | `fileUrl` | `file_url` | — | 发票附件 |
| _(办票 API)_ | `issuedAt` | `issued_at` | — | 开票时间 |
| lines[].sort | `lines[].sort` | `sort` | — | 行序 |
| _(system)_ | — | `application_id` | — | FK 逻辑 |

一期约束（D2/D-T6）：一行明细最多一张物理票；禁止一行多票 / 一票多行实体。

---

## 4. 商务单 / 到款扩展列

| 表 | 列 | 默认 | 口径 |
|----|----|------|------|
| `finance_business_order` | `invoiced_occupied_amount` | 0 | 可开 = 结算 − 占用；提交即占 |
| `finance_bank_receipt` | `pending_claimed_amount` | 0 | 可认 = transaction − claimed − pending |

---

## 5. 认领明细扩展 `finance_receipt_claim_item`

| form / API | DTO | 列 | 说明 |
|------------|-----|----|------|
| items[].invoiceApplicationId | `invoiceApplicationId` | `invoice_application_id` | 可空；INVOICE |
| items[].businessOrderId | `businessOrderId` | `business_order_id` | 可空；LEGACY_BO |
| items[].claimSource | `claimSource` | `claim_source` | `INVOICE` \| `LEGACY_BO` |
| items[].receiptId | `receiptId` | `receipt_id` | 不变 |
| items[].claimAmount | `claimAmount` | `claim_amount` | 不变 |

---

## 6. 金额与 claimAllowed（投影，非列）

| 规则 | 公式 |
|------|------|
| 商务单可开 | `settlement_amount − invoiced_occupied_amount` |
| 开票可认领 | `total_amount − confirmed_claimed_amount − pending_claimed_amount` |
| 到款可认领 | `transaction_amount − claimed_amount − pending_claimed_amount` |
| claimAllowed | `approval_status = APPROVED && !voided && 开票可认领 > 0`（**不读** `issue_status`） |

---

## 7. 枚举速查

| 域 | 取值 |
|----|------|
| `approval_status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `issue_status`（表头） | `0` 未开, `1` 部分, `2` 全部 |
| `issue_status`（行） | `0` 未开, `1` 已开 |
| `claim_source` | `INVOICE`, `LEGACY_BO` |

Java：`FinanceInvoiceApprovalStatusEnum` / `FinanceInvoiceIssueStatusEnum` / `FinanceReceiptClaimSourceEnum`。

---

## 8. 非目标（本契约不覆盖）

草稿表；红冲；物理票多对多；DB CHECK XOR；完整 createAndStart / 认领 CAS 业务实现（T2+）。
