# 开票+认领改挂 — 发布清单（T6）

## 1. 数据库

- [ ] 执行 `sql/mysql/finance_invoice_claim_phase2a.sql`（表/列/LEGACY 回填）
- [ ] 执行 `sql/mysql/finance_invoice_application_menu_phase2b.sql`（菜单与角色）
- [ ] 确认 `finance_receipt_claim_item.claim_source` 存量均为 `LEGACY_BO`

## 2. BPM

- [ ] 设计并发布流程定义 key = **`finance_invoice_apply`**（仅审批节点，通过/驳回即结束）
- [ ] 在结束路径挂载同步 Delegate：`${financeInvoiceApprovalOutcomeDelegate}`
- [ ] 确认 businessKey = 开票申请 id
- [ ] 确认 PROCESS_STATUS 在 Delegate 执行前已写入
- [ ] 禁止依赖异步通知作为唯一落账路径（见 `2026-07-29-finance-invoice-bpm-delegate.md`）

## 3. 表单

- [ ] form-create / 业务表单字段与 `2026-07-29-finance-invoice-claim-field-contract.md` 对齐
- [ ] 发起入口仅调 `createAndStart` / `resubmit`（无草稿）

## 4. 前端

- [ ] 菜单可见：开票申请、到款认领（认领第二列=开票申请）
- [ ] 无草稿按钮
- [ ] 历史 LEGACY 认领：修改/撤销禁用

## 5. 角色 SoD

| 角色 | 开票 | 认领 |
|------|------|------|
| business_staff | create/query/resubmit | create/update/resubmit/query |
| finance_admin | query/issue/update | review/confirm/reject/revoke |

## 6. 冒烟

- [ ] createAndStart → 流程运行 → Delegate APPROVED → 可认领
- [ ] 未出票即可选中认领
- [ ] 驳回释占 → resubmit 再占新流程
- [ ] LEGACY revoke 业务错误
