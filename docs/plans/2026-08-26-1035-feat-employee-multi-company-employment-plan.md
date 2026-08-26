---
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-brainstorm
execution: code
title: 员工多公司任职与签约公司
---

# 员工多公司任职与签约公司

## Goal Capsule

**Objective:** 一名员工可以在多家主体公司任职；薪酬考勤只跟唯一签约公司；发起审批时默认签约公司，也可改选其它任职公司，后续选人跟这次选的公司走。

**Means:** 任职关系独立成表，档案主表 `companyId` 继续表示签约公司；发起壳写入 `startCompanyDeptId`；「发起人公司财务」等策略优先读该变量。

**Product authority:** 本会话已确认：签约公司同时作为流程默认公司，发起时仍可改选其它任职公司。`session-settled: process-company-default`

**Open blockers:** 薪酬/考勤是否已 100% 只读 `employee.companyId` 需在实现时核对，未核对前不得改薪资公式语义。请假/报销/薪税等嵌入表单是否全部转发 `startCompanyDeptId` 仍有缺口。

## Product Contract

### Problem

现档案一人一公司。实际存在一人多家任职。若把财务塞进组织负责人，会改真实组织。薪酬必须跟劳动合同主体，审批必须跟这次办事的任职公司，二者不能绑死成同一个字段。

### Users

- HR：在档案维护任职列表，标注唯一签约公司。
- 员工：发起流程时看到默认签约公司，可改选其它任职。
- 财务/审批人：按所选任职公司命中公司财务等候选人。

### Key Decisions

- **KD-1 签约 vs 任职** `session-settled: process-company-default`  
  签约公司唯一，供薪酬考勤。发起流程默认签约公司，可改选其它任职。Governs R1 R2 R3。
- **KD-2 不改组织树**  
  任职不是把人挂进多家部门负责人。Governs R1。
- **KD-3 主表兼容**  
  `hrm_employee.companyId/companyName` 继续表示签约公司，避免薪酬考勤一次全改。Governs R2 R4。
- **KD-4 流程变量**  
  发起写入 `startCompanyDeptId`。选人策略优先该变量，其次表单主体公司，再回退上溯登录部门。Governs R3。

### Requirements

- **R1** HR 可给一名员工维护多家任职公司，必须且只能标一家签约公司。
- **R2** 薪酬、考勤、社保等计算继续以签约公司（主表 `companyId`）为准。
- **R3** 统一发起（含合同/开票/付款等嵌入表单）展示任职公司选择；默认签约；所选公司进入流程变量并影响「发起人公司财务」等按公司选人的策略。
- **R4** 已有档案按原所属公司回填一条签约任职，不丢历史。
- **R5** 登录系统用户仍只有一个主部门；任职表不替代 `system_user.dept_id`。

### Out of Scope

- 一人多登录账号、多主部门。
- 把财务配成公司部门负责人。
- 这次重做整套薪酬公式或考勤规则引擎。
- 入职/调动单据完整改成多任职（可后续票）。

### Success

- 档案能保存 2+ 任职且仅 1 个签约勾选。
- 工资核算名单仍按签约公司过滤。
- 发起页能改选任职公司，预测/实际审批人随 `startCompanyDeptId` 变。
- 未配置任职时行为与改前一致（回退上溯部门公司）。

## Planning Contract

工作区根目录无 `.compound-engineering/config.yaml`，计划落在 `docs/plans/`。实现必须走 `ce-work` + `ce-worktree`，票落在独立 HRM Epic，不得挂财务 EPIC-0023。已有未提交代码只作草稿，以本计划验收，缺的补、偏的改。

## Implementation Units

### U1 任职表与档案维护

- **R:** R1 R4 R5
- **Do:** `hrm_employee_employment`；档案读写 `employmentList`；保存时校验唯一签约并回写主表 `companyId`；存量回填。
- **Files:** `yudao-module-hrm/**`、`ruoyi-office-vben/apps/web-antd/src/views/hrm/employee/info/**`、`sql/mysql/hrm_employee_employment.sql`
- **Depends:** none
- **Verify:** 保存两家只勾一家签约；不勾或勾两家被拒；打开旧档案看到回填签约行。

### U2 发起选公司与审批变量

- **R:** R3
- **Do:** `GET /hrm/employee-archive/my-employments`；统一发起壳下拉；标准发起与合同/开票/付款嵌入把 `startCompanyDeptId` 写入变量；策略 52 优先读该变量。
- **Files:** `form.vue` 发起壳、各 embed `form-body.vue`、对应 `CreateAndStartReqVO`、`BpmTaskCandidateStartUserCompanyFinanceStrategy`
- **Depends:** U1
- **Verify:** 两家任职时改选后预测候选人变化；无任职时仍能发起且策略回退部门上溯。

### U3 薪酬考勤只读签约公司

- **R:** R2
- **Do:** 核对工资核算、考勤汇总、社保名单的公司过滤是否只用 `employee.companyId`；若有读部门上溯公司的路径，改为签约公司。不改公式本身。
- **Files:** `yudao-module-hrm/**` 薪酬考勤查询
- **Depends:** U1
- **Verify:** 员工任职 A+B、签约 A 时，只出现在 A 的工资/考勤名单。

### U4 其余嵌入发起补齐

- **R:** R3
- **Do:** 请假/出差/外出/报销/红冲/薪税等同路径转发 `startCompanyDeptId`。
- **Depends:** U2
- **Verify:** 这些流程发起也能改选任职公司并写入变量。

## Verification Contract

- 档案：双任职 + 唯一签约保存/回读。
- 流程：发起改选后 `startCompanyDeptId` 进实例变量；策略 52 命中映射表对应用户。
- 薪酬：签约公司过滤回归（U3）。
- test 库已回填的 18 条签约任职不丢。

## Definition of Done

- U1–U4 完成或 U4 明确拆成后续票且产品同意。
- 独立 HRM Epic/Story/Ticket 状态与实现一致。
- 经 `ce-work`/`ce-worktree` 合入，不在默认分支直接改。
- 发 test 后用双任职账号走通一次发起改选。
