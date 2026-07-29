# 开票申请 BPM 同步 Delegate 挂载约定（T3）

> 主路径：Flowable **同步** `JavaDelegate`，失败抛错阻断引擎事务。  
> **禁止**仅依赖异步 `BpmNotificationManager` + 吞异常 Listener 做占用/可认领。

## Bean

| 项 | 值 |
|----|-----|
| Spring Bean 名 | `financeInvoiceApprovalOutcomeDelegate` |
| 类 | `cn.iocoder.yudao.module.finance.framework.bpm.FinanceInvoiceApprovalOutcomeDelegate` |
| 接口 | `org.flowable.engine.delegate.JavaDelegate` |
| 表达式 | `${financeInvoiceApprovalOutcomeDelegate}` |

## 流程模型

- Process Definition Key：`finance_invoice_apply`
- businessKey：开票申请主键 `appId`（字符串）
- 流程变量 `PROCESS_STATUS`：与 `BpmTaskStatusEnum` 一致（2 通过 / 3 驳回 / 4 取消 / 10 撤回）

建议挂载点（任选其一，**须在 PROCESS_STATUS 已写入之后**）：

1. 结束事件（endEvent）ExecutionListener `end` → delegateExpression
2. 审批通过/驳回出口 ServiceTask → delegateExpression
3. 最后一个 UserTask 完成监听（需确认 status 变量已落）

## 辅路径

`FinanceInvoiceApplicationStatusListener` 监听 `BpmProcessInstanceStatusEvent`，仅作辅助/对账；**不可替代**同步 Delegate。

## 映射

| PROCESS_STATUS | onApprovalOutcome |
|----------------|-------------------|
| 2 APPROVE | APPROVED（保持 BO 占用） |
| 3 REJECT | REJECTED（释占） |
| 4 CANCEL / 10 WITHDRAW | CANCELLED + voided（释占） |
