# Finance form dictionary and approval field Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the finance payment, receipt, contract-signing, and invoice forms with the latest `下拉框选项.xlsx`, while keeping payment expense data visible and editable only at the finance approval node.

**Architecture:** Reuse the existing `system_dict_type`/`system_dict_data` dictionaries and the existing `taskFinance` BPMN node. The payment applicant request will no longer carry the finance-only field; the finance detail action and existing completion guard remain the authoritative write/required checks. Contract and invoice forms will load product options through the shared dictionary API. The invoice form maps “产品类型” to the existing backend `taxContent`/开票内容 field because there is no separate invoice product-type column.

**Tech Stack:** Java/Spring Boot/MyBatis-Plus backend, MySQL seed SQL, Vue 3 + TypeScript + Ant Design Vue, pnpm/Vitest, Maven/JUnit.

## Global Constraints

- Use the latest issue attachment as the source of truth: 40 accounting-subject values, 3 non-business receipt types, and 12 product-type values.
- Keep the dictionary keys stable where they already exist: `finance_accounting_subject` and `finance_fund_type_remark`; add `finance_product_type` for the shared contract/invoice product list.
- “费用科目/性质” is hidden on the payment application page and shown only for `taskFinance`; saving it is mandatory there, and the backend rejects writes outside that node or completion without a value.
- Do not change BPMN topology or unrelated finance form fields.

## Implementation Tasks

- [x] Add a local contract test covering the exact dictionary values, stale receipt option deactivation, payment-node visibility/requiredness, and Select usage on contract/invoice forms; run it red before production changes.
- [x] Update payment and receipt dictionary SQL idempotently. Seed all 40 spreadsheet accounting values, keep only the three spreadsheet receipt values active, and add the 12-value `finance_product_type` dictionary migration.
- [x] Remove `accountingSubject` from payment applicant create/resubmit input and payloads; clear any previous value on resubmit. Rename UI copy to “费用科目/性质”, keep the finance-only Select and save action, and hide the read-only field outside `taskFinance`.
- [x] Add the shared product dictionary constant and replace contract `productType` input with a dictionary Select.
- [x] Add invoice `taxContent` to the frontend model/payload/detail, render it as a “产品类型” dictionary Select, and validate the selected value server-side when supplied.
- [x] Validate contract product values server-side when supplied, update affected unit-test constructors/mocks, and adjust payment error wording to the new business label.
- [x] Run the contract test, focused backend tests, frontend typecheck/build, `git diff --check`, and inspect the final diff for unrelated changes.

## Verification

- Contract test proves the attachment-derived option sets and node/field source contracts.
- Focused Maven tests cover payment resubmit behavior and contract/invoice service validation.
- Frontend typecheck/build verifies Vue/API model compatibility; final commands and any remaining mapping assumption will be reported in the issue reply for acceptance.
