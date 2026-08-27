# 手续费付款台账

> 决策记录。财务分组下独立台账，列表增删改查 + Excel 导入。  
> 不启 BPM，不接入付款申请/出纳打款。

## 1. 背景与范围

财务需要登记银行手续费支出：日期、金额、币种、主体公司、付款账户。这是台账，不是审批单。

范围：

- 财务分组（`fin-biz`）菜单「手续费付款」，与「银行到款」同级
- 列表增删改查
- Excel 导入（模板 + 上传）
- 账户必须选自该公司已启用的公司银行账户；公司名与账户信息提交时快照

不做：

- 审批流程、附件、备注
- 导出
- 与付款申请、出纳打款、银行余额报表联动
- 状态机（关闭/作废）
- 抽公共 ImportService 框架

## 2. 数据模型

表 `finance_handling_fee_payment`（TenantBaseDO，逻辑删除沿用框架 `deleted`）。

| 列 | 说明 |
| --- | --- |
| `id` | 主键 |
| `fee_date` | 付款日期，必填 |
| `amount` | 金额，必填，> 0，精度 2 |
| `currency` | CNY/USD/HKD，必填 |
| `entity_company_dept_id` | 启用主体公司组织部门 ID |
| `entity_company_name` | 主体公司名称快照 |
| `company_bank_account_id` | 公司银行账户 ID |
| `account_name` | 户名快照 |
| `bank_name` | 开户行快照 |
| `account_no` | 账号快照（明文，台账对内） |
| `account_no_masked` | 列表展示用掩码 |

约束：

- 主体公司必须是启用公司（复用 `FinanceEntityCompanyResolver`）
- 账户必须存在、启用，且 `entity_company_dept_id` 与所选公司一致（复用 `FinanceCompanyBankAccountService.requireEnabledForEntityCompany`）
- 币种必须在 CNY/USD/HKD（复用 `FinanceCurrencySupport`）
- 选账户后表单默认币种为账户币种，允许改选

删除：走 MyBatis 逻辑删除，无额外生命周期。

## 3. API 与权限

路径前缀 `/finance/handling-fee-payment`。

| 接口 | 权限 |
| --- | --- |
| `GET /page` | `finance:handling-fee-payment:query` |
| `GET /get` | `finance:handling-fee-payment:query` |
| `POST /create` | `finance:handling-fee-payment:create` |
| `PUT /update` | `finance:handling-fee-payment:update` |
| `DELETE /delete` | `finance:handling-fee-payment:delete` |
| `GET /get-import-template` | `finance:handling-fee-payment:import` |
| `POST /import` | `finance:handling-fee-payment:import` |

分页筛选：`feeDate` 区间、`entityCompanyDeptId`。排序：`id` 倒序。

## 4. 菜单

挂在财务根下的「财务」分组（`path=fin-biz`），与银行到款同级。银行到款保持 sort=1；本菜单 sort=2；开票及之后现有 `fin-biz` 页面菜单 sort 各 +1，避免并列冲突。

页面：`finance/handling-fee-payment/index`，componentName `FinanceHandlingFeePayment`。

按钮：查询（菜单可见）、新增、编辑、删除、导入。仅 `finance_admin` 授权（与银行到款维护侧一致）。

`sql/mysql/` 与 `ruoyi-office-vben/sql/mysql/` 镜像幂等菜单 SQL。

## 5. 前端

对齐银行到款列表骨架（VxeGrid + 弹窗表单 + 导入弹窗），无关闭/重开/审计。

列表列：付款日期、金额、币种、主体公司、户名、开户行、账号（掩码）、创建时间。

表单：

1. 主体公司（启用公司下拉）
2. 银行账户（按公司 `simple-list` 已启用账户；换公司清空账户）
3. 只读展示户名/开户行/账号
4. 付款日期、金额、币种

校验：四项业务字段 + 账户均必填。

## 6. 导入

交互对齐银行到款：下载模板、上传、按行成功/失败回传。

| 列 | 必填 | 规则 |
| --- | --- | --- |
| 付款日期 | 是 | Excel 日期或 `yyyy-MM-dd` |
| 金额 | 是 | > 0 |
| 币种 | 否 | CNY/USD/HKD；空则用匹配账户的币种；账户无币种则 CNY |
| 主体公司名称 | 是 | 启用公司名称精确匹配；0 条或多条 → 该行失败 |
| 银行账号 | 是 | 在该公司已启用账户中按账号精确匹配；找不到 → 失败 |

失败行不入库。本文件内不要求唯一（同一天同一账户可多笔手续费）。

## 7. 错误处理

| 场景 | 行为 |
| --- | --- |
| 公司未启用/不存在 | 业务错误，不落库 |
| 账户停用或不属于该公司 | 业务错误 |
| 币种非法 | 业务错误 |
| 金额 ≤ 0 | 校验失败 |
| 导入部分失败 | 成功行提交，失败行返回行号+原因 |

## 8. 测试

- Service 单测：创建时账户/公司校验；更新换公司必须换合法账户；导入匹配失败不插入
- 菜单 SQL 幂等可重复执行

## 9. 发布

test 共享库执行：建表 SQL + 菜单/权限 SQL。不改环境变量。
