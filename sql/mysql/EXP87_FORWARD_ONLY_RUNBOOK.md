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

1. **备份**（必做，prod 强制）  
   - 逻辑备份：`mysqldump` 至少包含  
     `finance_payment_application`、`system_menu`、`system_role_menu`、`bpm_process_definition_info`  
     及后续将创建的新表（空库可跳过）  
   - 示例：  
     `mysqldump -u... -p... --single-transaction --routines dbname finance_payment_application system_menu system_role_menu bpm_process_definition_info > exp87_pre.sql`
2. 在变更窗口执行 DDL/菜单 SQL（幂等脚本可重复执行）。
3. 部署应用与前端。
4. 导入 BPMN 定义（salary/tax process key）。
5. 执行 `finance_salary_tax_bpm_form_view_path_exp87.sql`（依赖已存在 process_definition_info 行）。
6. 冒烟：普通付款出纳选账户；薪税独立菜单发起；待办详情 form path。

## 为何不做「逆序 SQL 完美回滚」

1. **`payee_*` / `business_settlement_term` / `pay_method` / `cost_project` 改为可空**  
   - 若新数据写入 NULL，**无法安全改回 NOT NULL**（需先回填默认值或删行）。  
2. **支付明细 `finance_payment_pay_line` 为业务台账**  
   - 删除表会丢失审计流水，业务上不可接受。  
3. **菜单/角色绑定** 与 **BPM 定义** 可能被运维手工改过，逆序 DELETE 易误伤。

因此 **V1 回滚策略 = 备份恢复（restore dump）+ 代码回退**，而不是逆序 DROP。

## 回滚演练（非 prod 必跑一次）

1. 在预发/测试库执行完整 forward 脚本 + 造 1 条账户 + 1 条支付明细。  
2. 记录当前 HEAD 应用版本。  
3. **回滚**：  
   - 停止写入流量；  
   - 用步骤 1 的 `exp87_pre.sql` 恢复库（或整库快照）；  
   - 应用回退到迁移前版本；  
4. 验证：旧付款创建/出纳（无账户字段的旧包）可运行；新菜单可消失。  
5. 记录演练时长与负责人。

## 若必须「部分卸载」新功能（不恢复备份）

仅当业务确认可丢弃新数据时，**按依赖逆序**（危险，需书面批准）：

1. 停用菜单（`system_menu.status=1` 或删除 role_menu），勿急删表。  
2. 清空/归档 `finance_payment_pay_line` / salary_line / tax_line（业务确认后）。  
3. 新单不再写入 `application_kind=SALARY/TAX`。  
4. **不要**对 `payee_*` 等列执行 `MODIFY NOT NULL`，除非已保证无 NULL 行。  
5. 账户主数据表可保留（只读）或归档。

## 数据处置原则

- **支付行不可变**：禁止逻辑删除实际支付明细作为业务回滚手段。  
- 有支付后的纠错走 **作废/冲正**（本单未实现冲正，须挡死 resubmit/reject）。  
- 公司主体仍以组织 `dept` 为准，账户仅 FK，无双写主体。

## 检查清单

- [ ] 备份文件路径与校验和已登记  
- [ ] 预发完成一次 restore 演练  
- [ ] BPM path SQL 在定义部署后执行  
- [ ] 回滚决策人与窗口已确认  
