# 表单数据源 Schema 中文说明迁移验证

验证日期：2026-07-19  
分支：`codex/oa-platform-production`

## 迁移结果

迁移通过现有管理接口逐项执行“保存新草稿、试运行、发布”，未直接修改历史版本 JSON。

| 数据源 | 原发布版本 | 新发布版本 | Schema 字段数 | 试运行结果数 |
| --- | ---: | ---: | ---: | ---: |
| `system_companies` | 1 | 2 | 5 | 2 |
| `oa_available_seals` | 1 | 2 | 9 | 0 |
| `bpm_approved_processes` | 1 | 2 | 10 | 0 |
| `hrm_entry_processes` | 2 | 3 | 17 | 0 |
| `hrm_active_employees` | 4 | 5 | 18 | 0 |
| `crm_customers` | 2 | 3 | 13 | 1 |
| `crm_customer_addresses` | 1 | 2 | 13 | 0 |
| `asset_employee_assets` | 1 | 2 | 12 | 0 |
| `bpm_recharge_contracts` | 1 | 2 | 10 | 0 |
| `bpm_rebate_rules` | 1 | 2 | 11 | 0 |
| `bpm_recharge_records` | 1 | 2 | 9 | 0 |
| `bpm_unclaimed_receipts` | 1 | 2 | 9 | 0 |
| `oa_seal_types` | 1 | 2 | 3 | 7 |

## 只读复核

- 启用且已发布的数据源：13 个。
- 当前发布 Schema 字段：139 个。
- 空白、缺失或超过 64 字符的中文说明：0 个。
- 保留的旧版本：18 个；所有数据源版本数均在迁移后增加 1。
- 已发布元数据接口逐项读取成功，响应不包含 `sourceConfig`、原始 Schema、SQL 或平台接口路径。
- 当前分支 JDK 17 单体构建成功；运行 JAR 为 201,267,164 字节，后端健康检查为 `UP`。

