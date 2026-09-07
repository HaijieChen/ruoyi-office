# 协调者预审记录（尚未通过）

生产基线：06597d9ba92da6c784cb359241f50a950373bea7。

## 待解决门禁

1. 整方法忽略数据范围后 hasPermission(query) 直接放行，扩大原 SELF/部门可读范围。必须保留普通 query 路径原数据范围，只有服务端验证真实参与的关联单据可使用参与人路径。覆盖 query=true/SELF非本人/无参与拒绝。
2. 当前测试代理验证 Spring Security，但 mapper 与参与关系全被 mock，不是 DataPermission/租户/Flowable 真实验证。历史审批人测试只 mock true，不能证明历史引擎关系生效。
3. generic get-approval-detail 无 processInstanceId 的定义预览仍应保留原门禁；有实例才按参与关系放行。
4. 矩阵须补齐全部 form_type10 和生产11种业务表单，财务非合同已改由 grok-0 接续审查（#487），另一 peer 明确未接手。合同协调者只评估已发布065子接口覆盖。
5. 当前/历史 assignee、task owner、引擎真实候选、无关人、伪造关联、跨租户以及完整附件预览下载需实际用例步骤与证据。保留现有共享/抄送语义；通用 BPM 当前明确允许抄送。

以上已通过 Agent Mail #487-491 发送。自动邮件镜像连接出现超时，发送成功不代表执行者已读；交付前须核实这些意见已处理。本文件是代码预审证据，不是部署授权或验收完成声明。

## 后续预审补充

- BPM `task-list.vue` 实际调用 `BpmTaskController/list-by-process-instance-id`，流转记录需要纳入实例参与读权验证。减签子任务列表和打印不自动扩大。
- 财务既有 `manageAll` 不能套用用印新引入的普通 query 分支规则：付款 `MANAGE_ALL_PERMISSION=finance:payment-application:update`，同时 `getOrdinaryApplicationForRead(...,true)` 被支付登记、修改会计科目写入口用于类型闭合。保留065显式管理/关联读权语义，避免收紧到SELF导致原写入口回归。报销 `canQueryAll` 同样需核对实际既有授权，不能无证据改动。
