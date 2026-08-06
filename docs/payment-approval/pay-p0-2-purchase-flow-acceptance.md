# PAY-P0-2：通用采购流 `oa_purchase_apply` 验收清单

| 项 | 值 |
| --- | --- |
| 流程 key | **`oa_purchase_apply`**（白名单：`FinancePurchaseProcessConstants.PURCHASE_PROCESS_KEYS`） |
| 形态 | OA 动态表单 + BPM；**非** finance 专属采购实体/菜单 |
| 依据 | tech-plan-payment-approval TP8–TP9；318 配置验收记录 |

## 代码侧

- [x] 白名单常量 `FinancePurchaseProcessConstants`（唯一真相源）
- [ ] 环境模型已发布且 key 与常量一致

## 配置 / 运维

- [ ] 流程设计器存在已发布定义 `oa_purchase_apply`
- [ ] 动态表最小字段：采购类型、费用归属项目、明细行、期望到货日、附件（比价不强制）
- [ ] 节点：提单人 → 部门负责人 → 公司业务负责人 → 财务主管
- [ ] **OA 发起中心** 可发起；**无** 财务管理下「采购申请」专属菜单
- [ ] 占位审批人可替换为真实职责

## 联调（供 P0-3）

- [ ] 非财务角色从发起中心提单
- [ ] 走完审批至成功结束 ≥1 条实例
- [ ] 审批中 / 已驳回实例可区分（不得被付款引用）
- [ ] 记录 1 条已通过 `processInstanceId` 供 P0-3 选择器联调

## 明确不做

- `finance_purchase_*` 表
- 预算引擎、强制比价
- 将 `oa_payment_apply` 作为财务付款写路径
