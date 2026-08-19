# 费用报销：发票 OCR 与无票流程

在 STORY-0011 已上线的全员费用报销之上增量。对应后续 Story（有票补发票/前置单 + 无票新流程 + 10.20.32.1 PaddleOCR）。

## 目标

1. 有票报销（`oa_expense_reimbursement`）非代票每行必须上传 **一张** 发票；上传后用 OCR 预填该行 **费用日期、金额**，人可改。
2. 差旅行必须挂本人已通过的 **出差** 单；交通行必须挂本人已通过的 **出差或外出** 单。
3. 新增 **无票费用报销**（`oa_expense_no_invoice`）：审批链与有票相同；发起人在流程模型页指定（可再与角色权限并集）；无发票、不调 OCR。
4. OCR 服务部署在 **10.20.32.1**（非 OA 同机）。OA 只在配置文件里写地址。

## 流程与权限

| | 有票 `oa_expense_reimbursement` | 无票 `oa_expense_no_invoice` |
|---|---|---|
| 发起 | 继续全员（登录即可 create） | 模型「指定用户」；可选再加 `finance:expense-no-invoice:create`，与名单 **并集** |
| 审批 | 多级部门负责人 → 财务实报 → 出纳 | 同左 |
| 壳内嵌 | 已有 form-body，补发票/OCR/前置 | 新 form-body，无上传 |
| 列表/详情 | 同一套费用报销列表，用 `invoiceMode` 区分 | 同左 |

模型页现成能力只有：全员 / 指定用户 / 指定部门。无「指定角色」。角色若要一键开放，用权限点，不改模型页。

## 数据

仍用 `finance_expense_reimbursement` / `_line`。

头表增加：

- `invoice_mode`：`WITH_INVOICE` | `NO_INVOICE`。有票入口 create 强制 WITH_INVOICE；无票入口 create 强制 NO_INVOICE。请求体带错 mode 直接拒。
- `process_key`：冗余，便于列表

明细增加：

- `invoice_file_url`：有票非代票必填；无票/代票必须空
- `predoc_type`：空 / `TRIP` / `OUTING`
- `predoc_process_instance_id`：前置 BPM 实例 id

`apply_amount` 仍由明细金额合计。OCR 只预填，以用户提交的金额/日期为准。

## 校验

- **有票 + 非代票**：每行恰好一张可识别文件 URL；缺票拒收。
- **有票 + 代票**：不强制发票，不调 OCR。
- **无票**：任何发票字段非空则拒收。
- **差旅**（`travel`）：必须 `predoc_type=TRIP`，且为 **本人发起、流程已通过** 的 `oa_business_trip`（状态口径与现有出差完结一致）。
- **交通**（`transport`）：必须 `TRIP` 或 `OUTING`，本人发起且已通过的出差或外出。
- 其它分类：禁止带前置。
- 无票流程同样执行差旅/交通前置规则。

## OCR

- 实现：PaddleOCR（CPU）独立 HTTP 服务，跑在 `10.20.32.1`，默认端口 `8099`。
- OA **配置文件**（`application-dev.yaml` / `application-test.yaml`，以及 test 机同名配置）增加：

```yaml
yudao:
  finance:
    invoice-ocr:
      base-url: http://10.20.32.1:8099
      timeout-ms: 8000
```

不要求再配环境变量。地址空或调用失败/超时：**不挡提交**，日期金额手填。
- 协议：`POST {base-url}/ocr/invoice`，body 为发票文件或 OA 可拉取的文件 URL；响应 `{ feeDate, amount, rawText? }`。只认日期和金额，**不验真、不查重**。
- OA 后端提供 `POST /finance/expense-reimbursement/ocr-invoice`（登录即可），前端上传后调它预填行，不把 10.20.32.1 暴露给浏览器。

## 前端

- 有票 form-body：非代票行增加发票上传；上传成功调 ocr-invoice；差旅/交通行出前置单选择器（复用出差/外出已通过列表）。
- 无票 form-body：无上传；`invoiceMode=NO_INVOICE`；发起走 `oa_expense_no_invoice`。
- 统一发起注册 `oa_expense_no_invoice`。目录可见性跟模型 startUserIds（及可选权限点）。

## 非目标

- 发票验真、发票代码查重、费用标准高亮。
- 手机端、多币种。
- 改有票审批节点。
- 在 OA 进程内嵌 Paddle。
- 模型页新增「按角色发起」UI（本期不改设计器）。

## 发布

- SQL：头/行新列；无票 BPMN + model SQL（`start_user_ids` 先空，上线后在设计器点名）。
- 在 `10.20.32.1` 部署 PaddleOCR 容器并监听 8099。
- test 配置写入 `yudao.finance.invoice-ocr.base-url`。
