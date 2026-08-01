# 合同签约申请 — 字段契约（CS-T1）

> 发布物。前后端、BPM 变量与库列对照。  
> DDL：`sql/mysql/finance_contract_application_phase1.sql`（镜像 `ruoyi-office-vben/sql/mysql/`）  
> 决策：`contract-signing-decisions` C15/C21/C22；Tech：`tech-plan-contract-signing` §4、D-T6/D-T7  
> **不做列：** 审批流程已完成证明（C22）

---

## 1. 命令入口命名（CS-T2+，本契约仅命名）

| 命令 | 说明 |
| --- | --- |
| `createAndStart` | 无草稿；提交即启流 `processKey=finance_contract_sign` |
| `resubmit` | 仅 REJECTED；整链重批 |
| `onApprovalOutcome` | 终态 PENDING→APPROVED/REJECTED/CANCELLED |
| `recordSeal` / `recordArchive` / `recordMail` | 执行节点写台账后再 completeTask |
| `listSelectableForBo` | APPROVED ∧ applicant=me |

businessKey = 台账 `id`。

---

## 2. 主表 `finance_contract_application`

| form schema key（建议） | DTO 字段（建议） | 列 | BPM 变量（建议） | 备注 |
| --- | --- | --- | --- | --- |
| _(system)_ | — | `id` | `businessKey` / `contractApplicationId` | |
| _(system)_ | — | `application_no` | `applicationNo` | **导入/选择器业务单号** |
| _(system)_ | — | `process_instance_id` | — | 最新实例 |
| _(system)_ | — | `approval_status` | `approvalStatus` | PENDING/APPROVED/REJECTED/CANCELLED |
| _(system)_ | — | `current_node_key` | — | Listener 回写 |
| _(system)_ | — | `current_node_name` | — | 列表标签 |
| _(system)_ | `applicantUserId` | `applicant_user_id` | `applicantUserId` | BS 权限 |
| _(system)_ | — | `applicant_dept_id` | — | 可选快照 |
| counterpartyCompanyId | `counterpartyCompanyId` | `counterparty_company_id` | — | **F-B**；启用客商 |
| _(snapshot)_ | — | `counterparty_name` | `counterpartyName` | 提交快照 |
| amountNa | `amountNa` | `amount_na` | — | true 时金额可空 |
| contractAmount | `contractAmount` | `contract_amount` | `contractAmount` | amount_na=false 时必填 |
| signCompany | `signCompany` | `sign_company` | `signCompany` | 签约主体 |
| fileName | `fileName` | `file_name` | `fileName` | 用印文件名 |
| fileType | `fileType` | `file_type` | `fileType` | 采购/销售/租赁/借款/推广充值业务合同 |
| productType | `productType` | `product_type` | — | |
| rebateRatio | `rebateRatio` | `rebate_ratio` | — | 文本 |
| settlementMethod | `settlementMethod` | `settlement_method` | — | |
| copyCount | `copyCount` | `copy_count` | — | |
| sealTypes | `sealTypes` | `seal_types` | — | JSON 或分隔串 |
| needMail | `needMail` | `need_mail` | **`needMail`** | 网关布尔（boolean/0/1 约定 T2） |
| mailAddress | `mailAddress` | `mail_address` | — | needMail 时必填 |
| preProcessRef | `preProcessRef` | `pre_process_ref` | — | 采购/租赁条件 |
| startDate | `startDate` | `start_date` | — | |
| endDate | `endDate` | `end_date` | — | 将到期筛选 |
| draftFileUrl | `draftFileUrl` | `draft_file_url` | — | 提交电子版 |
| _(recordSeal)_ | `sealFileUrl` | `seal_file_url` | — | 用印节点必填 |
| _(recordSeal)_ | `actualSealerUserId` | `actual_sealer_user_id` | — | 默认申请人 |
| _(recordArchive)_ | — | `archived_at` | — | 归档时间 |
| _(recordMail)_ | `mailTrackingNo` | `mail_tracking_no` | — | 邮寄节点 |
| remark | `remark` | `remark` | — | |
| _(system)_ | — | `voided` | `voided` | 作废 |

### amount_na 规则

```text
amount_na = true  → contract_amount 允许 NULL
amount_na = false → contract_amount NOT NULL 且 > 0（应用层）
```

### 对方（客商）

- 引用 `finance_customer_company`（启用态）  
- `counterparty_name` 为提交快照，不随档案回写历史行  

---

## 3. 商务单扩展 `finance_business_order`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `contract_application_id` | bigint NULL | **正式关联**；新写/导入必填且指向 APPROVED |
| `contract_process_id` | varchar(127) 保留 | **legacy 脏文本** / 未关联展示；新写禁止仅写此列当正式关联 |

导入契约键：**合同申请业务单号** = `finance_contract_application.application_no`。

---

## 4. 审批状态与可挂 BO

| approval_status | 可被 BO 选择（BS） |
| --- | --- |
| PENDING / REJECTED / CANCELLED | 否 |
| APPROVED | 是，且 `applicant_user_id = 当前用户` |

---

## 5. 流程节点 key（建议，与 BPMN 对齐 · CS-T3）

| current_node_key | 显示名 |
| --- | --- |
| biz_lead | 待业务主管 |
| legal | 待法务 |
| finance | 待财务 |
| gm | 待总经理 |
| seal | 待用印 |
| archive | 待归档 |
| mail | 待邮寄 |

网关：`needMail`；为 false 时跳过 `mail`。

---

## 6. 明确不做

| 项 | 说明 |
| --- | --- |
| 审批流程已完成证明 | 无列、无 form 必填；归档以 `seal_file_url` + `archived_at` 为准 |
| 独立用印申请表 | 用印在本表流程内 |
| 充值框架余额列 | 非本 epic |
