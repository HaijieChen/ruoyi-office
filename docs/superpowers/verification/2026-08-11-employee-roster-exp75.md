# EXP-75 员工花名册字段补齐 — 验证记录

## 自动化（复审 FAIL #1–#7/#9/#2 修复后）

| 命令 | 结果 |
|---|---|
| `mvn -pl yudao-module-hrm/yudao-module-hrm-server,yudao-module-infra/yudao-module-infra-server -am -Dtest=AttachmentServiceImplTest,EmployeeContractMapperTest,EmployeeServiceImplTest,EmployeeRosterExportTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS（23 tests） |
| Vitest employee-roster-fields | PASS（5 tests） |

## 复审阻断项修复

| 编号 | 修复 | 测试/证据 |
|---|---|---|
| #1 鉴权下载 | `GET /hrm/employee-archive/onboarding-attachment/download` + `@PreAuthorize query`；前端 Bearer fetch | `downloadRejectsAttachmentNotBelongingToEmployee` |
| #5 fileId claim | `/infra/file/upload-detail` 返回 id；保存只信 `FileApi.getFile` 元数据 | `createPersists…`；`rejectsForged…`/`rejectsOversized…`/`rejectsExe…` |
| #6 专用 VO | `OnboardingAttachmentSaveReqVO` 无 businessType 约束；前端只 POST id/fileId | 服务测试用真实 VO 形状 |
| #4 限制隔离 | 通用 `AttachmentService` 不再强制 PDF/20MB；仅 onboarding 路径校验 | `allowsDocxForNonOnboardingBusiness` |
| #9 20MB 传输 | `max-file-size: 20MB`（yudao-server / infra-server） | 配置变更 |
| #7 三态更新 | setter 记录 present；省略回填旧值；显式 null 清空 | `sparseUpdateOmitsKeepExplicitNullClears` |
| #3 迁移软删 | 回填 NOT EXISTS 含已软删目标行 | 脚本条件无 `deleted=0` |
| #2 合成夹具 | 测试/证据使用 `TEST_*` / `10000000000` | 导出 XLSX 内容断言 |

## 导出证据（合成数据）

- `docs/superpowers/verification/exp75-evidence/文枢花名册-evidence.xlsx`
- 工作表「文枢在职」、52 列；姓名/证件等均为 **TEST_** 前缀合成值，**非真实身份数据**
- 仓库内同路径已替换为合成版；issue 旧附件若仍含拟真样例，请仓库负责人按隐私流程清理历史版本

## 浏览器 E2E

- 本环境未起完整登录+文件存储联调；鉴权下载与 upload-detail 有代码路径与单测。
- 阻塞原因：无运行中的 OA 后端/对象存储与测试账号。

## 迁移

- `sql/mysql/hrm_employee_roster_exp75.sql`（幂等 + 软删不复活 + file_id 列）
