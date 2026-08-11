# EXP-75 员工花名册字段补齐 — 验证记录

## 自动化（审查 FAIL 修复后）

| 命令 | 结果 |
|---|---|
| `mvn -pl yudao-module-hrm/yudao-module-hrm-server -am -Dtest=AttachmentServiceImplTest,EmployeeContractMapperTest,EmployeeServiceImplTest,EmployeeRosterExportTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS（19 tests） |
| `ruoyi-office-vben/node_modules/.bin/vitest run --dom apps/web-antd/src/views/hrm/employee/__tests__/employee-roster-fields.test.ts` | PASS（5 tests） |

## F1–F8 修复证据摘要

| 项 | 修复 | 测试 |
|---|---|---|
| F1 真实上传 | `AttachmentList` 调用 `/infra/file/upload`，仅用服务端 URL 建元数据 | Vitest `createAttachmentFromUpload`；服务端拒 `blob:` |
| F5 归属 | `AttachmentServiceImpl` 非空 ID 必须同 businessType+businessId | `rejectsCrossBusinessAttachmentIdHijack` |
| F6 边界 | 最多 10 份 / 20MB / pdf\|jpg\|jpeg\|png | `rejectsMoreThanTen…`、`rejectsOversized…` |
| F3 null 写库 | 11 个可空字段 `FieldStrategy.ALWAYS`；社保否清空参保月 | `updateSocialSecurityFalse…`、`rosterNullableFieldsUseAlways…` |
| F4 集合契约 | null 保留 / 空数组清空 | `updateOmittingContractListPreserves…`、`updateEmptyContractListClears…` |
| F8 startDate | `@Valid` + 服务端非空校验 | `updateRejectsContractMissingStartDate` |
| F7 字典标签 + XLSX | `DictFrameworkUtils` 解析；真实写出 | `exportWritesRealXlsxWith52HeadersAndSheetName`；成品 `docs/superpowers/verification/exp75-evidence/文枢花名册-evidence.xlsx` |
| F2 幂等迁移 | `information_schema` 判列 + PREPARE；表 IF NOT EXISTS；字典/附件 NOT EXISTS | 脚本可重复执行 |

## 导出成品核对

- 文件：`docs/superpowers/verification/exp75-evidence/文枢花名册-evidence.xlsx`
- 工作表：`文枢在职`；表头 52 列；身份证/手机/银行卡为文本字符串

## 迁移

- 脚本：`sql/mysql/hrm_employee_roster_exp75.sql`（幂等续跑）
- 回滚策略：应用回滚可保留新表/列；旧代码忽略新结构

## 手工验收（环境就绪后）

1. 执行迁移脚本（可重复执行）
2. 页面实际上传入职资料 → 保存 → 刷新 → 下载
3. 导出 `文枢花名册.xlsx` 与原模板并排核对字典文案
