# 合同签约 BPM 同步 Delegate / 节点标签（CS-T3）

> 主路径：Flowable **同步** Delegate；辅路径 StatusListener。  
> 节点标签：`FinanceContractTaskNodeLabelListener`（task create）。  
> BPMN 种子（**含 DI**）：`sql/mysql/bpmn/finance_contract_sign.bpmn20.xml`

## Bean

| 项 | 值 |
| --- | --- |
| 终态 Delegate | `financeContractApprovalOutcomeDelegate` |
| 类 | `...framework.bpm.FinanceContractApprovalOutcomeDelegate` |
| 表达式 | `${financeContractApprovalOutcomeDelegate}` |
| 节点标签 | `financeContractTaskNodeLabelListener` |
| 表达式 | `${financeContractTaskNodeLabelListener}` event=create |

## 流程

| 项 | 值 |
| --- | --- |
| Process Key | `finance_contract_sign` |
| businessKey | 台账主键 id |
| 变量 `needMail` | boolean，网关 |
| 节点 | 业务主管→法务→财务→总经理→用印→归档→(邮寄?) |

## 映射 PROCESS_STATUS → onApprovalOutcome

与开票一致：RUNNING/空→APPROVED；2→APPROVED；3→REJECTED；4/10→CANCELLED。

## DI 验收

1. 导入种子 XML 到流程设计，KEY=`finance_contract_sign`  
2. 打开设计器画布可见全部节点与连线  
3. 运行 `sql/mysql/finance_contract_bpmn_diagram_di.sql` 校验 `has_di=1`  
4. 修改后 **重新发布**

## 候选组（默认，可在设计器改）

| task | candidateGroups |
| --- | --- |
| taskBizLead | contract_biz_lead |
| taskLegal | contract_legal |
| taskFinance | contract_finance |
| taskGm | contract_gm |
| taskSeal / taskArchive | contract_seal_admin |
| taskMail | contract_mail |
