# 合同签约 — 回归说明（CS-T7）

## 自动化（本地 / CI）

```bash
mvn -pl yudao-module-finance/yudao-module-finance-server -am test \
  -Dtest=FinanceContractApplicationServiceImplTest,\
FinanceContractApplicationMigrationContractTest,\
FinanceContractBpmnDiContractTest,\
FinanceContractApplicationExecAndBoBindingTest,\
FinanceContractPublishAssetsContractTest,\
FinanceBusinessOrderServiceImplTest,\
FinanceBusinessOrderImportTest,\
FinanceBusinessOrderControllerContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

**最近一次本地结果（2026-08-03）：** Tests run: 60, Failures: 0, Errors: 0 — BUILD SUCCESS。

### 覆盖映射（tech-plan §7）

| 场景 | 自动化 | 环境手工 |
| --- | --- | --- |
| createAndStart 成功写 processInstanceId | ServiceImplTest | S1 |
| 金额/前置/文件类型校验失败零副作用 | ServiceImplTest | — |
| amountNa 无金额可提交 | ServiceImplTest | — |
| 用印后撤回拒绝 | ServiceImplTest | S12 |
| resubmit 非 REJECTED 拒绝 | ServiceImplTest | S13 |
| onApprovalOutcome 幂等 | ServiceImplTest | — |
| recordSeal 无附件失败 | ExecAndBoBindingTest | S3 |
| recordMail needMail=否拒绝 | ExecAndBoBindingTest | S14 |
| listSelectable 仅 APPROVED+本人 | ExecAndBoBindingTest | S4–S7 |
| BO 新建无合同失败 | ExecAndBoBindingTest | S5 |
| BO 挂未通过/他人失败 | ExecAndBoBindingTest | S7 |
| BO 有占用换合同失败 | ExecAndBoBindingTest | S11 |
| BO 历史空可改非合同字段 | ExecAndBoBindingTest | S9 |
| 导入业务单号 / 契约字段 | ImportTest + ControllerContract | S8 |
| DDL 幂等 + 无完成证明列 | MigrationContractTest | §1 |
| BPMN 含 DI / needMail / Delegate | BpmnDiContractTest | S15 / §2 |
| 发布清单/菜单/前端入口存在 | PublishAssetsContractTest | §1–§4 |

## 手工矩阵（oa-test）

见发布清单 **§6 关键路径冒烟**（S1–S15）。

账号：`business_staff` / `finance_admin`（tenant_id=1）

## 主链路

```text
createAndStart → BPM(C15 全节点) → 用印 recordSeal → 归档 recordArchive
  → [needMail? recordMail] → Delegate APPROVED
  → BO create/import 挂 application_no / id（本人已通过）
```

## DI 人工勾选记录

| 环境 | 日期 | 操作人 | 画布可见可改 | has_di=1 | 备注 |
| --- | --- | --- | --- | --- | --- |
| | | | ☐ | ☐ | |
