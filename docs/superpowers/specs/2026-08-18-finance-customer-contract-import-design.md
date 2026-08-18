# 客户公司 / 合同签约 Excel 导入

> 决策记录。两套独立导入，交互对齐商务签单。  
> 不启 BPM。不更新已有档案或合同。

## 1. 背景与范围

客户公司目前只有单条新增/编辑；合同签约只有「提交即启审批」。需要批量补录历史数据。

范围：

- 客户公司列表：Excel 导入购方/客商档案
- 合同签约列表：Excel 导入**已通过**台账（历史补录）

不做：

- 一张表同时建档案和合同
- 导入后启 `finance_contract_sign`
- 按税号更新客户公司，或按单号更新合同
- 税号/单号已存在时跳过（静默不算错）
- 合同按税号匹配客商
- 导入附件、印章、邮寄、前置流程
- 抽公共 ImportService 框架

## 2. 架构

复用商务签单模式：Controller 模板 + 上传、`ImportExcelVO`、`ImportSupport` 校验、Service 按行写入、前端导入弹窗。

| | 客户公司 | 合同签约 |
| --- | --- | --- |
| 模板 | `GET /finance/customer-company/get-import-template` | `GET /finance/contract-application/get-import-template` |
| 上传 | `POST /finance/customer-company/import` | `POST /finance/contract-application/import` |
| 权限 | `finance:customer-company:import` | `finance:contract-application:import` |
| 授权 | 仅 `finance_admin` | 仅 `finance_admin` |
| 落库 | 走创建逻辑，`code` 系统生成 | **不走** `createAndStart`，不启 BPM |

合同导入直接写 `finance_contract_application`：

- `approval_status = APPROVED`
- `process_instance_id` 空
- `voided = 0`
- `current_node_key` / `current_node_name` 空
- `need_mail = 0`，`copy_count = 1`
- `draft_file_url` / `seal_file_url` 空
- 币种默认 `CNY`
- `counterparty_name`、`sign_company` 按匹配结果写快照

商务签单选合同规则不变：`APPROVED ∧ applicant_user_id = 当前用户 ∧ 未作废`。导入合同的申请人来自 Excel 账号，业务员才能挂自己的历史合同。

发布时另跑幂等菜单 SQL（新按钮权限 + `finance_admin` 授权）。两边 `sql/mysql/` 与 `ruoyi-office-vben/sql/mysql/` 镜像。

## 3. 客户公司字段

| 列 | 必填 | 规则 |
| --- | --- | --- |
| 名称 | 是 | 非空 |
| 纳税人识别号 | 是 | 非空；租户内已存在 → 该行失败；本文件内重复 → 后行失败 |
| 是否客户 | 否 | 是/否，默认是 |
| 是否供应商 | 否 | 是/否，默认否 |
| 开户银行 | 供应商为「是」时必填 | |
| 银行账号 | 供应商为「是」时必填 | |
| 邮寄地址 | 否 | |
| 联系电话 | 否 | |
| 联系人 | 否 | |
| 联系邮箱 | 否 | |

至少是客户或供应商之一。编码系统生成，不接受 Excel 指定。名称允许重复（库唯一键是税号）。

## 4. 合同签约字段

| 列 | 必填 | 规则 |
| --- | --- | --- |
| 合同业务单号 | 是 | 租户内已存在 → 失败；本文件内重复 → 后行失败 |
| 申请人账号 | 是 | 匹配系统用户名（`username`）；找不到 → 失败 |
| 签约主体 | 是 | 启用公司名称精确匹配；0 条或多条 → 失败 |
| 对方客商 | 是 | 启用客户公司名称精确匹配；0 条或多条 → 失败 |
| 文件类型 | 是 | 采购合同 / 销售合同 / 租赁合同 / 借款合同 / 推广充值业务合同 |
| 产品类型 | 是 | 字典 `finance_product_type` 的 label 或 value |
| 金额是否适用 | 是 | 是/否 |
| 合同金额 | 适用时必填 | 「是」必须 > 0；「否」必须空；「否」时 `amount_na = 1` |
| 返点比例 | 是 | 文本，与线上一致 |
| 结算方式 | 是 | CPA / CPS / CPC / 月结 / 其他 |
| 文件名称 | 是 | 写入 `file_name` |
| 起始日期 | 是 | |
| 结束日期 | 是 | 不得早于起始日期 |

对方客商**不读税号列**。名称对上一家启用档案后，写入 `counterparty_company_id` + 名称快照。

## 5. 数据流与错误处理

1. 下载模板（表头与 `@ExcelProperty` 一致）。
2. 上传 `.xls` / `.xlsx`。读失败或空表：整次请求失败，不写库。
3. 按行校验；失败记 `failureRows[rowNum] = reason`，该行不写。
4. 合法行独立写入。成功行进 `createdNos` / `createdCodes`。
5. 部分成功合法：成功行提交，失败行留在弹窗表。
6. 税号已存在、合同单号已存在：**失败，不跳过、不更新**。不用商务签单的 `sourceRowHash` skip。

布尔列只认：是 / 否（去空格）。其他值该行失败。

## 6. 前端

- `customer-company/index.vue`、`contract-application/index.vue` 工具栏加「导入」，`auth` 对应该权限。
- 弹窗克隆 `business-order/modules/import-modal.vue`：下模板、选文件、成功摘要、失败行表。
- API：`importXxx` + `importXxxTemplate`。

## 7. 测试

对齐 `FinanceBusinessOrderImportTest`，测 Support + Service，不启 BPM。

客户公司：

- 合法行写入且生成 `code`
- 税号已存在 → failureRows
- 本文件税号重复 → 后行失败
- 供应商缺银行 → 失败
- 客户供应商都否 → 失败

合同：

- 合法行 `APPROVED`、无 `processInstanceId`、申请人与匹配到的客商/主体正确
- 客商名 0 条或多条 → 失败
- 签约主体 0 条或多条 → 失败
- 申请人账号不存在 → 失败
- 金额适用但金额空/≤0 → 失败
- 金额不适用但填了金额 → 失败
- 单号已存在或文件内重复 → 失败
- 结束日早于起始日 → 失败
- 文件类型 / 结算方式非法 → 失败

## 8. 明确不做

| 项 | 说明 |
| --- | --- |
| 启审批 | 导入不是 `createAndStart` |
| 更新已有 | 税号/单号冲突只失败 |
| 跳过重复 | 与签单 hash skip 不同 |
| 合同按税号匹配 | 只按对方客商名称 |
| 附件与用印列 | 历史补录不要求 URL |
| 放开「仅本人可选」 | 商务签单选合同规则不变 |
