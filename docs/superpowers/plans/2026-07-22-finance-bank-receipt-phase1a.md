# 银行到款管理一期实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付银行到款 Excel 导入和仅面向财务/管理员的待认领池。

**Architecture:** 新增 `yudao-module-finance`，只持久化银行到款记录。Excel 导入将有效行写入到款表并逐行返回失败原因；待认领池只返回未认领余额大于零且状态为待认领或部分认领的记录。

**Requirement Update:** `docs/财务需求/OA表单-722.xlsx` 新增“商务签单信息”，定义了商务单主表、商务 ID 明细、开票/到款/退款/报表口径。该需求更新财务模块总体设计，但不改变本 Phase 1A 的交付边界；商务签单导入、商务单认领核销、开票联动、红冲、退款和报表进入后续 Phase 1B/1C。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis Plus、Redis、MySQL、EasyExcel、Vue 3、TypeScript、Vben。

## Global Constraints

- 仅实现银行到款导入和待认领池；不创建商务单、认领、核销、付款、凭证或用印相关代码。
- Excel 来源字段仅为：银行账户、交易日期、付款方名称、付款方账号、交易金额、摘要或附言、银行流水号。
- 不读取 `OA表单-722.xlsx` 的“商务签单信息”字段作为本阶段导入列；商务签单字段包括商务单 ID、导入日期、导入人、银行账户、合同审批流程 ID、下单日期、产品名称、对接人、执行开始日、执行截止日、付款方名称、签单执行金额、折扣率、签单结算金额、摘要或附言。
- 到款流水号由系统生成：`RC-{YYYYMMDD}-{sequence}`；银行流水号为唯一防重键。
- 只有财务可导入；财务和管理员可查询待认领池。
- 不生成另一份运行时 Excel 模板，不实现手工新增、编辑、关闭、导出、自动匹配或外部银行接口。

## Follow-Up Phase Boundaries

- Phase 1B：商务签单信息导入与商务单明细管理。签单结算金额按 `签单执行金额 * (1 - 折扣率)` 计算；折扣率空值按 `0` 处理；合同审批流程 ID 先允许为空或文本录入，后续再升级为流程引用。
- Phase 1C：认领单、认领明细和财务确认。认领明细可关联商务单 ID 或充值单 ID；选择商务单时，应收金额取商务签单明细的签单结算金额。
- Phase 1D：开票、红冲、退款和报表联动。商务签单明细表汇总所有明细，已开票金额按“开票 - 红冲”计算，已回款金额按“到款认领 - 退款金额”计算。

---

### Task 1: 搭建模块与到款表

**Plane:** OA-1

**Files:**
- Modify: `pom.xml`
- Modify: `yudao-server/pom.xml`
- Create: `yudao-module-finance/pom.xml`
- Create: `yudao-module-finance/yudao-module-finance-api/pom.xml`
- Create: `yudao-module-finance/yudao-module-finance-server/pom.xml`
- Create: `sql/mysql/finance_bank_receipt_phase1a.sql`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/enums/FinanceReceiptClaimStatusEnum.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/enums/ErrorCodeConstants.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/dal/dataobject/receipt/FinanceReceiptDO.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/dal/mysql/receipt/FinanceReceiptMapper.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/dal/redis/no/FinanceReceiptNoRedisDAO.java`

**Produces:** `finance_bank_receipt`，包含 `receipt_no`、导入信息、银行账户、交易信息、`bank_serial_no`、认领状态、已认领和未认领金额；`receipt_no` 和 `bank_serial_no` 均有唯一索引。

- [ ] **Step 1: 编写枚举和编号生成器的失败测试**

验证状态值 `0=待认领`、`1=部分认领`、`2=完全认领`、`3=已关闭`，并断言日期范围内编号遵循 `RC-YYYYMMDD-{sequence}`。

- [ ] **Step 2: 创建 Maven 模块并注册服务端依赖**

根 POM 注册 `yudao-module-finance`，`yudao-server/pom.xml` 依赖 `yudao-module-finance-server`；POM 结构遵循 ERP 模块。

- [ ] **Step 3: 创建迁移和持久化模型**

表字段使用 `decimal(18,2)` 存储金额，导入后写入 `claimed_amount=0.00`、`unclaimed_amount=transaction_amount`、`claim_status=0`。迁移同时写入财务菜单和 `finance:receipt:import`、`finance:receipt:query` 权限。

- [ ] **Step 4: 验证模块基础**

Run: `mvn -pl yudao-module-finance,yudao-server -am test`

Expected: exit 0。

### Task 2: 实现到款导入和待认领池后端

**Plane:** OA-3；依赖 OA-1。

**Files:**
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/receipt/FinanceReceiptController.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/receipt/vo/FinanceReceiptImportExcelVO.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/receipt/vo/FinanceReceiptImportRespVO.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/receipt/vo/FinanceReceiptPageReqVO.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/receipt/vo/FinanceReceiptRespVO.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/service/receipt/FinanceReceiptService.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/service/receipt/FinanceReceiptServiceImpl.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/test/java/cn/iocoder/yudao/module/finance/service/receipt/FinanceReceiptServiceImplTest.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/test/java/cn/iocoder/yudao/module/finance/controller/admin/receipt/FinanceReceiptControllerTest.java`
- Create: `yudao-module-finance/yudao-module-finance-server/src/test/resources/finance/receipt/到款认领表-valid.xlsx`
- Create: `yudao-module-finance/yudao-module-finance-server/src/test/resources/finance/receipt/到款认领表-invalid.xlsx`

**Produces:**

```http
POST /finance/receipt/import
GET /finance/receipt/unclaimed-page
```

- [ ] **Step 1: 编写导入与池查询的失败测试**

覆盖有效行导入、空文件、缺失必填字段、无效日期、非正金额、空银行流水号、文件内重复、库内重复、部分成功导入，以及待认领池排除完全认领/已关闭/未认领金额为零的记录。

- [ ] **Step 2: 绑定 Excel 行模型**

`FinanceReceiptImportExcelVO` 只映射七个来源字段。系统字段不得作为输入列读取。

- [ ] **Step 3: 实现逐行导入**

在生成到款编号前校验行数据；使用批次内集合和 `selectByBankSerialNo` 双重检测重复。响应以 Excel 数据行号映射失败信息，且有效行不因其他失败行回滚。

- [ ] **Step 4: 实现待认领池查询**

固定 `claimStatus IN (0, 1)` 且 `unclaimedAmount > 0`，支持到款流水号、银行账户、交易日期、付款方名称/账号、银行流水号和导入日期筛选。

- [ ] **Step 5: 验证后端**

Run: `mvn -pl yudao-module-finance/yudao-module-finance-server test`

Expected: exit 0。

### Task 3: 实现待认领池管理后台

**Plane:** OA-2；依赖 OA-3。

**Files:**
- Create: `ruoyi-office-vben/apps/web-antd/src/api/finance/receipt/index.ts`
- Create: `ruoyi-office-vben/apps/web-antd/src/views/finance/receipt/index.vue`
- Create: `ruoyi-office-vben/apps/web-antd/src/views/finance/receipt/data.ts`
- Create: `ruoyi-office-vben/apps/web-antd/src/views/finance/receipt/modules/import.vue`
- Create: `ruoyi-office-vben/apps/web-antd/src/views/finance/receipt/__tests__/receipt-api.test.ts`

- [ ] **Step 1: 编写 API 路径和导入失败映射的失败测试**

断言请求使用 `/finance/receipt/import` 和 `/finance/receipt/unclaimed-page`，并将 `failureRows` 展示为行号和原因。

- [ ] **Step 2: 创建 API 类型和请求函数**

定义 `BankReceipt`、`UnclaimedReceiptPageQuery`、`ReceiptImportResult`，字段与后端响应一一对应。

- [ ] **Step 3: 创建待认领池页面和导入弹窗**

展示全部到款字段和状态，提供筛选与 `.xlsx` 上传；导入成功后展示生成的到款流水号与行级错误。按钮受 `finance:receipt:import` 控制。

- [ ] **Step 4: 验证前端与视觉表现**

Run: `pnpm --filter @vben/web-antd typecheck`

Run: `pnpm --filter @vben/web-antd build`

Expected: 两项均 exit 0；对桌面和窄屏检查筛选区、表格横向溢出及导入反馈。

### Task 4: 端到端验收

**Plane:** OA-1、OA-3、OA-2。

- [ ] **Step 1: 验证数据库迁移可重复执行**

对临时 MySQL 执行 `finance_bank_receipt_phase1a.sql` 两次，均应成功。

- [ ] **Step 2: 验证导入契约**

导入含有效行、文件内重复、库内重复、无效金额和缺失字段的工作簿；有效行必须入池，失败行仅出现在反馈中。

- [ ] **Step 3: 验证权限和范围**

财务可导入和查询，管理员可查询，其他用户无权访问；不存在商务单、认领、出账、付款、自动匹配或凭证相关入口。

- [ ] **Step 4: 执行完整验证**

Run: `mvn -pl yudao-module-finance,yudao-server -am test`

Run: `mvn -pl yudao-server -am package -DskipTests`

Run: `pnpm --filter @vben/web-antd typecheck`

Run: `pnpm --filter @vben/web-antd build`

Expected: 全部 exit 0。
