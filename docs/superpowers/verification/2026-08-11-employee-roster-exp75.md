# EXP-75 员工花名册字段补齐 — 验证记录

## 自动化

| 命令 | 结果 |
|---|---|
| `mvn -pl yudao-module-hrm/yudao-module-hrm-server -am -Dtest=EmployeeContractMapperTest,EmployeeServiceImplTest,EmployeeRosterExportTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS（9 tests） |
| `ruoyi-office-vben/node_modules/.bin/vitest run --dom apps/web-antd/src/views/hrm/employee/__tests__/employee-roster-fields.test.ts` | PASS（4 tests） |

## 模板列核对（原 XLSX）

- 工作表名：`文枢在职`
- 表头第 2 行 52 列已用 openpyxl 核对，与 `EmployeeRosterExportVO` 顺序一致
- 样例字典值示例：社保/公积金「是」、户籍「农业」、用工形式「全日制」、合同类型样例「无固定期限劳动合同」——系统字典预置为可配置编码（固定期限/无固定期限等），上线前 HR 可按业务口径增补字典项

## 迁移

- 脚本：`sql/mysql/hrm_employee_roster_exp75.sql`
- 行为：主档 11 列可空、教育扩展、合同表、5 类字典、入职单附件元数据复制到 `hrm_employee_archive_onboarding`
- 回滚策略：应用回滚可保留新表/列；旧代码忽略新结构。仅空库/无新数据环境允许 DROP 列/表

## 手工验收（环境就绪后）

1. 执行迁移脚本
2. 新建含社保、两段教育、四次合同、两份入职资料的员工，刷新详情核对
3. 导出 `文枢花名册.xlsx`，核对 52 列与「文枢在职」
4. 旧员工新字段为空时不显示「否」
