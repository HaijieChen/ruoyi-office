# 开票+认领改挂 — 回归说明（T7）

## 自动化（本地）

```bash
mvn -pl yudao-module-finance/yudao-module-finance-server -am test \
  -Dtest=FinanceInvoiceApplicationServiceImplTest,FinanceReceiptClaimServiceImplTest,FinanceInvoiceClaimPhase2aMigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

覆盖要点：

| 场景 | 用例 |
|------|------|
| 占用超额/并发 | createAndStart tests |
| 批过不释占 / 驳回释占 / 幂等 | onApprovalOutcome tests |
| 未审批不可办票 / 部分办票 | updateIssueProgress tests |
| 双边 pending create | createClaimShouldOccupyDualPending |
| 确认 pending→claimed | confirmClaimShouldMovePendingToConfirmed |
| LEGACY 禁 revoke | revokeClaimShouldRejectLegacySource |
| claimAllowed 不读 issue | claimAllowedHelperIgnoresIssueStatus |

## 手工矩阵（oa-test）

账号：`business_staff` / `finance_admin`（tenant_id=1）

1. **开票 → 认领（批过未出票）**  
   BS 提交 createAndStart → FA 审批通过（Delegate）→ BS 新建认领选该开票 → FA 确认  
2. **同步失败**  
   人为让 onApprovalOutcome 失败时，审批任务不得静默成功（Delegate 抛错）  
3. **并发认到款**  
   两笔 create 同到款超额第二笔失败  
4. **resubmit**  
   驳回后重提，无双占  
5. **历史 revoke**  
   LEGACY 单撤销 403/业务错误  
6. **无草稿**  
   UI 无草稿入口  

## 主链路

```text
开票 createAndStart → BPM 审批 → onApprovalOutcome(APPROVED)
  → 认领 create(INVOICE pending 双边)
  → confirm / reject / revoke(INVOICE only)
办票 updateIssueProgress 独立于认领
```
