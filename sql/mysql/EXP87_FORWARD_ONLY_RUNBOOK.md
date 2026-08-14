# EXP-87 迁移：Forward-only 与回滚演练说明

## 适用范围

本单迁移脚本为 **forward-only（仅向前）**：

| 脚本 | 作用 |
|---|---|
| `finance_company_bank_account_exp87.sql` | 新建账户主数据表 + 菜单/角色 |
| `finance_payment_pay_line_exp87.sql` | 支付明细 / 薪税明细表；`application_kind`/`period_label` 列；收款方等列 **改可空** |
| `finance_salary_tax_payment_menu_exp87.sql` | 薪税菜单与权限 |
| `finance_salary_tax_bpm_form_view_path_exp87.sql` | BPM form_custom_view_path |
| BPMN | `finance_salary_payment_apply` / `finance_tax_payment_apply` |

## 部署顺序（可演练）

1. **全库一致性快照或逻辑备份（必做，prod 强制）**  
   - 推荐：整库 snapshot / `mysqldump --single-transaction` **全库**（含 Flowable 表）。  
   - 最小集合（不足时须明确接受风险）须至少包含：  
     - 业务：`finance_payment_application`、`finance_company_bank_account`、`finance_payment_pay_line`、`finance_payment_salary_line`、`finance_payment_tax_line`  
     - 菜单：`system_menu`、`system_role_menu`  
     - BPM 元数据：`bpm_process_definition_info`  
     - **Flowable**：`ACT_RE_DEPLOYMENT`、`ACT_RE_PROCDEF`、`ACT_GE_BYTEARRAY`（资源字节）、`ACT_RE_MODEL`（若使用）、运行时/历史按环境保留策略  
   - 示例：  
     ```bash
     mysqldump -u... -p... --single-transaction --routines dbname \
       > exp87_full_pre_$(date +%Y%m%d%H%M).sql
     ```
2. 在变更窗口执行 DDL/菜单 SQL（幂等脚本可重复执行）。
3. 部署应用与前端。
4. 导入 BPMN 定义（`finance_salary_payment_apply` / `finance_tax_payment_apply`）。
5. 执行 `finance_salary_tax_bpm_form_view_path_exp87.sql`（依赖已存在 process_definition_info 行）。
6. 冒烟：普通付款出纳选账户；薪税**独立菜单**与**统一发起目录**（有 create 权限时露出两流程卡片，点击跳转业务页）均可发起；**禁止**通用 createProcessInstance 直启薪税 key；待办详情 form path；财务节点写科目。

## Flowable 状态备份与恢复（EXP-87 F6）

### 备份对象

| 表 | 说明 |
|---|---|
| `ACT_RE_DEPLOYMENT` | 部署记录 |
| `ACT_RE_PROCDEF` | 流程定义（key/version） |
| `ACT_GE_BYTEARRAY` | BPMN/资源字节 |
| `ACT_RE_MODEL` | 设计器模型（如有） |
| `bpm_process_definition_info` | 业务扩展：form_custom_view_path 等 |

### 恢复后校验 SQL（演练必跑）

```sql
-- 1) 两 process key 应存在启用定义（按环境命名）
SELECT KEY_, VERSION_, DEPLOYMENT_ID_, SUSPENSION_STATE_
FROM ACT_RE_PROCDEF
WHERE KEY_ IN ('finance_salary_payment_apply', 'finance_tax_payment_apply')
ORDER BY KEY_, VERSION_;

-- 2) 回滚到「未上线薪税」目标时：确认无残留 key（或仅旧版本）
-- SELECT COUNT(*) FROM ACT_RE_PROCDEF
-- WHERE KEY_ IN ('finance_salary_payment_apply', 'finance_tax_payment_apply');

-- 3) 业务 form path 一致性
SELECT process_definition_id, form_custom_view_path
FROM bpm_process_definition_info
WHERE deleted = b'0'
  AND (process_definition_id LIKE 'finance_salary_payment_apply%'
    OR process_definition_id LIKE 'finance_tax_payment_apply%'
    OR process_definition_id LIKE 'finance_payment_apply%');
```

### 演练步骤（非 prod 必跑一次）

1. 预发执行完整 forward + 导入 BPMN + form path SQL，造 1 条薪税待办。  
2. 记录 dump 文件路径与 SHA256。  
3. **回滚**：停写流量 → 用步骤 1 的全库 dump 恢复 → 应用回退到迁移前版本。  
4. 跑「恢复后校验 SQL」：确认目标状态（有/无两 key）与期望一致。  
5. 验证旧普通付款可运行；新菜单/定义按目标消失或不可直启。  
6. 记录演练时长与负责人。

## 为何不做「逆序 SQL 完美回滚」

1. **`payee_*` / `business_settlement_term` / `pay_method` / `cost_project` 改为可空**  
   - 若新数据写入 NULL，**无法安全改回 NOT NULL**（需先回填默认值或删行）。  
2. **支付明细 `finance_payment_pay_line` 为业务台账**  
   - 删除表会丢失审计流水，业务上不可接受。  
3. **Flowable 部署/定义/资源** 与运行中实例耦合，逆序 DELETE 易产生孤儿任务。  
4. **菜单/角色绑定** 可能被运维手工改过，逆序 DELETE 易误伤。

因此 **V1 回滚策略 = 全库/一致性快照恢复 + 代码回退**，而不是逆序 DROP。

## 若必须「部分卸载」新功能（不恢复备份）

仅当业务确认可丢弃新数据时，**按依赖逆序**（危险，需书面批准）：

1. 停用菜单（`system_menu.status=1` 或删除 role_menu），勿急删表。  
2. 停用/下线薪税 process definition（`SUSPENSION_STATE_`），勿在有运行实例时删 deployment。  
3. 清空/归档 `finance_payment_pay_line` / salary_line / tax_line（业务确认后）。  
4. 新单不再写入 `application_kind=SALARY/TAX`。  
5. **不要**对 `payee_*` 等列执行 `MODIFY NOT NULL`，除非已保证无 NULL 行。  
6. 账户主数据表可保留（只读）或归档。

## 数据处置原则

- **支付行不可变**：禁止逻辑删除实际支付明细作为业务回滚手段。  
- 有支付后的纠错走 **作废/冲正**（本单未实现冲正，须挡死 resubmit/reject）。  
- 公司主体仍以组织 `dept` 为准，账户仅 FK，无双写主体。  
- 薪税 **禁止** 通用 `createProcessInstance` 直启；统一发起目录**可露出**（有 create 时），卡片跳转业务页；独立菜单 + 领域 API 仍为主路径。

## 检查清单

- [ ] 全库/Flowable 备份路径与 SHA256 已登记  
- [ ] 预发完成一次 restore 演练（含 ACT_RE_* 校验 SQL）  
- [ ] BPM path SQL 在定义部署后执行  
- [ ] 有 create 权限时统一目录展示薪税两 key，点击跳转业务入口
- [ ] 通用 createProcessInstance 对薪税 key 仍拒绝
- [ ] 回滚决策人与窗口已确认  
