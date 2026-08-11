# EXP-70 产品快照 · 发布与回滚清单（复审第 4 轮修订）

> 方案 A：合同权威源 + 分层快照 + 后端派生。本文档不部署，仅作运维/发布操作指引。  
> **硬约束（复审 #1/#2）**  
> 1. **任何**旧/新版本切换**之前**必须启用并验证全局写栅栏；  
> 2. 后审计必须满足 §0.3 确定性阈值并签字后，方可解栅栏；  
> 3. 预审计/后审计仅用只读 A–H（及 I），**禁止**用带 UPDATE 的整份 Backfill 当审计。

## 0. 选定发布策略（写死：方案 2 + 前置写栅栏）

```text
【全局写栅栏 ON + 验证】
  → 排空旧 Pod / 在途事务（确认无旧写者）
  → DDL（可空加列）
  → 部署快照感知后端（+ 前端可同发）
  → 只读 A–H(+I) 预审计
  → 无歧义回填（权威条件与写路径一致）
  → 只读 A–H(+I) 后审计
  → 不达标：人工处置 → 重跑后审计（循环）
  → 后审计判定 PASS + 签字 → 写栅栏 OFF
  → 观察
```

**回滚**：保持写栅栏 ON；不得把「旧写者恢复」当作正常恢复路径；使用兼容新写者或前向修复，直至后审计再次 PASS。

### 0.1 写栅栏范围（强制，非建议）

**至少**关闭以下写入口（网关/权限/feature flag 均可，须可验证）：

| 入口 | 原因 |
| --- | --- |
| 商务单 create | 混版本产品权威 |
| 商务单 import | 同上 |
| 商务单 update（含换合同） | 同上 |
| 开票 create-and-start | 占用/快照 |
| 开票 resubmit | 同上 |
| 合同 create-and-start | #3 产品 `@NotBlank` 切换期 |
| 合同 resubmit | #3 |

**启用时机**：在**任何**旧/新版本切换**之前**（含 DDL 与应用滚动）。  
**验证**：抽检上述 API 返回 403/503/维护页；确认旧 Pod 已摘流量。  
**解除时机**：仅 §0.3 后审计 PASS + 发布负责人签字。

### 0.2 独立只读审计

- 预/后审计：`sql/mysql/finance_product_snapshot_audit_readonly_exp70.sql`（仅 SELECT，无 UPDATE）  
- **禁止**预审计阶段执行 `finance_product_snapshot_backfill_exp70.sql`  
- 镜像：`ruoyi-office-vben/sql/mysql/` 同名文件  

### 0.3 后审计确定性判据（解栅栏机器条件）

| 审计键 | 解栅栏阈值 | 不达标处置 |
| --- | --- | --- |
| **C** `C_bo_snapshot_conflict` | **必须 = 0** | 人工改数据或剔除引用后重跑 |
| **D** `D_invoice_mixed_products` | **必须 = 0** | 同上 |
| **E** `E_tax_content_mismatch` | **必须 = 0** | 同上 |
| **F_bo** `F_bo_snapshot_still_empty_with_contract`（可开票口径：有合同 + `settlement > IFNULL(occupied,0)` + 权威有效 + snapshot 仍空；权威无效归 **I**，不与 I 例外冲突） | **必须 = 0** | 回填失败原因归类后补齐，或关停该 BO 可开余额 |
| **A** `A_contract_product_empty` | 允许 >0，须有**责任人 + 处置结果 + 例外批准 + 样例 ID** | 记录后可解栅栏 |
| **B** `B_bo_no_contract` | 同上 | 同上 |
| **F_line** `F_line_snapshot_still_empty` | 同上（历史行本就不强制回填） | 同上 |
| **G** `G_line_source_contract_unproven` | 同上 | 同上 |
| **H** `H_line_product_unproven` | 同上 | 同上 |
| **I** `I_bo_contract_authority_invalid` | 允许 >0，须责任人+处置+例外批准+样例 | 不得静默自动写 |

**解栅栏检查单（全部勾选）**

- [ ] 后审计已执行只读脚本，原始结果归档  
- [ ] C=0 且 D=0 且 E=0 且 F_bo(可开票)=0  
- [ ] A/B/F_line/G/H/I：每键有责任人、处置结果、例外批准号（若 cnt>0）、样例 ID  
- [ ] 处置后**重跑**只读 A–H(+I)，结果仍满足上表  
- [ ] 发布负责人签字：姓名/时间  

未全部勾选 → **禁止**解除写栅栏。

## 1. 发布顺序（逐步）

| 顺序 | 动作 | 产物 |
| --- | --- | --- |
| 0 | **写栅栏 ON + 验证** | 运维记录 |
| 1 | 排空旧 Pod / 在途事务 | 集群/网关证据 |
| 2 | DDL | `finance_product_snapshot_exp70.sql` |
| 3 | 部署快照感知后端（+ 前端） | 应用包 |
| 4 | 只读预审计 | `*_audit_readonly_exp70.sql` |
| 5 | 回填 | `*_backfill_exp70.sql`（权威条件见 §回填） |
| 6 | 只读后审计 + §0.3 判据 | 归档 + 签字 |
| 7 | 写栅栏 OFF | 运维记录 |
| 8 | 观察 | 监控 §4 |

### 回填规则摘要（与正常写路径对齐 · 复审 #5）

自动写 **商务单** `product_type_snapshot` 仅当：

- BO `deleted=0`，snapshot 空  
- 合同 `deleted=0`，**`approval_status='APPROVED'`**，**`voided=0`**  
- 合同 `product_type` 非空  
- **`bo.importer_id = ca.applicant_user_id`**（主体一致）  
- `product_name` 空或与合同产品一致  

不满足权威条件但有关联合同、snapshot 仍空 → **不得 UPDATE**，进入审计 **I**。  
开票行 snapshot/source：**永不**自动回填。  
表头 `tax_content`：仅全部行已有行级 snapshot 且同产品且表头空。

## 2. 应用发布注意点

1. 写栅栏先于任何版本切换（含合同 `@NotBlank productType` 切换）。  
2. DDL 先于依赖新列的写流量。  
3. 字典 `finance_product_type` 保持 12 项。  

## 3. 回滚策略

| 层级 | 策略 |
| --- | --- |
| 应用 | 回退版本时**保持写栅栏**；禁止旧写者对外服务 |
| DDL | 不 DROP 列；不逆向覆盖快照 |
| 恢复 | 后审计再次 PASS + 签字后才解栅栏 |

## 4. 监控点

| 监控 | 说明 |
| --- | --- |
| 新错误码 | `1_040_001_013/014`、`1_040_003_017/018` |
| A–H/I | 关注 C/D/E/F_bo 是否回升 |
| 业务路径 | BO create/import/换合同；开票 create/resubmit |

## 5. 环境侧待办（本机未连库 · #4 分批）

- [ ] 按 §0 在预发演练写栅栏开关与解栅栏签字  
- [ ] DBA：整表 Backfill 分批、EXPLAIN、锁等待/复制延迟阈值、检查点（**环境硬门禁**）  
- [ ] 生产窗口前备份 / PITR  

## 6. 相关路径

- DDL：`sql/mysql/finance_product_snapshot_exp70.sql`  
- 只读审计：`sql/mysql/finance_product_snapshot_audit_readonly_exp70.sql`  
- 回填：`sql/mysql/finance_product_snapshot_backfill_exp70.sql`  
- 镜像：`ruoyi-office-vben/sql/mysql/` 同名文件  
