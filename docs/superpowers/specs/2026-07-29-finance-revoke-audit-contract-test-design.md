# 财务撤销审计响应契约测试修复设计

## 背景

`FinanceReceiptClaimControllerContractTest` 仍断言撤销审计响应 VO 只有 5 个字段，
但提交 `40806d70e` 已为操作人姓名展示新增 `reviewerName`，并为现有前端
`AuditLog` 增加 `operatorId`、`operatorName`、`reason`、`createTime`、`action`
兼容字段。生产 VO、控制器映射和前端消费已经形成 11 字段契约，测试未同步更新。

## 决策

保留当前 API 行为，只修复漂移的契约测试。撤销审计响应的精确字段顺序固定为：

1. `id`
2. `claimId`
3. `reviewerId`
4. `reviewerName`
5. `operatorId`
6. `operatorName`
7. `revokeTime`
8. `revokeReason`
9. `reason`
10. `createTime`
11. `action`

其中 `reviewerName` 是审计快照字段；后五个展示兼容字段由控制器从原生撤销审计字段派生。

## 修改范围

- 重命名旧测试，使名称准确表达“持久化字段与展示兼容字段”的响应契约。
- 将精确字段列表更新为上述 11 个字段。
- 补齐新增字段的 Java 类型断言。
- 不修改 VO、控制器、前端、数据库或接口路径。

## 验证

1. 修复前，现有测试稳定失败，差异为期望 5 字段、实际 11 字段。
2. 修复后，`FinanceReceiptClaimControllerContractTest` 全部通过。
3. 财务模块完整测试通过；若出现其他失败，先确认是否由本次测试变更引入。
4. `git diff --check` 通过，随后快进推送到 `codeup/dev`。

## 非目标

- 不删除前端兼容字段。
- 不把精确契约降级为“只检查字段子集”。
- 不处理与撤销审计响应无关的重构。
