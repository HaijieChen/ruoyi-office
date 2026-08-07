# PAY-P0-2：通用采购流 `oa_purchase_apply` 验收清单

| 项 | 值 |
| --- | --- |
| 流程 key | **`oa_purchase_apply`**（白名单：`FinancePurchaseProcessConstants.PURCHASE_PROCESS_KEYS`） |
| 形态 | OA 动态表单 + BPM；**非** finance 专属采购实体/菜单 |
| 依据 | tech-plan-payment-approval TP8–TP9；318 配置验收记录 |

## 代码侧

- [x] 白名单常量 `FinancePurchaseProcessConstants`（唯一真相源）
- [x] 环境模型已发布且 key 与常量一致（oa-test 2026-08-06）

## 配置 / 运维

- [x] 流程设计器存在已发布定义 `oa_purchase_apply`（oa-test model `59febb33-9185-11f1-a9fc-469fa1a325ed`）
- [x] 动态表最小字段：采购类型、费用归属项目、明细行、期望到货日、附件（比价不强制；表单 id=1）
- [x] 节点：提单人 → 部门负责人 → 公司业务负责人 → 财务主管（占位审批人=admin）
- [x] **OA 发起中心** 可发起；**无** 财务管理下「采购申请」专属菜单
- [ ] 占位审批人可替换为真实职责（仍为 admin 占位，待运维改角色/负责人）

## 联调（供 P0-3）

- [x] 非财务角色从发起中心提单（`businessstaffuser`）
- [x] 走完审批至成功结束 ≥1 条实例
- [x] 审批中 / 已驳回实例可区分（不得被付款引用）— 选择器仅 `PROCESS_STATUS=APPROVE` 且本人发起
- [x] 记录 1 条已通过 `processInstanceId` 供 P0-3 选择器联调：`5a7a53d9-9185-11f1-a9fc-469fa1a325ed`（见 epic `oa-test-deploy-purchase-apply-20260806`）

## 明确不做

- `finance_purchase_*` 表
- 预算引擎、强制比价
- 将 `oa_payment_apply` 作为财务付款写路径
